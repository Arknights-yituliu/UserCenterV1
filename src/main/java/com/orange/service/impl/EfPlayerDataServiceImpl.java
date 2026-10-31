package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.RateLimitedException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.efplayer.EfPlayerDataSaveRequest;
import com.orange.entity.po.EfAccountBinding;
import com.orange.entity.po.EfPlayerData;
import com.orange.entity.vo.efplayer.EfAccountVO;
import com.orange.entity.vo.efplayer.EfPlayerDataSaveResultVO;
import com.orange.entity.vo.efplayer.EfPlayerDataVO;
import com.orange.mapper.EfAccountBindingMapper;
import com.orange.mapper.EfPlayerDataMapper;
import com.orange.service.EfPlayerDataService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 终末地玩家数据服务实现
 *
 * <p>处理顺序：校验角色标识与 JSON → 占用该角色的上传限流名额 → 开启写事务，
 * 事务内先建立/刷新绑定，再按唯一键覆盖写入 JSON 文本。玩家数据整份保存，不做字段拆分。</p>
 *
 * @author UserCenter
 */
@Service
public class EfPlayerDataServiceImpl implements EfPlayerDataService {

    /** 角色 UID 合法格式：不超过 32 位可见 ASCII 字符（与列定义 VARCHAR(32) utf8mb4_bin 一致） */
    private static final Pattern ROLE_ID_PATTERN = Pattern.compile("^[\\x21-\\x7E]{1,32}$");

    /** 服务器 ID 合法格式：0-16 位可见 ASCII 字符，允许省略 */
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("^[\\x21-\\x7E]{0,16}$");

    /** 上传限流业务标识，构成 Redis key 的中间段 */
    private static final String RATE_LIMIT_BIZ = "ef-player-data-save";

    /** 同一角色上传限流窗口（秒）：窗口内最多接受 1 个保存请求，全部绑定该角色的用户共享名额 */
    private static final long SAVE_RATE_LIMIT_WINDOW_SECONDS = 2L;

    /** 玩家数据 JSON 文本大小上限（字节），防止单条数据过大占用存储 */
    private static final long MAX_PLAYER_DATA_BYTES = 2L * 1024 * 1024;

