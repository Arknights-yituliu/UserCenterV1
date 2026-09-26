package com.orange.service.impl;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.RateLimitedException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.akoperator.OperatorItemRequest;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.po.AkAccountBinding;
import com.orange.entity.po.AkPlayerInfo;
import com.orange.entity.po.AkOperatorState;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.entity.vo.akoperator.OperatorVO;
import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkPlayerInfoMapper;
import com.orange.mapper.AkOperatorStateMapper;
import com.orange.service.AkAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 游戏账号与干员数据服务实现
 *
 * <p>鉴权与归属：uid 取自可信登录上下文；读取前必须核对本人的绑定关系，
 * 未绑定统一返回业务码 80008，不通过错误消息暴露账号是否存在。</p>
 *
 * <p>保存顺序：登录鉴权（拦截器）→ 目标账号与角色信息校验 → 归一化干员数据 →
 * 占用该 ak_uid 的上传限流名额 → 开启写事务，避免非法请求占用别人的名额。
 * 首次上传该 ak_uid 时不做额外凭据校验，绑定关系在写事务中直接建立。</p>
 *
 * @author UserCenter
 */
@Service
public class AkAccountServiceImpl implements AkAccountService {

    private static final Logger log = LoggerFactory.getLogger(AkAccountServiceImpl.class);

    /** 游戏账号 UID 合法格式：不超过 32 位可见 ASCII 字符（与列定义 VARCHAR(32) utf8mb4_bin 一致，保持大小写敏感） */
    private static final Pattern AK_UID_PATTERN = Pattern.compile("^[\\x21-\\x7E]{1,32}$");

    /** 上传限流业务标识，构成 Redis key 的中间段 */
    private static final String RATE_LIMIT_BIZ = "ak-operator-save";

    /** 同一 ak_uid 上传限流窗口（秒）：窗口内最多接受 1 个保存请求，全部绑定该账号的 uid 共享名额 */
    private static final long SAVE_RATE_LIMIT_WINDOW_SECONDS = 2L;

    /** 唯一键冲突/死锁时整事务回滚后的最大重试次数（不含首次执行） */
    private static final int SAVE_MAX_RETRY = 2;

