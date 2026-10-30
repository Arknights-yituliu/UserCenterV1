package com.orange.service.impl;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.entity.dto.akoperator.OperatorStateManualSaveRequest;
import com.orange.entity.dto.akoperator.OperatorItemRequest;
import com.orange.entity.po.AkOperatorStateManual;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.entity.vo.akoperator.OperatorVO;
import com.orange.mapper.AkOperatorStateManualMapper;
import com.orange.service.AkOperatorStateManualService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户手动修改版干员数据服务实现
 *
 * <p>归属：uid 取自可信登录上下文，不接受客户端指定；一个用户只能有一份手动修改版，
 * 由唯一键 (uid, operator_id) 保证，不存在跨用户共享。</p>
 *
 * <p>保存顺序：参数校验与归一化 → 写事务内比对后增量写入，遇唯一键冲突或死锁时
 * 整事务回滚后重试，仍失败返回业务码 40004。</p>
 *
 * @author UserCenter
 */
@Service
public class AkOperatorStateManualServiceImpl implements AkOperatorStateManualService {

    /** 唯一键冲突/死锁时整事务回滚后的最大重试次数（不含首次执行） */
    private static final int SAVE_MAX_RETRY = 2;

    private final AkOperatorStateManualMapper manualMapper;
    private final AkOperatorStateManualSaveExecutor saveExecutor;

    /**
     * 构造器注入依赖
     *
     * @param manualMapper 手动修改版干员数据 Mapper
     * @param saveExecutor 写事务执行器（独立 Bean 保证 @Transactional 生效）
     */
    public AkOperatorStateManualServiceImpl(AkOperatorStateManualMapper manualMapper,
                                       AkOperatorStateManualSaveExecutor saveExecutor) {
        this.manualMapper = manualMapper;
        this.saveExecutor = saveExecutor;
    }

    /**
     * 读取当前用户的手动修改版干员数据
     *
     * @param uid 用户中心 UID
     * @return 手动版干员列表，无数据时返回空列表
     */
    @Override
    public List<OperatorVO> listManualOperators(Long uid) {
        List<AkOperatorStateManual> rows = manualMapper.selectByUid(uid);
        List<OperatorVO> items = new ArrayList<>(rows.size());
        for (AkOperatorStateManual row : rows) {
            items.add(toManualOperatorVO(row));
        }
        return items;
    }

    /**
     * 保存当前用户的手动修改版干员数据
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（干员数组）
     * @return 新增/更新/未变更条数统计，三项之和等于传入记录数
     */
    @Override
    public OperatorSaveResultVO saveManualOperators(Long uid, OperatorStateManualSaveRequest request) {
        // 先归一化（缺省/null/空字符串补 0）再校验重复 ID，保证比较语义与落库值一致
        List<AkOperatorStateManual> records = normalizeOperators(uid, request.getOperators());
        return saveWithRetry(uid, records);
    }

    /**
     * 调用写事务执行器保存，遇唯一键冲突或死锁时整事务回滚后重试整个请求
     *
     * @param uid 用户中心 UID
     * @param records 本次提交的手动版干员记录（已归一化）
     * @return 新增/更新/未变更条数统计
     */
    private OperatorSaveResultVO saveWithRetry(Long uid, List<AkOperatorStateManual> records) {
        for (int attempt = 0; ; attempt++) {
            try {
                return saveExecutor.save(uid, records);
            } catch (DuplicateKeyException | PessimisticLockingFailureException e) {
                if (attempt >= SAVE_MAX_RETRY) {
                    LogUtil.warn(AkOperatorStateManualServiceImpl.class,
                            "手动版干员数据保存冲突重试耗尽, uid={}, attempts={}", uid, attempt + 1, e);
                    throw new BusinessException(ResultCode.OPERATOR_SAVE_CONFLICT);
                }
                LogUtil.info(AkOperatorStateManualServiceImpl.class,
                        "手动版干员数据保存冲突，回滚后重试整个请求, uid={}, attempt={}", uid, attempt + 1);
            }
        }
    }

