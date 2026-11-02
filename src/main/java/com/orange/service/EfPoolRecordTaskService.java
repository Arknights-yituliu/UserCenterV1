package com.orange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.HttpClientUtil;
import com.orange.common.util.IdGenerator;
import com.orange.common.util.LogUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RedisRateLimiter;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.efpool.EfEndfieldUserInfoDTO;
import com.orange.entity.po.EfCharacterPoolRecord;
import com.orange.entity.po.EfEndministratorInfo;
import com.orange.entity.po.EfPoolRecordTask;
import com.orange.entity.po.EfWeaponPoolRecord;
import com.orange.entity.vo.efpool.EfPoolRecordVO;
import com.orange.mapper.EfEndministratorInfoMapper;
import com.orange.mapper.EfPoolRecordTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 寻访记录导入任务服务
 *
 * <p>对外提供创建任务、查询任务状态与读取寻访结果的入口；后台每秒取一条未开始的任务，
 * 通过 token 换取服务拿到角色信息后，依次导入干员与武器寻访记录，最后标记任务完成。
 * 当前接口暂不做用户鉴权，任务归属完全由 taskId 控制。</p>
 *
 * @author UserCenter
 */
@Service
public class EfPoolRecordTaskService {

    /** 创建任务限流：同一 hgToken 在窗口内允许的请求次数 */
    private static final long CREATE_TASK_LIMIT = 1L;

    /** 创建任务限流窗口（秒） */
    private static final long CREATE_TASK_WINDOW_SECONDS = 60L;

    /** 任务有效窗口（毫秒）：创建超过该时长仍未开始的任务不再执行 */
    private static final long TASK_VALID_WINDOW_MS = 10 * 60 * 1000L;

    /** token 覆盖窗口（毫秒）：创建超过该时长的任务抹掉 hgToken，避免凭证长期留存 */
    private static final long TOKEN_MASK_WINDOW_MS = 15 * 60 * 1000L;

    /** token 覆盖回溯范围（毫秒）：只处理最近一段时间内的任务，避免全表扫描 */
    private static final long TOKEN_MASK_LOOKBACK_MS = 24 * 60 * 60 * 1000L;

    /** 过期 token 的覆盖占位值 */
    private static final String EXPIRED_TOKEN_PLACEHOLDER = "complete_114514";

    /** 限流业务标识，构成 Redis key 的中间段 */
    private static final String RATE_LIMIT_BIZ = "ef-pool-record-task";

    private final EfPoolRecordTaskMapper poolRecordTaskMapper;
    private final EfEndministratorInfoMapper endministratorInfoMapper;
    private final EfCharacterPoolRecordService characterPoolRecordService;
    private final EfWeaponPoolRecordService weaponPoolRecordService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final IdGenerator idGenerator = IdGenerator.getInstance();

    /** 本机 token 换取服务地址：入参 hgToken，返回 u8Token / roleId / uid / nickName */
    private final String tokenServiceUrl;

