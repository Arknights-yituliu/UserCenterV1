package com.orange.service.impl;

import com.orange.entity.po.AkPlayerInfo;
import com.orange.entity.po.OperatorProgressionData;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkPlayerInfoMapper;
import com.orange.mapper.OperatorProgressionDataMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 干员数据保存写事务执行器
 *
 * <p>独立成 Bean 是为了让 {@code @Transactional} 生效（Spring 自调用不走代理），
 * 并由调用方在唯一键冲突/死锁时整事务回滚后重试。</p>
 *
 * <p>单个事务内依次完成：核对/创建本人绑定 → 角色信息比对更新或插入 → 干员比对后增量写入。</p>
 *
 * @author UserCenter
 */
@Component
public class AkOperatorSaveExecutor {

    /** 提交条数达到该阈值时改为按 ak_uid 全量读取，避免超长 IN 列表 */
    private static final int FULL_SCAN_THRESHOLD = 100;

    /** 批量插入每条 SQL 的行数 */
    private static final int INSERT_BATCH_SIZE = 100;

    private final AkAccountBindingMapper bindingMapper;
    private final AkPlayerInfoMapper playerInfoMapper;
    private final OperatorProgressionDataMapper operatorMapper;

    /**
     * 构造器注入 Mapper
     *
     * @param bindingMapper    绑定关系 Mapper
     * @param playerInfoMapper 角色信息 Mapper
     * @param operatorMapper   干员数据 Mapper
     */
    public AkOperatorSaveExecutor(AkAccountBindingMapper bindingMapper,
                                  AkPlayerInfoMapper playerInfoMapper,
                                  OperatorProgressionDataMapper operatorMapper) {
        this.bindingMapper = bindingMapper;
        this.playerInfoMapper = playerInfoMapper;
        this.operatorMapper = operatorMapper;
    }

    /**
     * 在一个写事务内保存绑定关系、角色信息与干员数据
     *
     * @param akUid              游戏账号 UID
     * @param uid                用户中心 UID
     * @param submittedOperators 本次提交的干员记录（已归一化，数值字段非空）
     * @return 新增/更新/未变更条数统计
     */
    @Transactional(rollbackFor = Exception.class)
    public OperatorSaveResultVO save(String akUid,
                                     Long uid,
                                     List<OperatorProgressionData> submittedOperators) {
        ensureBinding(akUid, uid);
        savePlayerInfo(akUid);
        return saveOperators(akUid, submittedOperators);
    }

    /**
     * 在事务中保证本人绑定存在，并把“最近导入时间”推进到本次导入
     *
     * <p>首次导入建立绑定（create_time 与 update_time 由数据库默认值写入），此后每次导入
     * 只刷新 update_time，使账号列表能按最近导入时间倒序。并发重复插入由组合主键兜底。</p>
     *
     * @param akUid    游戏账号 UID
     * @param uid      用户中心 UID
     */
    private void ensureBinding(String akUid, Long uid) {
        bindingMapper.upsertBinding(akUid, uid);
    }

    /**
     * 保存角色信息：该 ak_uid 无记录时插入一条仅含 ak_uid 与创建时间的行，已有记录则不再变动
     *
     * @param akUid 游戏账号 UID
     */
    private void savePlayerInfo(String akUid) {
        if (playerInfoMapper.selectByAkUid(akUid) != null) {
            return;
        }
        AkPlayerInfo info = new AkPlayerInfo();
        info.setAkUid(akUid);
        info.setCreateTime(System.currentTimeMillis());
        playerInfoMapper.insert(info);
    }