    /**
     * 归一化干员数组：数值字段缺省/null 统一补 0，并拒绝同一请求中的重复干员 ID
     *
     * @param uid   用户中心 UID
     * @param items 客户端提交的干员数组（已通过 Bean Validation 的格式与范围校验）
     * @return 可直接落库的手动版干员记录列表
     */
    private List<AkOperatorStateManual> normalizeOperators(Long uid, List<OperatorItemRequest> items) {
        Set<String> seenIds = new HashSet<>(Math.max(16, items.size() * 2));
        List<AkOperatorStateManual> result = new ArrayList<>(items.size());
        for (OperatorItemRequest item : items) {
            if (!seenIds.add(item.getId())) {
                throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "同一请求中干员ID重复：" + item.getId());
            }
            result.add(toManualRecord(uid, item));
        }
        return result;
    }

    /**
     * 将单条手动版请求转换为落库实体，数值字段 null 归一化为 0
     *
     * @param uid  用户中心 UID
     * @param item 客户端提交的干员记录
     * @return 手动版落库实体（id 与 updatedAt 由服务端维护）
     */
    private AkOperatorStateManual toManualRecord(Long uid, OperatorItemRequest item) {
        AkOperatorStateManual record = new AkOperatorStateManual();
        record.setUid(uid);
        record.setOperatorId(item.getId());
        record.setOwned(zeroIfNull(item.getOwned()));
        record.setRarity(zeroIfNull(item.getRarity()));
        record.setLevel(zeroIfNull(item.getLevel()));
        record.setEvolvePhase(zeroIfNull(item.getEvolvePhase()));
        record.setMainSkillLevel(zeroIfNull(item.getMainSkillLevel()));
        record.setSkill1(zeroIfNull(item.getSkill1()));
        record.setSkill2(zeroIfNull(item.getSkill2()));
        record.setSkill3(zeroIfNull(item.getSkill3()));
        record.setEquipX(zeroIfNull(item.getEquipX()));
        record.setEquipY(zeroIfNull(item.getEquipY()));
        record.setEquipD(zeroIfNull(item.getEquipD()));
        record.setEquipA(zeroIfNull(item.getEquipA()));
        record.setEquipB(zeroIfNull(item.getEquipB()));
        record.setPotentialRank(zeroIfNull(item.getPotentialRank()));
        return record;
    }

    /**
     * 手动版实体转视图对象：数据库行 ID 命名为 recordId，数值字段始终返回数字
     *
     * @param row 手动版干员实体
     * @return 手动版干员视图对象
     */
    private OperatorVO toManualOperatorVO(AkOperatorStateManual row) {
        OperatorVO vo = new OperatorVO();
        vo.setId(row.getOperatorId());
        vo.setRecordId(row.getId());
        vo.setOwned(zeroIfNull(row.getOwned()));
        vo.setRarity(zeroIfNull(row.getRarity()));
        vo.setLevel(zeroIfNull(row.getLevel()));
        vo.setEvolvePhase(zeroIfNull(row.getEvolvePhase()));
        vo.setMainSkillLevel(zeroIfNull(row.getMainSkillLevel()));
        vo.setSkill1(zeroIfNull(row.getSkill1()));
        vo.setSkill2(zeroIfNull(row.getSkill2()));
        vo.setSkill3(zeroIfNull(row.getSkill3()));
        vo.setEquipX(zeroIfNull(row.getEquipX()));
        vo.setEquipY(zeroIfNull(row.getEquipY()));
        vo.setEquipD(zeroIfNull(row.getEquipD()));
        vo.setEquipA(zeroIfNull(row.getEquipA()));
        vo.setEquipB(zeroIfNull(row.getEquipB()));
        vo.setPotentialRank(zeroIfNull(row.getPotentialRank()));
        return vo;
    }

    /**
     * 数值归一化：null 视为 0（与显式传 0 等价，不表示沿用旧值）
     *
     * @param value 原始值
     * @return 非空整数值
     */
    private Integer zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