    /**
     * 构造器注入依赖
     *
     * @param poolRecordTaskMapper        任务 Mapper
     * @param endministratorInfoMapper    玩家角色信息 Mapper
     * @param characterPoolRecordService  干员寻访记录导入服务
     * @param weaponPoolRecordService     武器寻访记录导入服务
     * @param stringRedisTemplate         Redis 客户端，用于创建任务限流
     * @param objectMapper                JSON 序列化器
     * @param tokenServiceUrl             token 换取服务地址
     */
    public EfPoolRecordTaskService(EfPoolRecordTaskMapper poolRecordTaskMapper,
                                   EfEndministratorInfoMapper endministratorInfoMapper,
                                   EfCharacterPoolRecordService characterPoolRecordService,
                                   EfWeaponPoolRecordService weaponPoolRecordService,
                                   StringRedisTemplate stringRedisTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${user-center.ef.pool-record.token-service-url:http://127.0.0.1:10086/token}")
                                   String tokenServiceUrl) {
        this.poolRecordTaskMapper = poolRecordTaskMapper;
        this.endministratorInfoMapper = endministratorInfoMapper;
        this.characterPoolRecordService = characterPoolRecordService;
        this.weaponPoolRecordService = weaponPoolRecordService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.tokenServiceUrl = tokenServiceUrl;
    }

    /**
     * 创建寻访记录导入任务
     *
     * <p>同一 hgToken 每分钟只允许创建一次任务，避免频繁触发上游接口调用。</p>
     *
     * @param hgToken 鹰角通行证 token
     * @return 任务 ID，供前端轮询任务状态
     */
    public String createTask(String hgToken) {
        if (hgToken == null || hgToken.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "hgToken不能为空");
        }
        String limitKey = RedisKeyUtil.rate(RATE_LIMIT_BIZ, SignUtil.sha256(hgToken));
        boolean acquired;
        try {
            acquired = RedisRateLimiter.tryAcquire(stringRedisTemplate, limitKey,
                    CREATE_TASK_LIMIT, CREATE_TASK_WINDOW_SECONDS);
        } catch (Exception e) {
            LogUtil.error(EfPoolRecordTaskService.class, "创建寻访记录任务限流设施不可用，key=" + limitKey, e);
            throw new BusinessException(ResultCode.RATE_LIMITER_UNAVAILABLE);
        }
        if (!acquired) {
            throw new BusinessException(ResultCode.IP_RATE_LIMITED, "创建任务过于频繁，请稍后再试");
        }

        EfPoolRecordTask task = new EfPoolRecordTask();
        task.setTaskId("task" + idGenerator.nextId());
        task.setToken(hgToken);
        task.setStartFlag(false);
        task.setCompleteFlag(false);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        poolRecordTaskMapper.insert(task);
        return task.getTaskId();
    }

