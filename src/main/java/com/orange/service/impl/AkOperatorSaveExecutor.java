package com.orange.service.impl;

import com.orange.entity.po.AkItemData;
import com.orange.entity.po.AkPlayerInfo;
import com.orange.entity.po.AkOperatorState;
import com.orange.entity.po.AkSkinState;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkItemDataMapper;
import com.orange.mapper.AkPlayerInfoMapper;
import com.orange.mapper.AkPlayerInfoSnapshotMapper;
import com.orange.mapper.AkOperatorStateMapper;
import com.orange.mapper.AkSkinStateMapper;
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
 * <p>单个事务内依次完成：核对/创建本人绑定 → 角色信息比对更新或插入 → 干员比对后增量写入 →
 * 物品存量 JSON 整份覆盖写入 → 皮肤集合全量对齐 → player/info 文本原样覆盖。</p>
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
    private final AkOperatorStateMapper operatorMapper;
    private final AkItemDataMapper itemMapper;
    private final AkSkinStateMapper skinMapper;
    private final AkPlayerInfoSnapshotMapper playerSnapshotMapper;

    /**
     * 构造器注入 Mapper
     *
     * @param bindingMapper        绑定关系 Mapper
     * @param playerInfoMapper     角色信息 Mapper
     * @param operatorMapper       干员数据 Mapper
     * @param itemMapper           物品存量 Mapper
     * @param skinMapper           皮肤数据 Mapper
     * @param playerSnapshotMapper player/info 数据镜像 Mapper
     */
    public AkOperatorSaveExecutor(AkAccountBindingMapper bindingMapper,
                                  AkPlayerInfoMapper playerInfoMapper,
                                  AkOperatorStateMapper operatorMapper,
                                  AkItemDataMapper itemMapper,
                                  AkSkinStateMapper skinMapper,
                                  AkPlayerInfoSnapshotMapper playerSnapshotMapper) {
        this.bindingMapper = bindingMapper;
        this.playerInfoMapper = playerInfoMapper;
        this.operatorMapper = operatorMapper;
        this.itemMapper = itemMapper;
        this.skinMapper = skinMapper;
        this.playerSnapshotMapper = playerSnapshotMapper;
    }

    /**
     * 在一个写事务内保存绑定关系、角色信息、干员数据、物品存量、皮肤数据与 player/info 文本
     *
     * @param akUid              游戏账号 UID
     * @param uid                用户中心 UID
     * @param submittedOperators 本次提交的干员记录（已归一化，数值字段非空）
     * @param itemData           本次上报的物品 JSON 文本，null 表示不上报物品
     * @param submittedSkins     本次提交的皮肤记录（已归一化），null 或空表示不上报皮肤
     * @param playerData         本次上报的 player/info 文本，null 或空表示不上报，已有镜像保持不变
     * @return 新增/更新/未变更条数统计（仅统计干员）
     */
    @Transactional(rollbackFor = Exception.class)
    public OperatorSaveResultVO save(String akUid,
                                     Long uid,
                                     List<AkOperatorState> submittedOperators,
                                     String itemData,
                                     List<AkSkinState> submittedSkins,
                                     String playerData) {
        ensureBinding(akUid, uid);
        savePlayerInfo(akUid);
        saveItems(akUid, itemData);
        saveSkins(akUid, submittedSkins);
        savePlayerData(akUid, playerData);
        return saveOperators(akUid, submittedOperators);
    }

    /**
     * 原样保存 player/info 文本：不做解析、不做结构校验，整段覆盖该账号已有镜像
     *
     * <p>文本为 null 或空串时不发任何 SQL，已有镜像保持不变。</p>
     *
     * @param akUid      游戏账号 UID
     * @param playerData 前端上报的文本原文
     */
    private void savePlayerData(String akUid, String playerData) {
        if (playerData == null || playerData.isEmpty()) {
            return;
        }
        playerSnapshotMapper.upsertPlayerData(akUid, playerData);
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
    private OperatorSaveResultVO saveOperators(String akUid, List<AkOperatorState> submitted) {
        Map<String, AkOperatorState> existingMap = loadExisting(akUid, submitted);
        LocalDateTime now = LocalDateTime.now();
        List<AkOperatorState> toInsert = new ArrayList<>();
        List<AkOperatorState> toUpdate = new ArrayList<>();
        int unchangedCount = 0;

        for (AkOperatorState item : submitted) {
            AkOperatorState existing = existingMap.get(item.getOperatorId());
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
            operatorMapper.batchInsert(toInsert.subList(from, to));
        }
        for (AkOperatorState record : toUpdate) {
            operatorMapper.updateState(record);
        }
        return new OperatorSaveResultVO(toInsert.size(), toUpdate.size(), unchangedCount);
    }

    /**
     * 保存物品存量 JSON：携带 items 时整份覆盖该账号的物品数据，省略时保持原数据不变
     *
     * <p>物品数据不做行级拆分与比对，每次携带 items 都以全量快照覆盖，一条 upsert 完成写入。</p>
     *
     * @param akUid    游戏账号 UID
     * @param itemData 服务端已校验并序列化的物品 JSON 文本，null 表示本次不上报物品
     */
    private void saveItems(String akUid, String itemData) {
        if (itemData == null) {
            return;
        }
        itemMapper.upsertItemData(akUid, itemData, LocalDateTime.now());
    }

    /**
     * 全量对齐皮肤集合：补新增、刷新获得时间变化的行、删除本次未提交的旧行
     *
     * <p>皮肤是账号维度的完整集合（森空岛 player/info 一次返回该账号全部已拥有皮肤），
     * 因此携带 skins 时以本次提交为准做对齐：未提交的旧皮肤会被删除，使数据能自愈。
     * 省略 skins（null 或空数组）时直接返回，已有皮肤数据保持不变。</p>
     *
     * <p>只写真正变化的行：新增行与获得时间变化的行才落库，时间未变的行不产生 UPDATE，
     * 保证 updated_at 表示「最后一次实际变更」。</p>
     *
     * @param akUid     游戏账号 UID
     * @param submitted 本次提交的皮肤记录（已归一化），null 或空表示不上报皮肤
     */
    private void saveSkins(String akUid, List<AkSkinState> submitted) {
        if (submitted == null || submitted.isEmpty()) {
            return;
        }
        Map<String, AkSkinState> existingMap = new HashMap<>(Math.max(16, submitted.size() * 2));
        for (AkSkinState row : skinMapper.selectByAkUid(akUid)) {
            existingMap.put(row.getSkinId(), row);
        }

        LocalDateTime now = LocalDateTime.now();
        List<AkSkinState> toInsert = new ArrayList<>();
        List<String> submittedIds = new ArrayList<>(submitted.size());

        for (AkSkinState item : submitted) {
            submittedIds.add(item.getSkinId());
            AkSkinState existing = existingMap.get(item.getSkinId());
            if (existing == null) {
                item.setUpdatedAt(now);
                toInsert.add(item);
                continue;
            }
            if (sameValue(existing.getGainTs(), item.getGainTs())) {
                continue;
            }
            item.setId(existing.getId());
            item.setUpdatedAt(now);
            skinMapper.updateGainTs(item);
        }

        for (int from = 0; from < toInsert.size(); from += INSERT_BATCH_SIZE) {
            int to = Math.min(from + INSERT_BATCH_SIZE, toInsert.size());
            skinMapper.batchInsert(toInsert.subList(from, to));
        }
        // submittedIds 必然非空：本方法已在入口拒绝空集合，避免拼出 NOT IN () 清空全表
        skinMapper.deleteByAkUidAndNotIn(akUid, submittedIds);
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
    private Map<String, AkOperatorState> loadExisting(String akUid,
                                                              List<AkOperatorState> submitted) {
        List<AkOperatorState> rows;
        if (submitted.size() >= FULL_SCAN_THRESHOLD) {
            rows = operatorMapper.selectByAkUid(akUid);
        } else {
            List<String> operatorIds = new ArrayList<>(submitted.size());
            for (AkOperatorState item : submitted) {
                operatorIds.add(item.getOperatorId());
            }
            rows = operatorMapper.selectByAkUidAndIds(akUid, operatorIds);
        }
        Map<String, AkOperatorState> existingMap = new HashMap<>(Math.max(16, rows.size() * 2));
        for (AkOperatorState row : rows) {
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
    private boolean isSameState(AkOperatorState existing, AkOperatorState submitted) {
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
