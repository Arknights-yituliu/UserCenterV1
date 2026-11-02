package com.orange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.util.EfPoolRecordUtil;
import com.orange.common.util.HttpClientUtil;
import com.orange.common.util.IdGenerator;
import com.orange.common.util.LogUtil;
import com.orange.entity.dto.efpool.EfEndfieldUserInfoDTO;
import com.orange.entity.dto.efpool.EfWeaponPoolRecordDTO;
import com.orange.entity.dto.efpool.EfWeaponPoolRecordResponseDTO;
import com.orange.entity.po.EfWeaponPoolRecord;
import com.orange.mapper.EfWeaponPoolRecordMapper;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 武器寻访记录导入服务
 *
 * <p>拉取终末地武器寻访记录接口并分批写入 weapon_pool_record：
 * 先取最新一页，再按 5 递减补齐数据库缺失的历史记录。</p>
 *
 * @author UserCenter
 */
@Service
public class EfWeaponPoolRecordService {

    /** 单批写入数据库的记录数 */
    private static final int BATCH_SIZE = 200;

    /** 上游接口语言参数 */
    private static final String LANG = "zh-cn";

    /** 上游接口服务器 ID */
    private static final String SERVER_ID = "1";

    /** 武器寻访记录接口地址 */
    private static final String WEAPON_RECORD_API = "https://ef-webview.hypergryph.com/api/record/weapon";

    private final EfWeaponPoolRecordMapper weaponPoolRecordMapper;
    private final TaskExecutor taskExecutor;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator = IdGenerator.getInstance();