    private final AkAccountBindingMapper bindingMapper;
    private final AkPlayerInfoMapper playerInfoMapper;
    private final AkOperatorStateMapper operatorMapper;
    private final AkOperatorSaveExecutor saveExecutor;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造器注入依赖
     *
     * @param bindingMapper       绑定关系 Mapper
     * @param playerInfoMapper    角色信息 Mapper
     * @param operatorMapper      干员数据 Mapper
     * @param saveExecutor        写事务执行器（独立 Bean 保证 @Transactional 生效）
     * @param stringRedisTemplate Redis 客户端，用于跨实例的原子限流
     */
    public AkAccountServiceImpl(AkAccountBindingMapper bindingMapper,
                                AkPlayerInfoMapper playerInfoMapper,
                                AkOperatorStateMapper operatorMapper,
                                AkOperatorSaveExecutor saveExecutor,
                                StringRedisTemplate stringRedisTemplate) {
        this.bindingMapper = bindingMapper;
        this.playerInfoMapper = playerInfoMapper;
        this.operatorMapper = operatorMapper;
        this.saveExecutor = saveExecutor;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 查询当前用户已绑定的游戏账号列表，按最近导入时间倒序
     *
     * <p>倒序由 Mapper 的 SQL 保证，前端取首条即可默认展示最新导入的账号数据。</p>
     *
     * @param uid 用户中心 UID
     * @return 已绑定游戏账号列表（含创建时间与最近导入时间），最近的在前，无绑定时返回空列表
     */
    @Override
    public List<AkAccountVO> listBoundAccounts(Long uid) {
        List<AkAccountBinding> bindings = bindingMapper.selectByOwnerOrderByUpdateTime(uid);
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> akUids = new ArrayList<>(bindings.size());
        for (AkAccountBinding binding : bindings) {
            akUids.add(binding.getAkUid());
        }
        Map<String, AkPlayerInfo> infoMap = loadPlayerInfoMap(akUids);
        List<AkAccountVO> accounts = new ArrayList<>(bindings.size());
        for (AkAccountBinding binding : bindings) {
            // 角色信息缺失时不展示，但保留绑定关系本身
            if (!infoMap.containsKey(binding.getAkUid())) {
                continue;
            }
            accounts.add(toAccountVO(binding));
        }
        return accounts;
    }

    /**
     * 全量读取某游戏账号的干员数据，读取前先核对本人绑定关系
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     * @return 该账号全部干员数据，无数据时 items 为空数组
     */
    @Override
    public OperatorListVO listOperators(Long uid, String akUid) {
        validateAkUid(akUid);
        requireBinding(akUid, uid);
        List<AkOperatorState> rows = operatorMapper.selectByAkUid(akUid);
        List<OperatorVO> items = new ArrayList<>(rows.size());
        for (AkOperatorState row : rows) {
            items.add(toOperatorVO(row));
        }
        return new OperatorListVO(akUid, items);
    }

    /**
     * 批量保存某游戏账号的角色信息与干员数据
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（角色信息 + 干员数组），目标游戏账号取自 request.playerInfo.akUid
     * @return 新增/更新/未变更条数统计，三项之和等于传入记录数
     */
    @Override
    public OperatorSaveResultVO saveOperators(Long uid, OperatorSaveRequest request) {
        String akUid = request.getPlayerInfo().getAkUid();
        validateAkUid(akUid);
        // 先归一化（缺省/null/空字符串补 0）再校验重复 ID，保证比较语义与落库值一致
        List<AkOperatorState> operators = normalizeOperators(akUid, request.getOperators());

        // 参数校验通过后占用该 ak_uid 的限流名额；首次上传不做凭据校验，绑定关系由写事务建立
        acquireSaveQuota(akUid);

        return saveWithRetry(akUid, uid, operators);
    }

    /**
     * 调用写事务执行器保存，遇唯一键冲突或死锁时整事务回滚后重试整个请求
     *
     * <p>限流名额只在首次执行前占用一次，内部重试不重复占用。</p>
     *
     * @param akUid     游戏账号 UID
     * @param uid       用户中心 UID
     * @param operators 本次提交的干员记录（已归一化）
     * @return 新增/更新/未变更条数统计
     */
    private OperatorSaveResultVO saveWithRetry(String akUid,
                                               Long uid,
                                               List<AkOperatorState> operators) {
        for (int attempt = 0; ; attempt++) {
            try {
                return saveExecutor.save(akUid, uid, operators);
            } catch (DuplicateKeyException | PessimisticLockingFailureException e) {
                if (attempt >= SAVE_MAX_RETRY) {
                    log.warn("干员数据保存冲突重试耗尽, akUidHash={}, attempts={}",
                            SignUtil.sha256(akUid), attempt + 1, e);
                    throw new BusinessException(ResultCode.OPERATOR_SAVE_CONFLICT);
                }
                log.info("干员数据保存冲突，回滚后重试整个请求, akUidHash={}, attempt={}",
                        SignUtil.sha256(akUid), attempt + 1);
            }
        }
    }

    /**
     * 占用同一 ak_uid 的上传限流名额：Redis SET NX EX 原子操作，多实例共享
     *
     * <p>名额已被占用返回业务码 30006 并携带等待秒数，不读写角色/干员表也不排队；
     * 限流设施不可用返回业务码 40003，不默默绕过限流。</p>
     *
     * @param akUid 游戏账号 UID
     */
    private void acquireSaveQuota(String akUid) {
        String key = RedisKeyUtil.rate(RATE_LIMIT_BIZ, SignUtil.sha256(akUid));
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(SAVE_RATE_LIMIT_WINDOW_SECONDS)));
        } catch (Exception e) {
            log.error("干员上传限流设施不可用, key={}", key, e);
            throw new BusinessException(ResultCode.RATE_LIMITER_UNAVAILABLE);
        }
        if (!acquired) {
            throw new RateLimitedException(ResultCode.AK_UPLOAD_RATE_LIMITED, SAVE_RATE_LIMIT_WINDOW_SECONDS);
        }
    }

    /**
     * 核对本人绑定关系，未绑定返回业务码 80008
     *
     * <p>查询他人账号与查询不存在的绑定关系返回同一权限码，不暴露账号是否存在。</p>
     *
     * @param akUid    游戏账号 UID
     * @param uid      用户中心 UID
     */
    private void requireBinding(String akUid, Long uid) {
        if (bindingMapper.countBinding(akUid, uid) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 归一化干员数组：数值字段缺省/null 统一补 0，并拒绝同一请求中的重复干员 ID
     *
     * @param akUid 游戏账号 UID
     * @param items 客户端提交的干员数组（已通过 Bean Validation 的格式与范围校验）
     * @return 可直接落库的干员记录列表
     */
    private List<AkOperatorState> normalizeOperators(String akUid, List<OperatorItemRequest> items) {
        Set<String> seenIds = new HashSet<>(Math.max(16, items.size() * 2));
        List<AkOperatorState> result = new ArrayList<>(items.size());
        for (OperatorItemRequest item : items) {
            if (!seenIds.add(item.getId())) {
                throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "同一请求中干员ID重复：" + item.getId());
            }
            result.add(toStateRecord(akUid, item));
        }
        return result;
    }

    /**
     * 将单条干员请求转换为落库实体，数值字段 null 归一化为 0
     *
     * @param akUid 游戏账号 UID
     * @param item  客户端提交的干员记录
     * @return 干员落库实体（id 与 updatedAt 由服务端维护）
     */
    private AkOperatorState toStateRecord(String akUid, OperatorItemRequest item) {
        AkOperatorState record = new AkOperatorState();
        record.setAkUid(akUid);
        record.setOperatorId(item.getId());
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
     * 批量读取角色信息并按 ak_uid 建立映射
     *
     * @param akUids 游戏账号 UID 列表，调用方保证非空
     * @return ak_uid 到角色信息的映射
     */
    private Map<String, AkPlayerInfo> loadPlayerInfoMap(List<String> akUids) {
        List<AkPlayerInfo> infos = playerInfoMapper.selectByAkUids(akUids);
        Map<String, AkPlayerInfo> infoMap = new HashMap<>(Math.max(16, infos.size() * 2));
        for (AkPlayerInfo info : infos) {
            infoMap.put(info.getAkUid(), info);
        }
        return infoMap;
    }

    /**
     * 绑定关系实体转已绑定账号视图对象
     *
     * <p>账号 UID 取自绑定表，创建时间与最近导入时间一并返回，供前端排序与展示。</p>
     *
     * @param binding 绑定关系实体
     * @return 账号视图对象
     */
    private AkAccountVO toAccountVO(AkAccountBinding binding) {
        AkAccountVO vo = new AkAccountVO();
        vo.setAkUid(binding.getAkUid());
        vo.setCreateTime(binding.getCreateTime());
        vo.setUpdateTime(binding.getUpdateTime());
        return vo;
    }

    /**
     * 干员实体转视图对象：数据库行 ID 命名为 recordId，数值字段始终返回数字
     *
     * @param row 干员实体
     * @return 干员视图对象
     */
    private OperatorVO toOperatorVO(AkOperatorState row) {
        OperatorVO vo = new OperatorVO();
        vo.setId(row.getOperatorId());
        vo.setRecordId(row.getId());
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
     * 校验游戏账号 UID 格式
     *
     * @param akUid 游戏账号 UID
     */
    private void validateAkUid(String akUid) {
        if (!StringUtils.hasText(akUid) || !AK_UID_PATTERN.matcher(akUid).matches()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "游戏账号UID必须为不超过32位的可见ASCII字符");
        }
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
