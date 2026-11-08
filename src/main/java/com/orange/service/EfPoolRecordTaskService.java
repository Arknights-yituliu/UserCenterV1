package com.orange.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.EfTokenExchangeUtil;
import com.orange.common.util.IdGenerator;
import com.orange.common.util.LogUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RedisRateLimiter;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.efpool.EfEndfieldUserInfoDTO;
import com.orange.entity.po.EfAccountBinding;
import com.orange.entity.po.EfCharacterPoolRecord;
import com.orange.entity.vo.efpool.EfPoolRecordIdentityVO;
import com.orange.entity.po.EfPlayerPoolRecordTask;
import com.orange.entity.po.EfWeaponPoolRecord;
import com.orange.entity.vo.efpool.EfPoolRecordAccountVO;
import com.orange.entity.vo.efpool.EfPoolRecordVO;
import com.orange.mapper.EfAccountBindingMapper;
import com.orange.mapper.EfPoolRecordTaskMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 寻访记录导入任务服务
 *
 * <p>对外提供创建任务、查询任务状态与读取寻访结果的入口；后台每秒取一条未开始的任务，
 * 通过内置的 token 换取工具类拿到角色信息后，依次导入干员与武器寻访记录，最后标记任务完成。
 * 匿名接口已下线，所有任务均由鉴权接口创建并记录 owner_uid，
 * 导入完成后把角色档案与归属写入 ef_account_binding，供登录用户找回自己的寻访记录。</p>
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
    private final EfAccountBindingMapper accountBindingMapper;
    private final EfCharacterPoolRecordService characterPoolRecordService;
    private final EfWeaponPoolRecordService weaponPoolRecordService;
    private final StringRedisTemplate stringRedisTemplate;
    private final IdGenerator idGenerator = IdGenerator.getInstance();

    /**
     * 构造器注入依赖
     *
     * @param poolRecordTaskMapper        任务 Mapper
     * @param accountBindingMapper        终末地角色绑定关系 Mapper，用于记录归属与角色档案
     * @param characterPoolRecordService  干员寻访记录导入服务
     * @param weaponPoolRecordService     武器寻访记录导入服务
     * @param stringRedisTemplate         Redis 客户端，用于创建任务限流
     */
    public EfPoolRecordTaskService(EfPoolRecordTaskMapper poolRecordTaskMapper,
                                   EfAccountBindingMapper accountBindingMapper,
                                   EfCharacterPoolRecordService characterPoolRecordService,
                                   EfWeaponPoolRecordService weaponPoolRecordService,
                                   StringRedisTemplate stringRedisTemplate) {
        this.poolRecordTaskMapper = poolRecordTaskMapper;
        this.accountBindingMapper = accountBindingMapper;
        this.characterPoolRecordService = characterPoolRecordService;
        this.weaponPoolRecordService = weaponPoolRecordService;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 创建寻访记录导入任务
     *
     * <p>同一 hgToken 每分钟只允许创建一次任务，避免频繁触发上游接口调用。
     * 任务记录创建者归属，导入完成后据此写入角色绑定关系。</p>
     *
     * @param hgToken  鹰角通行证 token
     * @param ownerUid 创建任务的用户中心 UID
     * @return 任务 ID，供前端轮询任务状态
     */
    public String createTask(String hgToken, Long ownerUid) {
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

        EfPlayerPoolRecordTask task = new EfPlayerPoolRecordTask();
        task.setTaskId("task" + idGenerator.nextId());
        task.setToken(hgToken);
        task.setOwnerUid(ownerUid);
        task.setStartFlag(false);
        task.setCompleteFlag(false);
        task.setCreateTime(new Date());
        task.setUpdateTime(new Date());
        poolRecordTaskMapper.insert(task);
        return task.getTaskId();
    }

    /**
     * 查询任务状态并校验任务归属当前用户
     *
     * @param taskId   任务 ID
     * @param ownerUid 当前登录用户中心 UID
     * @return 玩家角色信息
     */
    public EfPoolRecordIdentityVO checkTask(String taskId, Long ownerUid) {
        return checkCompletedTask(requireOwnedTask(taskId, ownerUid));
    }

    /**
     * 校验任务已完成并返回对应角色信息
     *
     * @param task 任务记录
     * @return 玩家角色信息
     */
    private EfPoolRecordIdentityVO checkCompletedTask(EfPlayerPoolRecordTask task) {
        if (!Boolean.TRUE.equals(task.getCompleteFlag())) {
            throw new BusinessException(ResultCode.ILLEGAL_OPERATION, "任务尚未完成");
        }
        // 角色档案统一存放在 ef_account_binding：同一角色可能同时存在玩家数据绑定、
        // 匿名档案与多个用户绑定，取最近更新的一条「已导入寻访记录」的档案
        LambdaQueryWrapper<EfAccountBinding> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EfAccountBinding::getRoleId, task.getRoleId())
                .eq(EfAccountBinding::getPoolRecordImported, 1)
                .orderByDesc(EfAccountBinding::getUpdateTime)
                .last("LIMIT 1");
        EfAccountBinding binding = accountBindingMapper.selectOne(queryWrapper);
        if (binding == null) {
            return null;
        }
        return new EfPoolRecordIdentityVO(binding.getHgUid(), binding.getRoleId(), binding.getNickName());
    }

    /**
     * 查询当前用户导入过寻访记录的终末地账户列表
     *
     * <p>归属与角色档案都取自 ef_account_binding：鉴权接口创建的导入任务完成后会写入该表，
     * 并把 pool_record_imported 置为 1。同一角色可能同时存在玩家数据绑定与寻访绑定，
     * 这里只保留已导入过寻访记录的绑定，并按 roleId 去重。</p>
     *
     * @param ownerUid 用户中心 UID
     * @return 已导入账户列表，按绑定最近更新时间倒序；无记录时返回空列表
     */
    public List<EfPoolRecordAccountVO> listImportedAccounts(Long ownerUid) {
        // owner_uid = 0 是旧匿名导入的档案，不对应任何用户；传入非法归属时直接返回空，避免暴露匿名历史
        if (ownerUid == null || ownerUid <= 0) {
            return new ArrayList<>();
        }
        List<EfAccountBinding> bindings = accountBindingMapper.selectByOwnerOrderByUpdateTime(ownerUid);
        // 绑定列表已按最近导入时间倒序，同角色只保留首条即可
        Map<String, EfAccountBinding> latestByRoleId = new LinkedHashMap<>();
        for (EfAccountBinding binding : bindings) {
            if (Integer.valueOf(1).equals(binding.getPoolRecordImported())) {
                latestByRoleId.putIfAbsent(binding.getRoleId(), binding);
            }
        }

        List<EfPoolRecordAccountVO> accounts = new ArrayList<>(latestByRoleId.size());
        for (Map.Entry<String, EfAccountBinding> entry : latestByRoleId.entrySet()) {
            accounts.add(new EfPoolRecordAccountVO(entry.getKey(), entry.getValue().getNickName(),
                    entry.getValue().getUpdateTime(),
                    Integer.valueOf(1).equals(entry.getValue().getPlayerDataImported())));
        }
        return accounts;
    }

    /**
     * 按归属用户读取指定角色的干员与武器寻访记录
     *
     * <p>先校验当前用户对该角色存在绑定关系，避免通过遍历 roleId 读取他人记录。</p>
     *
     * @param ownerUid 用户中心 UID
     * @param roleId   终末地游戏内角色 UID
     * @return 干员与武器寻访记录
     */
    public EfPoolRecordVO getPoolRecordData(Long ownerUid, String roleId) {
        if (roleId == null || roleId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "roleId不能为空");
        }
        if (accountBindingMapper.countBindingByRoleId(roleId, ownerUid) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        List<EfCharacterPoolRecord> characterRecords = characterPoolRecordService.listByRoleId(roleId);
        List<EfWeaponPoolRecord> weaponRecords = weaponPoolRecordService.listByRoleId(roleId);
        return new EfPoolRecordVO(characterRecords, weaponRecords);
    }

    /**
     * 查询任务并校验存在性
     *
     * @param taskId 任务 ID
     * @return 任务记录
     */
    private EfPlayerPoolRecordTask requireTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "taskId不能为空");
        }
        EfPlayerPoolRecordTask task = poolRecordTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "任务不存在或已过期");
        }
        return task;
    }

    /**
     * 查询任务并校验归属当前用户
     *
     * <p>旧的不鉴权接口创建的任务 owner_uid 为 null，在鉴权接口中一律视为无权访问。</p>
     *
     * @param taskId   任务 ID
     * @param ownerUid 当前登录用户中心 UID
     * @return 任务记录
     */
    private EfPlayerPoolRecordTask requireOwnedTask(String taskId, Long ownerUid) {
        EfPlayerPoolRecordTask task = requireTask(taskId);
        if (ownerUid == null || task.getOwnerUid() == null || !task.getOwnerUid().equals(ownerUid)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
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
    public void savePoolRecordAsync(EfPlayerPoolRecordTask task) {
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

            // 角色档案与归属统一写入 ef_account_binding；仅带归属的任务写绑定，
            // 匿名接口下线前遗留的未完成任务仍可完成，但不产生归属
            if (task.getOwnerUid() != null) {
                accountBindingMapper.upsertPoolRecordBinding(roleId, task.getOwnerUid(),
                        userInfo.getUid(), userInfo.getNickName());
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
     * 调用内置 token 换取工具类，把 hgToken 换成 u8Token 与角色信息
     *
     * @param task 任务记录，token 字段为 hgToken
     * @return 玩家角色信息
     */
    private EfEndfieldUserInfoDTO getPlayerInfo(EfPlayerPoolRecordTask task) {
        EfEndfieldUserInfoDTO userInfo = EfTokenExchangeUtil.exchange(task.getToken());
        if (userInfo == null) {
            throw new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "换取终末地角色信息失败");
        }
        return userInfo;
    }

    /**
     * 把创建时间在「当前时间-24 小时」到「当前时间-15 分钟」之间的任务 token 覆盖为占位值，
     * 避免 hgToken 长期留存导致数据泄露
     *
     * <p>限定时间区间只扫描最近 24 小时内的任务，避免全表更新带来的性能开销；
     * 已被覆盖的记录不会重复写入。触发节奏由
     * {@link com.orange.service.scheduler.EfPoolRecordTaskScheduler} 控制（每分钟一次）。</p>
     */
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
     * 取一条最早创建且未开始的任务执行导入
     *
     * <p>超过有效窗口仍未开始的任务直接标记为已开始、不再执行，避免堆积旧任务。
     * 触发节奏由 {@link com.orange.service.scheduler.EfPoolRecordTaskScheduler} 控制（每秒一次）。</p>
     */
    public void loadingPoolRecord() {
        EfPlayerPoolRecordTask task = null;
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