    /**
     * 构造器注入依赖
     *
     * @param weaponPoolRecordMapper 武器寻访记录 Mapper
     * @param taskExecutor           后台任务执行器，用于并发请求历史分页
     * @param objectMapper           JSON 序列化器
     */
    public EfWeaponPoolRecordService(EfWeaponPoolRecordMapper weaponPoolRecordMapper,
                                     TaskExecutor taskExecutor,
                                     ObjectMapper objectMapper) {
        this.weaponPoolRecordMapper = weaponPoolRecordMapper;
        this.taskExecutor = taskExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 拉取并保存指定角色的全部武器寻访记录
     *
     * @param userInfo 玩家角色信息（含 u8Token 与 roleId）
     */
    public void saveWeaponPoolRecordAsync(EfEndfieldUserInfoDTO userInfo) {
        long startTime = System.currentTimeMillis();
        String roleId = userInfo.getRoleId();
        String u8Token = userInfo.getU8Token();
        List<EfWeaponPoolRecord> records = new ArrayList<>();
        LogUtil.info(EfWeaponPoolRecordService.class, "roleId：{} 开始处理武器寻访记录导入任务", roleId);

        try {
            Integer existingMaxSeqId = weaponPoolRecordMapper.getMaxSeqIdNumber(roleId);
            EfWeaponPoolRecordResponseDTO firstPage = requestWeaponPoolRecordApi(u8Token, null);
            if (!isUsable(firstPage)) {
                LogUtil.info(EfWeaponPoolRecordService.class, "roleId：{} 暂无武器寻访记录", roleId);
                return;
            }

            List<EfWeaponPoolRecordDTO> firstPageList = firstPage.getData().getList();
            for (EfWeaponPoolRecordDTO dto : firstPageList) {
                records.add(convertToEntity(dto, roleId));
            }

            String lastSeqId = firstPageList.get(firstPageList.size() - 1).getSeqId();
            List<CompletableFuture<EfWeaponPoolRecordResponseDTO>> futures = new ArrayList<>();
            for (String seqId : EfPoolRecordUtil.initSeqIdList(lastSeqId, existingMaxSeqId)) {
                futures.add(CompletableFuture.supplyAsync(
                        () -> requestWeaponPoolRecordApi(u8Token, seqId), taskExecutor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (CompletableFuture<EfWeaponPoolRecordResponseDTO> future : futures) {
                try {
                    EfWeaponPoolRecordResponseDTO page = future.get();
                    if (!isUsable(page)) {
                        continue;
                    }
                    for (EfWeaponPoolRecordDTO dto : page.getData().getList()) {
                        records.add(convertToEntity(dto, roleId));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LogUtil.error(EfWeaponPoolRecordService.class, "roleId：" + roleId + " 异步任务被中断", e);
                } catch (ExecutionException e) {
                    LogUtil.error(EfWeaponPoolRecordService.class,
                            "roleId：" + roleId + " 异步任务执行失败", e.getCause());
                }
            }
        } catch (Exception e) {
            LogUtil.error(EfWeaponPoolRecordService.class, "roleId：" + roleId + " 武器寻访记录导入失败", e);
        }

        batchInsert(records);
        LogUtil.info(EfWeaponPoolRecordService.class, "roleId：{} 武器寻访记录导入结束，待写入 {} 条，耗时 {} ms",
                roleId, records.size(), System.currentTimeMillis() - startTime);
    }

    /**
     * 查询指定角色的全部武器寻访记录
     *
     * @param roleId 终末地游戏内角色 UID
     * @return 寻访记录列表，无数据时返回空列表
     */
    public List<EfWeaponPoolRecord> listByRoleId(String roleId) {
        LambdaQueryWrapper<EfWeaponPoolRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EfWeaponPoolRecord::getRoleId, roleId);
        return weaponPoolRecordMapper.selectList(queryWrapper);
    }

    /**
     * 分批写入武器寻访记录，重复记录由 INSERT IGNORE 自动忽略
     *
     * @param records 待写入记录
     */
    private void batchInsert(List<EfWeaponPoolRecord> records) {
        if (records.isEmpty()) {
            return;
        }
        int successCount = 0;
        for (int from = 0; from < records.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, records.size());
            List<EfWeaponPoolRecord> batch = records.subList(from, to);
            for (EfWeaponPoolRecord record : batch) {
                record.setId(idGenerator.nextId());
            }
            try {
                weaponPoolRecordMapper.batchInsert(batch);
                successCount += batch.size();
            } catch (Exception e) {
                LogUtil.error(EfWeaponPoolRecordService.class,
                        "武器寻访记录批处理插入失败：" + from + "-" + to, e);
            }
        }
        LogUtil.info(EfWeaponPoolRecordService.class, "武器寻访记录插入完成，提交 {} 条", successCount);
    }

    /**
     * 请求终末地武器寻访记录接口
     *
     * @param u8Token 用于鉴权的 u8Token
     * @param seqId   分页序列 ID，为 null 时取最新一页
     * @return 接口响应；请求失败或响应无法解析时返回 code=500 的响应
     */
    private EfWeaponPoolRecordResponseDTO requestWeaponPoolRecordApi(String u8Token, String seqId) {
        String url = WEAPON_RECORD_API + "?lang=" + LANG
                + "&token=" + EfPoolRecordUtil.encodeToken(u8Token)
                + "&server_id=" + SERVER_ID;
        if (seqId != null) {
            url += "&seq_id=" + seqId;
        }

        String responseText = HttpClientUtil.get(url, EfPoolRecordUtil.buildHeaders());
        if (responseText == null) {
            return new EfWeaponPoolRecordResponseDTO(500, null, "请求终末地武器寻访记录失败");
        }
        try {
            return objectMapper.readValue(responseText, new TypeReference<EfWeaponPoolRecordResponseDTO>() {
            });
        } catch (JsonProcessingException e) {
            LogUtil.error(EfWeaponPoolRecordService.class, "武器寻访记录响应解析失败", e);
            return new EfWeaponPoolRecordResponseDTO(500, null, "终末地武器寻访记录响应解析失败");
        }
    }

    /**
     * 判断响应是否包含可用的寻访记录
     *
     * @param response 接口响应
     * @return true=成功且有记录
     */
    private boolean isUsable(EfWeaponPoolRecordResponseDTO response) {
        return response != null
                && response.getCode() != null && response.getCode() == 0
                && response.getData() != null
                && response.getData().getList() != null
                && !response.getData().getList().isEmpty();
    }

    /**
     * 把单条武器寻访记录 DTO 转换为数据库实体
     *
     * @param dto    单条记录
     * @param roleId 终末地游戏内角色 UID
     * @return 数据库实体
     */
    private EfWeaponPoolRecord convertToEntity(EfWeaponPoolRecordDTO dto, String roleId) {
        return new EfWeaponPoolRecord(
                idGenerator.nextId(),
                roleId,
                dto.getPoolId(),
                dto.getPoolName(),
                dto.getWeaponId(),
                dto.getWeaponName(),
                dto.getWeaponType(),
                dto.getRarity(),
                dto.getIsNew(),
                dto.getGachaTs(),
                dto.getSeqId(),
                LANG,
                SERVER_ID
        );
    }
}
