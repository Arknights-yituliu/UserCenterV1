package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BadRequestException;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.ConfigConflictException;
import com.orange.entity.dto.userconfig.UserConfigSaveRequest;
import com.orange.entity.po.AuditLog;
import com.orange.entity.po.UserConfig;
import com.orange.entity.po.UserConfigQuota;
import com.orange.entity.vo.UserConfigQuotaVO;
import com.orange.entity.vo.UserConfigSaveVO;
import com.orange.entity.vo.UserConfigVO;
import com.orange.mapper.AuditLogMapper;
import com.orange.mapper.UserConfigMapper;
import com.orange.mapper.UserConfigQuotaMapper;
import com.orange.service.UserConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 用户配置服务实现：保存（创建/按 id 覆盖更新）、条件保存（save-if-match 乐观锁）、
 * 查询、物理删除和配额维护。
 *
 * @author UserCenter
 */
@Service
public class UserConfigServiceImpl implements UserConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserConfigServiceImpl.class);
    private static final long DEFAULT_CONFIG_QUOTA_BYTES = 500L * 1024;
    private static final long MAX_ASSIGNABLE_CONFIG_BYTES = 10L * 1024 * 1024;
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final UserConfigMapper userConfigMapper;
    private final UserConfigQuotaMapper quotaMapper;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public UserConfigServiceImpl(UserConfigMapper userConfigMapper,
                                 UserConfigQuotaMapper quotaMapper,
                                 AuditLogMapper auditLogMapper,
                                 ObjectMapper objectMapper) {
        this.userConfigMapper = userConfigMapper;
        this.quotaMapper = quotaMapper;
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserConfigSaveVO saveConfig(Long uid, UserConfigSaveRequest request) {
        validateSaveRequest(request);
        String clientId = requireClientId();
        String configString = toConfigString(request.getConfig());
        long newBytes = configString.getBytes(StandardCharsets.UTF_8).length;
        String newHash = sha256(configString);

        if (request.getId() == null) {
            return createConfig(uid, clientId, request, configString, newHash, newBytes);
        }
        // id 非空：按 id 直接覆盖更新，忽略 expectedHash（last-write-wins，不防并发）
        return overwriteConfig(uid, clientId, request, configString, newHash, newBytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserConfigSaveVO saveConfigIfMatch(Long uid, UserConfigSaveRequest request) {
        validateIfMatchRequest(request);
        String clientId = requireClientId();
        String configString = toConfigString(request.getConfig());
        long newBytes = configString.getBytes(StandardCharsets.UTF_8).length;
        String newHash = sha256(configString);
        // save-if-match 仅承担更新（校验已保证 id 非空），防止并发覆盖
        return updateConfigIfHashMatches(uid, clientId, request, configString, newHash, newBytes);
    }

    private UserConfigSaveVO createConfig(Long uid,
                                          String clientId,
                                          UserConfigSaveRequest request,
                                          String configString,
                                          String newHash,
                                          long newBytes) {
        UserConfigQuota quota = initializeAndLockQuota(uid);
        UserConfig existing = userConfigMapper.selectByIdentity(
                uid, clientId, request.getCategory(), request.getVersion(), request.getName());
        if (existing != null) {
            throw new ConfigConflictException(existing.getContentHash());
        }

        long newUsedBytes = checkedUsage(quota, newBytes);
        UserConfig config = new UserConfig();
        config.setUid(uid);
        config.setClientId(clientId);
        config.setCategory(request.getCategory());
        config.setVersion(request.getVersion());
        config.setName(request.getName());
        config.setSource(request.getSource());
        config.setNote(request.getNote());
        config.setConfig(configString);
        config.setContentHash(newHash);
        config.setConfigBytes(newBytes);

        try {
            userConfigMapper.insert(config);
        } catch (DuplicateKeyException e) {
            UserConfig current = userConfigMapper.selectByIdentity(
                    uid, clientId, request.getCategory(), request.getVersion(), request.getName());
            throw new ConfigConflictException(current == null ? null : current.getContentHash());
        }
        updateQuotaUsage(quota, newUsedBytes);
        return new UserConfigSaveVO(config.getId(), newHash);
    }

    /**
     * 按 id 直接覆盖更新：不校验内容 hash、不做并发控制（save 接口的更新分支）
     */
    private UserConfigSaveVO overwriteConfig(Long uid,
                                             String clientId,
                                             UserConfigSaveRequest request,
                                             String configString,
                                             String newHash,
                                             long newBytes) {
        UserConfigQuota quota = lockExistingQuota(uid);
        UserConfig current = userConfigMapper.selectOwnedByIdForUpdate(request.getId(), uid, clientId);
        if (current == null) {
            throw new ConfigConflictException(null);
        }
        validateIdentity(current, request);

        long delta = newBytes - current.getConfigBytes();
        long newUsedBytes = checkedUsage(quota, delta);
        int updated = userConfigMapper.updateOwnedById(
                request.getId(), uid, clientId, request.getSource(), request.getNote(),
                configString, newHash, newBytes);
        if (updated != 1) {
            throw new ConfigConflictException(null);
        }
        updateQuotaUsage(quota, newUsedBytes);
        return new UserConfigSaveVO(request.getId(), newHash);
    }

    /**
     * 按 id + expectedHash 条件更新（save-if-match，乐观锁防并发覆盖）：
     * 仅当库中内容 hash 仍等于请求携带的 expectedHash 才落库，否则返回最新 hash 供调用方重试
     */
    private UserConfigSaveVO updateConfigIfHashMatches(Long uid,
                                                       String clientId,
                                                       UserConfigSaveRequest request,
                                                       String configString,
                                                       String newHash,
                                                       long newBytes) {
        UserConfigQuota quota = lockExistingQuota(uid);
        UserConfig current = userConfigMapper.selectOwnedByIdForUpdate(request.getId(), uid, clientId);
        if (current == null) {
            throw new ConfigConflictException(null);
        }
        validateIdentity(current, request);

        String expectedHash = request.getExpectedHash().toLowerCase(Locale.ROOT);
        if (!expectedHash.equals(current.getContentHash())) {
            throw new ConfigConflictException(current.getContentHash());
        }

        long delta = newBytes - current.getConfigBytes();
        long newUsedBytes = checkedUsage(quota, delta);
        int updated = userConfigMapper.updateIfHashMatches(
                request.getId(), uid, clientId, request.getSource(), request.getNote(),
                configString, newHash, newBytes, expectedHash);
        if (updated != 1) {
            UserConfig latest = userConfigMapper.selectOwnedById(request.getId(), uid, clientId);
            throw new ConfigConflictException(latest == null ? null : latest.getContentHash());
        }

        updateQuotaUsage(quota, newUsedBytes);
        return new UserConfigSaveVO(request.getId(), newHash);
    }

    @Override
    public List<UserConfigVO> listConfigs(Long uid, String clientId, String category, String version, String name) {
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少来源客户端标识，请重新登录");
        }
        List<UserConfig> list = userConfigMapper.selectList(Wrappers.<UserConfig>lambdaQuery()
                .eq(UserConfig::getUid, uid)
                .eq(UserConfig::getClientId, clientId)
                .eq(UserConfig::getCategory, category)
                .eq(StringUtils.hasText(version), UserConfig::getVersion, version)
                .eq(StringUtils.hasText(name), UserConfig::getName, name)
                .orderByDesc(UserConfig::getUpdateTime));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public UserConfigQuotaVO getQuota(Long uid) {
        UserConfigQuota quota = quotaMapper.selectById(uid);
        if (quota == null) {
            return new UserConfigQuotaVO(0, DEFAULT_CONFIG_QUOTA_BYTES);
        }
        return new UserConfigQuotaVO(quota.getUsedBytes(), quota.getLimitBytes());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(Long uid, Long id) {
        String clientId = requireClientId();
        UserConfig initial = userConfigMapper.selectOwnedById(id, uid, clientId);
        if (initial == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置不存在");
        }

        UserConfigQuota quota = lockExistingQuota(uid);
        UserConfig current = userConfigMapper.selectOwnedByIdForUpdate(id, uid, clientId);
        if (current == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置不存在");
        }
        long newUsedBytes = quota.getUsedBytes() - current.getConfigBytes();
        if (newUsedBytes < 0) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "用户配置配额数据异常");
        }
        if (userConfigMapper.deleteOwnedById(id, uid, clientId) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "删除配置失败");
        }
        updateQuotaUsage(quota, newUsedBytes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adjustQuota(Long uid, long newLimitBytes, Long operatorId, String reason) {
        if (newLimitBytes <= 0 || newLimitBytes > MAX_ASSIGNABLE_CONFIG_BYTES) {
            throw new BadRequestException("配置配额必须大于 0 且不超过平台上限");
        }
        UserConfigQuota quota = initializeAndLockQuota(uid);
        if (newLimitBytes < quota.getUsedBytes()) {
            throw new BadRequestException("配置配额不能低于当前已使用容量");
        }

        long oldLimitBytes = quota.getLimitBytes();
        quota.setLimitBytes(newLimitBytes);
        if (quotaMapper.updateById(quota) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "调整配置配额失败");
        }

        AuditLog auditLog = new AuditLog();
        auditLog.setOperatorType("admin");
        auditLog.setOperatorId(operatorId);
        auditLog.setAction("USER_CONFIG_QUOTA_ADJUST");
        auditLog.setTarget("user:" + uid);
        auditLog.setDetail(toAuditDetail(oldLimitBytes, newLimitBytes, reason));
        auditLogMapper.insert(auditLog);
    }

    /**
     * save 接口入参校验：id 为空=创建（不允许携带 expectedHash）；id 非空=直接覆盖更新，
     * 不要求 expectedHash（携带与否均忽略，不做并发控制）
     */
    private void validateSaveRequest(UserConfigSaveRequest request) {
        if (request.getId() == null) {
            if (request.isExpectedHashPresent() && request.getExpectedHash() != null) {
                throw new BadRequestException("创建配置时不能携带 expectedHash");
            }
            return;
        }
        // 覆盖更新：不校验 expectedHash（缺省或携带均忽略），last-write-wins
    }

    /**
     * save-if-match 接口入参校验：仅承担更新，id 必填且 expectedHash 必填为 64 位 SHA-256
     */
    private void validateIfMatchRequest(UserConfigSaveRequest request) {
        if (request.getId() == null) {
            throw new BadRequestException("save-if-match 更新必须携带 id");
        }
        if (!request.isExpectedHashPresent() || request.getExpectedHash() == null
                || !SHA256_PATTERN.matcher(request.getExpectedHash()).matches()) {
            throw new BadRequestException("save-if-match 更新必须携带 64 位 SHA-256 expectedHash");
        }
    }

    private void validateIdentity(UserConfig current, UserConfigSaveRequest request) {
        if (!Objects.equals(current.getCategory(), request.getCategory())
                || !Objects.equals(current.getVersion(), request.getVersion())
                || !Objects.equals(current.getName(), request.getName())) {
            throw new BadRequestException("更新配置时 category、version 和 name 不能修改");
        }
    }

    private UserConfigQuota initializeAndLockQuota(Long uid) {
        quotaMapper.initialize(uid, DEFAULT_CONFIG_QUOTA_BYTES);
        UserConfigQuota quota = quotaMapper.selectForUpdate(uid);
        if (quota == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "初始化用户配置配额失败");
        }
        return quota;
    }

    private UserConfigQuota lockExistingQuota(Long uid) {
        UserConfigQuota quota = quotaMapper.selectForUpdate(uid);
        if (quota == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "用户配置配额不存在");
        }
        return quota;
    }

    private long checkedUsage(UserConfigQuota quota, long delta) {
        long newUsedBytes;
        try {
            newUsedBytes = Math.addExact(quota.getUsedBytes(), delta);
        } catch (ArithmeticException e) {
            throw new BusinessException(ResultCode.CONFIG_TOO_LARGE);
        }
        if (newUsedBytes < 0) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "用户配置配额数据异常");
        }
        if (newUsedBytes > quota.getLimitBytes()) {
            throw new BusinessException(ResultCode.CONFIG_TOO_LARGE);
        }
        return newUsedBytes;
    }

    private void updateQuotaUsage(UserConfigQuota quota, long usedBytes) {
        quota.setUsedBytes(usedBytes);
        if (quotaMapper.updateById(quota) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "更新用户配置配额失败");
        }
    }

    private String requireClientId() {
        String clientId = UserContext.getClientId();
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少来源客户端标识，请重新登录");
        }
        return clientId;
    }

    private String toConfigString(Object config) {
        if (config instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.warn("配置内容序列化失败：{}", e.getMessage());
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置内容格式错误");
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持 SHA-256", e);
        }
    }

    private String toAuditDetail(long oldLimitBytes, long newLimitBytes, String reason) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("oldLimitBytes", oldLimitBytes);
        detail.put("newLimitBytes", newLimitBytes);
        detail.put("reason", reason);
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "配额审计数据序列化失败");
        }
    }

    private Object parseConfig(String configString) {
        try {
            return objectMapper.readValue(configString, Object.class);
        } catch (IOException e) {
            log.warn("配置内容解析失败，原样返回：{}", e.getMessage());
            return configString;
        }
    }

    private UserConfigVO toVO(UserConfig config) {
        UserConfigVO vo = new UserConfigVO();
        vo.setId(config.getId());
        vo.setClientId(config.getClientId());
        vo.setCategory(config.getCategory());
        vo.setVersion(config.getVersion());
        vo.setName(config.getName());
        vo.setSource(config.getSource());
        vo.setNote(config.getNote());
        vo.setConfig(parseConfig(config.getConfig()));
        vo.setHash(config.getContentHash());
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }
}