    /**
     * 查询任务状态：完成时返回该任务对应角色的玩家信息
     *
     * @param taskId 任务 ID
     * @return 玩家角色信息
     */
    public EfEndministratorInfo checkTask(String taskId) {
        EfPoolRecordTask task = requireTask(taskId);
        if (!Boolean.TRUE.equals(task.getCompleteFlag())) {
            throw new BusinessException(ResultCode.ILLEGAL_OPERATION, "任务尚未完成");
        }
        LambdaQueryWrapper<EfEndministratorInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EfEndministratorInfo::getRoleId, task.getRoleId());
        return endministratorInfoMapper.selectOne(queryWrapper);
    }

    /**
     * 读取任务对应角色的干员与武器寻访记录
     *
     * @param taskId 任务 ID
     * @return 干员与武器寻访记录
     */
    public EfPoolRecordVO getPoolRecordData(String taskId) {
        EfPoolRecordTask task = requireTask(taskId);
        List<EfCharacterPoolRecord> characterRecords = characterPoolRecordService.listByRoleId(task.getRoleId());
        List<EfWeaponPoolRecord> weaponRecords = weaponPoolRecordService.listByRoleId(task.getRoleId());
        return new EfPoolRecordVO(characterRecords, weaponRecords);
    }

    /**
     * 查询任务并校验存在性
     *
     * @param taskId 任务 ID
     * @return 任务记录
     */
    private EfPoolRecordTask requireTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "taskId不能为空");
        }
        EfPoolRecordTask task = poolRecordTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "任务不存在或已过期");
        }
        return task;
    }

    /**
     * 执行一个寻访记录导入任务
     *
     * <p>先通过 token 换取服务拿到 u8Token 与角色信息，再依次导入干员、武器寻访记录，
     * 完成后写入玩家角色信息并标记任务完成。</p>
     *
     * @param task 待执行任务
     */
    public void savePoolRecordAsync(EfPoolRecordTask task) {
        long startTime = System.currentTimeMillis();
        String roleId = null;
        try {
            EfEndfieldUserInfoDTO userInfo = getPlayerInfo(task);
            roleId = userInfo.getRoleId();
            LogUtil.info(EfPoolRecordTaskService.class, "roleId：{} 开始导入干员寻访记录", roleId);
            characterPoolRecordService.saveCharacterPoolRecordAsync(userInfo);
            LogUtil.info(EfPoolRecordTaskService.class, "roleId：{} 开始导入武器寻访记录", roleId);
            weaponPoolRecordService.saveWeaponPoolRecordAsync(userInfo);

            task.setCompleteFlag(true);
            task.setRoleId(roleId);
            task.setToken("token" + idGenerator.nextId());
            task.setUpdateTime(new Date());

            LambdaQueryWrapper<EfEndministratorInfo> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(EfEndministratorInfo::getRoleId, roleId);
            if (!endministratorInfoMapper.exists(queryWrapper)) {
                endministratorInfoMapper.insert(
                        new EfEndministratorInfo(userInfo.getUid(), roleId, userInfo.getNickName()));
            }
            poolRecordTaskMapper.updateById(task);
            LogUtil.info(EfPoolRecordTaskService.class, "roleId：{} 寻访记录导入任务完成，耗时 {} ms",
                    roleId, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            LogUtil.error(EfPoolRecordTaskService.class,
                    "roleId：" + roleId + " 寻访记录导入任务失败", e);
            throw e;
        }
    }

    /**
     * 调用本机 token 换取服务，把 hgToken 换成 u8Token 与角色信息
     *
     * @param task 任务记录，token 字段为 hgToken
     * @return 玩家角色信息
     */
    private EfEndfieldUserInfoDTO getPlayerInfo(EfPoolRecordTask task) {
        String responseText = HttpClientUtil.postForm(tokenServiceUrl,
                Map.of("hgToken", task.getToken() == null ? "" : task.getToken()), null);
        if (responseText == null) {
            throw new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "换取终末地角色信息失败");
        }
        try {
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                throw new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "换取终末地角色信息失败");
            }
            return objectMapper.treeToValue(data, EfEndfieldUserInfoDTO.class);
        } catch (JsonProcessingException e) {
            LogUtil.error(EfPoolRecordTaskService.class, "token 换取服务响应解析失败", e);
            throw new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "换取终末地角色信息失败");
        }
    }

    /**
     * 每分钟把创建时间在「当前时间-24 小时」到「当前时间-15 分钟」之间的任务 token 覆盖为占位值，
     * 避免 hgToken 长期留存导致数据泄露
     *
     * <p>限定时间区间只扫描最近 24 小时内的任务，避免全表更新带来的性能开销；
     * 已被覆盖的记录不会重复写入。</p>
     */
    @Scheduled(cron = "0 * * * * ?")
    public void maskExpiredToken() {
        long now = System.currentTimeMillis();
        Date endTime = new Date(now - TOKEN_MASK_WINDOW_MS);
        Date startTime = new Date(now - TOKEN_MASK_LOOKBACK_MS);
        try {
            int maskedCount = poolRecordTaskMapper.maskExpiredToken(startTime, endTime, EXPIRED_TOKEN_PLACEHOLDER);
            if (maskedCount > 0) {
                LogUtil.info(EfPoolRecordTaskService.class, "已覆盖 {} 条过期任务的 token", maskedCount);
            }
        } catch (Exception e) {
            LogUtil.error(EfPoolRecordTaskService.class, "覆盖过期任务 token 失败", e);
        }
    }

    /**
     * 每秒取一条最早创建且未开始的任务执行导入
     *
     * <p>超过有效窗口仍未开始的任务直接标记为已开始、不再执行，避免堆积旧任务。</p>
     */
    @Scheduled(cron = "* * * * * ?")
    public void loadingPoolRecord() {
        EfPoolRecordTask task = null;
        try {
            task = poolRecordTaskMapper.selectFirstByStartFlagFalseOrderByCreateTimeAsc();
            if (task == null) {
                return;
            }
            task.setStartFlag(true);
            task.setUpdateTime(new Date());
            poolRecordTaskMapper.updateById(task);
            if (task.getCreateTime() != null
                    && System.currentTimeMillis() - task.getCreateTime().getTime() < TASK_VALID_WINDOW_MS) {
                savePoolRecordAsync(task);
            }
            LogUtil.info(EfPoolRecordTaskService.class, "当前执行任务：{}", task.getTaskId());
        } catch (Exception e) {
            if (task != null) {
                task.setRoleId("ERROR_TASK");
                try {
                    poolRecordTaskMapper.updateById(task);
                } catch (Exception updateError) {
                    LogUtil.error(EfPoolRecordTaskService.class, "标记异常任务失败", updateError);
                }
            }
            LogUtil.error(EfPoolRecordTaskService.class,
                    "处理寻访记录任务失败，任务ID：" + (task == null ? "unknown" : task.getTaskId()), e);
        }
    }
}
