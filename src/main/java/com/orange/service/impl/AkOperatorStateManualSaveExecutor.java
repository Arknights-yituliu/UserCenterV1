package com.orange.service.impl;

import com.orange.entity.po.AkOperatorStateManual;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.mapper.AkOperatorStateManualMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手动修改版干员数据保存写事务执行器
 *
 * <p>独立成 Bean 是为了让 {@code @Transactional} 生效（Spring 自调用不走代理），
 * 并由调用方在唯一键冲突/死锁时整事务回滚后重试。</p>
 *
 * <p>单个事务内完成：按 uid 读取现有手动版记录 → 逐条比对 → 批量插入新增行、
 * 按行 ID 更新属性变化行，完全相同的行不写库。</p>
 *
 * @author UserCenter
 */
@Component
public class AkOperatorStateManualSaveExecutor {

    /** 提交条数达到该阈值时改为按 uid 全量读取，避免超长 IN 列表 */
    private static final int FULL_SCAN_THRESHOLD = 100;

    /** 批量插入每条 SQL 的行数 */
    private static final int INSERT_BATCH_SIZE = 100;

    private final AkOperatorStateManualMapper manualMapper;

    /**
     * 构造器注入 Mapper
     *
     * @param manualMapper 手动修改版干员数据 Mapper
     */
    public AkOperatorStateManualSaveExecutor(AkOperatorStateManualMapper manualMapper) {
        this.manualMapper = manualMapper;
    }

    /**
     * 在一个写事务内保存用户的手动修改版干员数据
     *
     * @param uid       用户中心 UID
     * @param submitted 本次提交的干员记录（已归一化，数值字段与 owned 非空）
     * @return 新增/更新/未变更条数统计
     */
    @Transactional(rollbackFor = Exception.class)
    public OperatorSaveResultVO save(Long uid, List<AkOperatorStateManual> submitted) {
        Map<String, AkOperatorStateManual> existingMap = loadExisting(uid, submitted);
        LocalDateTime now = LocalDateTime.now();
        List<AkOperatorStateManual> toInsert = new ArrayList<>();
        List<AkOperatorStateManual> toUpdate = new ArrayList<>();
        int unchangedCount = 0;

        for (AkOperatorStateManual item : submitted) {
            AkOperatorStateManual existing = existingMap.get(item.getOperatorId());
            if (existing == null) {
                item.setUpdatedAt(now);
                toInsert.add(item);
                continue;
            }
            if (isSameState(existing, item)) {
                unchangedCount++;
                continue;
            }
            item.setId(existing.getId());
            item.setUpdatedAt(now);
            toUpdate.add(item);
        }

        for (int from = 0; from < toInsert.size(); from += INSERT_BATCH_SIZE) {
            int to = Math.min(from + INSERT_BATCH_SIZE, toInsert.size());
            manualMapper.batchInsert(toInsert.subList(from, to));
        }
        for (AkOperatorStateManual record : toUpdate) {
            manualMapper.updateManual(record);
        }
        return new OperatorSaveResultVO(toInsert.size(), toUpdate.size(), unchangedCount);
    }

    /**
     * 读取本次提交涉及的现有手动版记录并按干员编码建立内存映射
     *
     * <p>完整上传时按 uid 一次取全部；少量上传时按 operator_id IN (...) 点查。</p>
     *
     * @param uid       用户中心 UID
     * @param submitted 本次提交的干员记录
     * @return 干员编码到现有记录的映射
     */
    private Map<String, AkOperatorStateManual> loadExisting(Long uid, List<AkOperatorStateManual> submitted) {
        List<AkOperatorStateManual> rows;
        if (submitted.size() >= FULL_SCAN_THRESHOLD) {
            rows = manualMapper.selectByUid(uid);
        } else {
            List<String> operatorIds = new ArrayList<>(submitted.size());
            for (AkOperatorStateManual item : submitted) {
                operatorIds.add(item.getOperatorId());
            }
            rows = manualMapper.selectByUidAndIds(uid, operatorIds);
        }
        Map<String, AkOperatorStateManual> existingMap = new HashMap<>(Math.max(16, rows.size() * 2));
        for (AkOperatorStateManual row : rows) {
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
    private boolean isSameState(AkOperatorStateManual existing, AkOperatorStateManual submitted) {
        return sameValue(existing.getOwned(), submitted.getOwned())
                && sameValue(existing.getRarity(), submitted.getRarity())
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
