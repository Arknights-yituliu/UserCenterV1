package com.orange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.util.EfPoolRecordUtil;
import com.orange.common.util.HttpClientUtil;
import com.orange.common.util.IdGenerator;
import com.orange.common.util.LogUtil;
import com.orange.entity.dto.efpool.EfCharacterPoolRecordDTO;
import com.orange.entity.dto.efpool.EfCharacterPoolRecordResponseDTO;
import com.orange.entity.dto.efpool.EfEndfieldUserInfoDTO;
import com.orange.entity.po.EfCharacterPoolRecord;
import com.orange.mapper.EfCharacterPoolRecordMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 干员寻访记录导入服务
 *
 * <p>按卡池类型并发拉取终末地寻访记录接口，再分批写入 ef_character_pool_record。
 * 首次请求不传 seq_id 取最新一页，随后按 5 递减补齐全量历史记录。</p>
 *
 * <p>上游 list 会混入赠礼类记录（特许寻访满 60 抽赠送寻访情报书，kind=gift_intel_book），
 * 这类记录没有 charId / charName，落库时以 kind 作为 gift_ 前缀的伪角色编码保留，
 * 既避免产生空值行，也不丢记录条数。</p>
 *
 * @author UserCenter
 */
@Service
public class EfCharacterPoolRecordService {

    /** 特许寻访 */
    private static final String SPECIAL_POOL_TYPE = "E_CharacterGachaPoolType_Special";

    /** 重构寻访（同角色复刻卡池，按版本号聚合） */
    private static final String RERUN_POOL_TYPE = "E_CharacterGachaPoolType_Rerun";

    /** 辉光庆典（联合寻访，多 UP 六星卡池） */
    private static final String JOINT_POOL_TYPE = "E_CharacterGachaPoolType_Joint";

    /** 基础寻访（常驻） */
    private static final String STANDARD_POOL_TYPE = "E_CharacterGachaPoolType_Standard";

    /** 启程寻访（新手） */
    private static final String BEGINNER_POOL_TYPE = "E_CharacterGachaPoolType_Beginner";

    /** 新手卡池已有记录数达到该值后不再重复拉取 */
    private static final long BEGINNER_POOL_COMPLETE_COUNT = 40L;

    /** 单批写入数据库的记录数 */
    private static final int BATCH_SIZE = 200;

    /** 上游接口语言参数 */
    private static final String LANG = "zh-cn";

    /** 上游接口服务器 ID */
    private static final String SERVER_ID = "1";

    /** 干员寻访记录接口地址 */
    private static final String CHARACTER_RECORD_API = "https://ef-webview.hypergryph.com/api/record/char";

    private final EfCharacterPoolRecordMapper characterPoolRecordMapper;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator = IdGenerator.getInstance();