    /**
     * 比对并写入干员数据：新 ID 批量插入，属性变化的旧 ID 按数据库行 ID 更新，完全相同的行不写库
     *
     * @param akUid     游戏账号 UID
     * @param submitted 本次提交的干员记录（已归一化）
     * @return 新增/更新/未变更条数统计
     */
    private OperatorSaveResultVO saveOperators(String akUid, List<OperatorProgressionData> submitted) {
        Map<String, OperatorProgressionData> existingMap = loadExisting(akUid, submitted);
        LocalDateTime now = LocalDateTime.now();
        List<OperatorProgressionData> toInsert = new ArrayList<>();
        List<OperatorProgressionData> toUpdate = new ArrayList<>();
        int unchangedCount = 0;

        for (OperatorProgressionData item : submitted) {
            OperatorProgressionData existing = existingMap.get(item.getOperatorId());
            if (existing == null) {
                item.setUpdatedAt(now);
                toInsert.add(item);
                continue;
            }
            if (isSameProgression(existing, item)) {
                unchangedCount++;
                continue;
            }
            item.setId(existing.getId());
            item.setUpdatedAt(now);
            toUpdate.add(item);
        }

        for (int from = 0; from < toInsert.size(); from += INSERT_BATCH_SIZE) {
            int to = Math.min(from + INSERT_BATCH_SIZE, toInsert.size());
            operatorMapper.batchInsert(toInsert.subList(from, to));
        }
        for (OperatorProgressionData record : toUpdate) {
            operatorMapper.updateProgression(record);
        }
        return new OperatorSaveResultVO(toInsert.size(), toUpdate.size(), unchangedCount);
    }

    /**
     * 读取本次提交涉及的现有记录并按干员编码建立内存映射
     *
     * <p>完整上传时按 ak_uid 一次取全部；少量上传时按 operator_id IN (...) 点查。</p>
     *
     * @param akUid     游戏账号 UID
     * @param submitted 本次提交的干员记录
     * @return 干员编码到现有记录的映射
     */
    private Map<String, OperatorProgressionData> loadExisting(String akUid,
                                                              List<OperatorProgressionData> submitted) {
        List<OperatorProgressionData> rows;
        if (submitted.size() >= FULL_SCAN_THRESHOLD) {
            rows = operatorMapper.selectByAkUid(akUid);
        } else {
            List<String> operatorIds = new ArrayList<>(submitted.size());
            for (OperatorProgressionData item : submitted) {
                operatorIds.add(item.getOperatorId());
            }
            rows = operatorMapper.selectByAkUidAndIds(akUid, operatorIds);
        }
        Map<String, OperatorProgressionData> existingMap = new HashMap<>(Math.max(16, rows.size() * 2));
        for (OperatorProgressionData row : rows) {
            existingMap.put(row.getOperatorId(), row);
        }
        return existingMap;
    }

    /**
     * 逐字段比较干员属性是否完全相同（先归一化再比较，null 与 0 等价）
     *
     * @param existing  数据库当前值
     * @param submitted 本次提交值
     * @return 是否无需更新
     */
    private boolean isSameProgression(OperatorProgressionData existing, OperatorProgressionData submitted) {
        return sameValue(existing.getRarity(), submitted.getRarity())
                && sameValue(existing.getLevel(), submitted.getLevel())
                && sameValue(existing.getEvolvePhase(), submitted.getEvolvePhase())
                && sameValue(existing.getMainSkillLevel(), submitted.getMainSkillLevel())
                && sameValue(existing.getSkill1(), submitted.getSkill1())
                && sameValue(existing.getSkill2(), submitted.getSkill2())
                && sameValue(existing.getSkill3(), submitted.getSkill3())
                && sameValue(existing.getEquipX(), submitted.getEquipX())
                && sameValue(existing.getEquipY(), submitted.getEquipY())
                && sameValue(existing.getEquipD(), submitted.getEquipD())
                && sameValue(existing.getEquipA(), submitted.getEquipA())
                && sameValue(existing.getEquipB(), submitted.getEquipB())
                && sameValue(existing.getPotentialRank(), submitted.getPotentialRank());
    }

    /**
     * 数值比较（null 按 0 处理）
     *
     * @param left  左值
     * @param right 右值
     * @return 是否相等
     */
    private boolean sameValue(Integer left, Integer right) {
        return (left == null ? 0 : left) == (right == null ? 0 : right);
    }
}