    private final EfAccountBindingMapper bindingMapper;
    private final EfPlayerDataMapper playerDataMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造器注入依赖
     *
     * @param bindingMapper       绑定关系 Mapper
     * @param playerDataMapper    玩家数据 Mapper
     * @param objectMapper        JSON 序列化器
     * @param stringRedisTemplate Redis 客户端，用于跨实例的原子限流
     */
    public EfPlayerDataServiceImpl(EfAccountBindingMapper bindingMapper,
                                   EfPlayerDataMapper playerDataMapper,
                                   ObjectMapper objectMapper,
                                   StringRedisTemplate stringRedisTemplate) {
        this.bindingMapper = bindingMapper;
        this.playerDataMapper = playerDataMapper;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 查询当前用户已绑定的终末地角色列表，按最近导入时间倒序
     *
     * @param uid 用户中心 UID
     * @return 已绑定角色列表（不含玩家数据正文），无绑定关系时返回空列表
     */
    @Override
    public List<EfAccountVO> listBoundAccounts(Long uid) {
        List<EfAccountBinding> bindings = bindingMapper.selectByOwnerOrderByUpdateTime(uid);
        List<EfAccountVO> accounts = new ArrayList<>(bindings.size());
        for (EfAccountBinding binding : bindings) {
            accounts.add(toAccountVO(binding));
        }
        return accounts;
    }

    /**
     * 读取指定角色的玩家数据：先校验绑定归属，再把 JSON 文本解析为对象返回
     *
     * <p>绑定关系不存在时返回业务码 80008，避免用户读取他人角色数据；
     * 绑定存在但数据行缺失（异常清理场景）时返回 null。</p>
     *
     * @param uid      用户中心 UID
     * @param roleId   终末地游戏内角色 UID
     * @param serverId 服务器 ID，可省略（按空串处理）
     * @return 玩家数据；绑定存在但数据行缺失时返回 null
     */
    @Override
    public EfPlayerDataVO getPlayerData(Long uid, String roleId, String serverId) {
        String normalizedServerId = normalizeServerId(serverId);
        validateIdentity(roleId, normalizedServerId);
        if (bindingMapper.countBinding(roleId, normalizedServerId, uid) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        EfPlayerData data = playerDataMapper.selectByRoleAndServer(roleId, normalizedServerId);
        if (data == null) {
            return null;
        }
        return new EfPlayerDataVO(roleId, normalizedServerId, parsePlayerData(data), data.getUpdateTime());
    }

    /**
     * 绑定关系转列表视图对象
     *
     * @param binding 绑定关系
     * @return 不含玩家数据正文的角色视图
     */
    private EfAccountVO toAccountVO(EfAccountBinding binding) {
        EfAccountVO account = new EfAccountVO();
        account.setRoleId(binding.getRoleId());
        account.setServerId(binding.getServerId());
        account.setCreateTime(binding.getCreateTime());
        account.setUpdateTime(binding.getUpdateTime());
        return account;
    }

    /**
     * 把落库的 JSON 文本解析为 JSON 对象
     *
     * <p>写入时已保证文本是合法 JSON 对象，此处解析失败只可能是数据被人工改动，
     * 按系统错误返回而不是静默返回空数据。</p>
     *
     * @param data 玩家数据行
     * @return 解析后的 JSON 对象
     */
    private JsonNode parsePlayerData(EfPlayerData data) {
        try {
            return objectMapper.readTree(data.getPlayerData());
        } catch (JsonProcessingException e) {
            LogUtil.error(EfPlayerDataServiceImpl.class, "终末地玩家数据解析失败, roleId={}, serverId={}",
                    data.getRoleId(), data.getServerId(), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "玩家数据解析失败");
        }
    }

    /**
     * 保存终末地玩家数据：校验通过后在一次写事务内建立绑定并覆盖写入 JSON 文本
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（角色标识 + JSON 玩家数据）
     * @return 本次写入的角色标识与保存时间
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public EfPlayerDataSaveResultVO savePlayerData(Long uid, EfPlayerDataSaveRequest request) {
        String roleId = request.getRoleId();
        String serverId = normalizeServerId(request.getServerId());
        validateIdentity(roleId, serverId);
        String playerData = serializePlayerData(request.getPlayerData());

        // 参数与数据校验通过后才占用限流名额，非法请求不挤占他人的上传名额
        acquireSaveQuota(roleId, serverId);

        LocalDateTime now = LocalDateTime.now();
        bindingMapper.upsertBinding(roleId, serverId, uid);
        playerDataMapper.upsertPlayerData(roleId, serverId, playerData, now);
        return new EfPlayerDataSaveResultVO(roleId, serverId, now);
    }

    /**
     * 归一化服务器 ID：缺省或空白按空串处理，避免唯一键出现 null
     *
     * @param serverId 原始服务器 ID
     * @return 去空白后的服务器 ID，绝不返回 null
     */
    private String normalizeServerId(String serverId) {
        if (serverId == null) {
            return "";
        }
        return serverId.trim();
    }

    /**
     * 校验角色标识格式：roleId 与 serverId 均为可见 ASCII 字符，避免不可见字符进唯一键
     *
     * @param roleId   角色 UID
     * @param serverId 服务器 ID（已归一化）
     */
    private void validateIdentity(String roleId, String serverId) {
        if (roleId == null || !ROLE_ID_PATTERN.matcher(roleId).matches()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "roleId必须为不超过32位的可见ASCII字符");
        }
        if (!SERVER_ID_PATTERN.matcher(serverId).matches()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "serverId必须为不超过16位的可见ASCII字符");
        }
    }

    /**
     * 校验并序列化玩家数据：必须是 JSON 对象，序列化后不超过大小上限
     *
     * @param playerData 客户端提交的 JSON 节点
     * @return 可直接落库的 JSON 文本
     */
    private String serializePlayerData(JsonNode playerData) {
        if (playerData == null || playerData.isNull()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "playerData不能为空");
        }
        if (!playerData.isObject()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "playerData必须是JSON对象");
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(playerData);
        } catch (JsonProcessingException e) {
            LogUtil.error(EfPlayerDataServiceImpl.class, "终末地玩家数据序列化失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "玩家数据序列化失败");
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_PLAYER_DATA_BYTES) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "玩家数据不能超过2MB");
        }
        return text;
    }

    /**
     * 占用同一角色的上传限流名额：Redis SET NX EX 原子操作，多实例共享
     *
     * <p>名额已被占用返回业务码 30006 并携带等待秒数；限流设施不可用返回业务码 40003，
     * 不默默绕过限流。</p>
     *
     * @param roleId   角色 UID
     * @param serverId 服务器 ID（已归一化）
     */
    private void acquireSaveQuota(String roleId, String serverId) {
        String key = RedisKeyUtil.rate(RATE_LIMIT_BIZ, SignUtil.sha256(roleId + ":" + serverId));
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(SAVE_RATE_LIMIT_WINDOW_SECONDS)));
        } catch (Exception e) {
            LogUtil.error(EfPlayerDataServiceImpl.class, "终末地玩家数据上传限流设施不可用, key={}", key, e);
            throw new BusinessException(ResultCode.RATE_LIMITER_UNAVAILABLE);
        }
        if (!acquired) {
            throw new RateLimitedException(ResultCode.AK_UPLOAD_RATE_LIMITED, SAVE_RATE_LIMIT_WINDOW_SECONDS);
        }
    }
}