    /**
     * 构造器注入依赖
     *
     * @param characterPoolRecordMapper 干员寻访记录 Mapper
     * @param taskExecutor              后台任务执行器，用于并发请求不同卡池分页
     * @param objectMapper              JSON 序列化器
     */
    public EfCharacterPoolRecordService(EfCharacterPoolRecordMapper characterPoolRecordMapper,
                                        TaskExecutor taskExecutor,
                                        ObjectMapper objectMapper) {
        this.characterPoolRecordMapper = characterPoolRecordMapper;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 拉取并保存指定角色的全部干员寻访记录
     *
     * <p>逐个卡池类型取第一页，随后并发补齐历史分页；单个卡池类型无记录时跳过，
     * 不影响其它卡池类型导入。</p>
     *
     * @param userInfo 玩家角色信息（含 u8Token 与 roleId）
     */
    public void saveCharacterPoolRecordAsync(EfEndfieldUserInfoDTO userInfo) {
        long startTime = System.currentTimeMillis();
        String roleId = userInfo.getRoleId();
        String u8Token = userInfo.getU8Token();
        List<EfCharacterPoolRecord> records = new ArrayList<>();
        int giftCount = 0;
        int skippedCount = 0;
        LogUtil.info(EfCharacterPoolRecordService.class, "roleId：{} 开始处理干员寻访记录导入任务", roleId);

        try {
            List<String> poolTypes = new ArrayList<>(List.of(SPECIAL_POOL_TYPE, RERUN_POOL_TYPE,
                    JOINT_POOL_TYPE, STANDARD_POOL_TYPE));
            LambdaQueryWrapper<EfCharacterPoolRecord> beginnerWrapper = new LambdaQueryWrapper<>();
            beginnerWrapper.eq(EfCharacterPoolRecord::getRoleId, roleId)
                    .eq(EfCharacterPoolRecord::getPoolType, BEGINNER_POOL_TYPE)
                    // 只统计真实抽卡行：赠礼类记录以 gift_ 伪编码落库，不应计入 40 抽上限
                    .notLikeRight(EfCharacterPoolRecord::getCharId, EfPoolRecordUtil.GIFT_CHAR_PREFIX);
            if (characterPoolRecordMapper.selectCount(beginnerWrapper) < BEGINNER_POOL_COMPLETE_COUNT) {
                poolTypes.add(BEGINNER_POOL_TYPE);
            }

            List<CompletableFuture<EfCharacterPoolRecordResponseDTO>> futures = new ArrayList<>();
            for (String poolType : poolTypes) {
                EfCharacterPoolRecordResponseDTO firstPage = requestCharacterPoolRecordApi(u8Token, poolType, null);
                if (!isUsable(firstPage)) {
                    LogUtil.info(EfCharacterPoolRecordService.class,
                            "roleId：{} 卡池类型 {} 暂无寻访记录，跳过", roleId, poolType);
                    continue;
                }

                List<EfCharacterPoolRecordDTO> firstPageList = firstPage.getData().getList();
                for (EfCharacterPoolRecordDTO dto : firstPageList) {
                    if (EfPoolRecordUtil.isUnusableRecord(dto)) {
                        skippedCount++;
                        logUnusableRecord(roleId, poolType, dto);
                        continue;
                    }
                    if (EfPoolRecordUtil.isGiftRecord(dto)) {
                        giftCount++;
                    }
                    records.add(convertToEntity(dto, roleId, poolType));
                }

                String lastSeqId = firstPageList.get(firstPageList.size() - 1).getSeqId();
                Integer existingMaxSeqId = characterPoolRecordMapper.getMaxSeqIdNumberByPoolType(roleId, poolType);
                for (String seqId : EfPoolRecordUtil.initSeqIdList(lastSeqId, existingMaxSeqId)) {
                    futures.add(CompletableFuture.supplyAsync(
                            () -> requestCharacterPoolRecordApi(u8Token, poolType, seqId), taskExecutor));
                }
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<EfCharacterPoolRecordResponseDTO> future : futures) {
                try {
                    EfCharacterPoolRecordResponseDTO page = future.get();
                    if (!isUsable(page)) {
                        continue;
                    }
                    for (EfCharacterPoolRecordDTO dto : page.getData().getList()) {
                        if (EfPoolRecordUtil.isUnusableRecord(dto)) {
                            skippedCount++;
                            logUnusableRecord(roleId, page.getPoolType(), dto);
                            continue;
                        }
                        if (EfPoolRecordUtil.isGiftRecord(dto)) {
                            giftCount++;
                        }
                        records.add(convertToEntity(dto, roleId, page.getPoolType()));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LogUtil.error(EfCharacterPoolRecordService.class, "roleId：" + roleId + " 异步任务被中断", e);
                } catch (ExecutionException e) {
                    LogUtil.error(EfCharacterPoolRecordService.class,
                            "roleId：" + roleId + " 异步任务执行失败", e.getCause());
                }
            }
        } catch (Exception e) {
            LogUtil.error(EfCharacterPoolRecordService.class, "roleId：" + roleId + " 干员寻访记录导入失败", e);
        }

        batchInsert(records);
        LogUtil.info(EfCharacterPoolRecordService.class,
                "roleId：{} 干员寻访记录导入结束，待写入 {} 条（赠礼类 {} 条），跳过脏数据 {} 条，耗时 {} ms",
                roleId, records.size(), giftCount, skippedCount, System.currentTimeMillis() - startTime);
    }

    /**
     * 查询指定角色的全部干员寻访记录
     *
     * @param roleId 终末地游戏内角色 UID
     * @return 寻访记录列表，无数据时返回空列表
     */
    public List<EfCharacterPoolRecord> listByRoleId(String roleId) {
        LambdaQueryWrapper<EfCharacterPoolRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EfCharacterPoolRecord::getRoleId, roleId);
        return characterPoolRecordMapper.selectList(queryWrapper);
    }

    /**
     * 分批写入干员寻访记录，重复记录由唯一键冲突时「只补不毁」地更新
     *
     * @param records 待写入记录
     */
    private void batchInsert(List<EfCharacterPoolRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        int affectedRows = 0;
        for (int from = 0; from < records.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, records.size());
            List<EfCharacterPoolRecord> batch = records.subList(from, to);
            for (EfCharacterPoolRecord record : batch) {
                record.setId(idGenerator.nextId());
            }
            try {
                affectedRows += characterPoolRecordMapper.batchUpsert(batch);
            } catch (Exception e) {
                LogUtil.error(EfCharacterPoolRecordService.class,
                        "干员寻访记录批处理写入失败：" + from + "-" + to, e);
            }
        }
        LogUtil.info(EfCharacterPoolRecordService.class, "干员寻访记录写入完成，影响 {} 行", affectedRows);
    }

    /**
     * 请求终末地干员寻访记录接口
     *
     * @param u8Token  用于鉴权的 u8Token
     * @param poolType 卡池类型
     * @param seqId    分页序列 ID，为 null 时取最新一页
     * @return 接口响应；请求失败或响应无法解析时返回 code=500 的响应
     */
    private EfCharacterPoolRecordResponseDTO requestCharacterPoolRecordApi(String u8Token, String poolType, String seqId) {
        String url = CHARACTER_RECORD_API + "?lang=" + LANG
                + "&pool_type=" + poolType
                + "&token=" + EfPoolRecordUtil.encodeToken(u8Token)
                + "&server_id=" + SERVER_ID;
        if (seqId != null) {
            url += "&seq_id=" + seqId;
        }

        String responseText = HttpClientUtil.get(url, EfPoolRecordUtil.buildHeaders());
        if (responseText == null) {
            return new EfCharacterPoolRecordResponseDTO(500, null, "请求终末地干员寻访记录失败", poolType);
        }
        try {
            EfCharacterPoolRecordResponseDTO response = objectMapper.readValue(
                    responseText, new TypeReference<EfCharacterPoolRecordResponseDTO>() {
                    });
            response.setPoolType(poolType);
            return response;
        } catch (JsonProcessingException e) {
            LogUtil.error(EfCharacterPoolRecordService.class, "干员寻访记录响应解析失败，poolType=" + poolType, e);
            return new EfCharacterPoolRecordResponseDTO(500, null, "终末地干员寻访记录响应解析失败", poolType);
        }
    }

    /**
     * 判断响应是否包含可用的寻访记录
     *
     * @param response 接口响应
     * @return true=成功且有记录
     */
    private boolean isUsable(EfCharacterPoolRecordResponseDTO response) {
        return response != null
                && response.getCode() != null && response.getCode() == 0
                && response.getData() != null
                && response.getData().getList() != null
                && !response.getData().getList().isEmpty();
    }

    /**
     * 记录一条脏数据日志：既没有角色编码也没有记录类型，无法落库
     *
     * @param roleId   终末地游戏内角色 UID
     * @param poolType 卡池类型
     * @param dto      单条记录，可能为 null
     */
    private void logUnusableRecord(String roleId, String poolType, EfCharacterPoolRecordDTO dto) {
        LogUtil.warn(EfCharacterPoolRecordService.class,
                "roleId：{} 卡池类型 {} seqId：{} 既无角色编码也无记录类型，跳过该条记录",
                roleId, poolType, dto == null ? null : dto.getSeqId());
    }

    /**
     * 把单条寻访记录 DTO 转换为数据库实体
     *
     * <p>赠礼类记录没有 charId / charName，由 {@link EfPoolRecordUtil} 统一映射为
     * gift_ 前缀的伪角色编码与展示名，避免落库出现空值行。</p>
     *
     * @param dto      单条记录
     * @param roleId   终末地游戏内角色 UID
     * @param poolType 卡池类型
     * @return 数据库实体
     */
    private EfCharacterPoolRecord convertToEntity(EfCharacterPoolRecordDTO dto, String roleId, String poolType) {
        return new EfCharacterPoolRecord(
                idGenerator.nextId(),
                roleId,
                dto.getPoolId(),
                dto.getPoolName(),
                EfPoolRecordUtil.resolveCharId(dto),
                EfPoolRecordUtil.resolveCharName(dto),
                dto.getRarity(),
                dto.getIsFree(),
                dto.getIsNew(),
                dto.getGachaTs(),
                dto.getSeqId(),
                LANG,
                poolType,
                SERVER_ID
        );
    }
}
