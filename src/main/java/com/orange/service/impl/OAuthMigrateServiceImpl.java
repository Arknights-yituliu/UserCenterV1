package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RedisRateLimiter;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.oauth.MigrateTokenRequest;
import com.orange.entity.po.AuditLog;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.oauth.MigrateTokenVO;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.mapper.AuditLogMapper;
import com.orange.mapper.OAuthClientMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.OAuthMigrateService;
import com.orange.service.OAuthTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OAuth 令牌迁移兑换实现（按需兑换 / 懒迁移）
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>认证层是唯一准入屏障：Ed25519 非对称验签，UC 侧只存公钥，配置泄漏不足以伪造</li>
 *   <li>开关校验先于认证校验：未开启时直接返回，不泄露任何配置细节</li>
 *   <li>用户状态校验在签发之前：否则该端点会成为绕过封禁的后门</li>
 *   <li>nonce 一次性占用 + 时间窗，防止请求重放</li>
 *   <li>签名原文、令牌明文一律不入日志与审计</li>
 * </ul>
 *
 * @author UserCenter
 */
@Service
public class OAuthMigrateServiceImpl implements OAuthMigrateService {

    /** 迁移兑换端点路径：纳入签名原文，防止同密钥签出的串被挪用到其他端点 */
    private static final String MIGRATE_PATH = "/oauth2/internal/migrate-token";

    /** 本期唯一支持的认证方式 */
    private static final String AUTH_MODE_ED25519 = "ed25519";

    /** 限流统计窗口（秒） */
    private static final long RATE_WINDOW_SECONDS = 60L;

    /** 审计动作标识 */
    private static final String AUDIT_ACTION = "oauth.migrate.issue";

    /** 审计操作者类型（audit_log.operator_type 仅支持 admin/user） */
    private static final String AUDIT_OPERATOR_TYPE = "user";

    private final StringRedisTemplate stringRedisTemplate;
    private final OAuthClientMapper oauthClientMapper;
    private final UserInfoMapper userInfoMapper;
    private final OAuthTokenService oauthTokenService;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /** 总开关：默认关闭，上线时显式打开，迁移结束后立即关闭 */
    @Value("${user-center.oauth.migrate.enabled:false}")
    private boolean enabled;

    /** 是否强制 HTTPS：非 HTTPS 直接拒绝，仅本地联调可临时关闭 */
    @Value("${user-center.oauth.migrate.require-https:true}")
    private boolean requireHttps;

    /** 认证方式：本期固定 ed25519 */
    @Value("${user-center.oauth.migrate.auth-mode:ed25519}")
    private String authMode;

    /** 允许的请求时间偏移（秒），同时决定 nonce 的 TTL（该值 ×2） */
    @Value("${user-center.oauth.migrate.clock-skew-seconds:120}")
    private long clockSkewSeconds;

    /** 来源 IP 白名单（逗号分隔）：留空表示不限制 */
    @Value("${user-center.oauth.migrate.ip-allowlist:}")
    private String ipAllowlist;

    /** 应用层限流：单 IP / 单 uid / 单 client 每分钟上限 */
    @Value("${user-center.oauth.migrate.per-ip-per-minute:600}")
    private long perIpPerMinute;

    @Value("${user-center.oauth.migrate.per-uid-per-minute:5}")
    private long perUidPerMinute;

    @Value("${user-center.oauth.migrate.per-client-per-minute:3000}")
    private long perClientPerMinute;

    /** kid → Ed25519 公钥（Base64 的 X.509 SubjectPublicKeyInfo）映射，格式：k1=base64,k2=base64 */
    @Value("${user-center.oauth.migrate.verify-keys:}")
    private String verifyKeys;

    /** 解析后的 kid → 公钥映射缓存（配置在运行期不变，首次使用时解析） */
    private volatile Map<String, String> publicKeyCache;

    /**
     * 构造器注入依赖
     *
     * @param stringRedisTemplate Redis 客户端（防重放与限流）
     * @param oauthClientMapper   OAuth 客户端 Mapper
     * @param userInfoMapper      用户表 Mapper（校验用户状态）
     * @param oauthTokenService   令牌签发服务（迁移签发入口）
     * @param auditLogMapper      操作审计 Mapper
     * @param objectMapper        JSON 序列化器（审计详情）
     */
    public OAuthMigrateServiceImpl(StringRedisTemplate stringRedisTemplate,
                                   OAuthClientMapper oauthClientMapper,
                                   UserInfoMapper userInfoMapper,
                                   OAuthTokenService oauthTokenService,
                                   AuditLogMapper auditLogMapper,
                                   ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.oauthClientMapper = oauthClientMapper;
        this.userInfoMapper = userInfoMapper;
        this.oauthTokenService = oauthTokenService;
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public MigrateTokenVO migrateToken(MigrateTokenRequest request) {
        long startNanos = System.nanoTime();

        // 1. 协议校验：非 HTTPS 直接拒绝（反向代理终止 TLS 时由 X-Forwarded-Proto 判定）
        if (requireHttps && !request.httpsRequest()) {
            LogUtil.warn(OAuthMigrateServiceImpl.class,
                    "[OAuth] 迁移兑换被拒绝（非 HTTPS）: clientId={}, ip={}", request.clientId(), request.requestIp());
            throw new BusinessException(ResultCode.FORBIDDEN, "迁移端点仅接受 HTTPS 访问");
        }

        // 2. 开关校验：未开启时立即返回，且先于认证校验，不泄露任何配置细节
        if (!enabled) {
            throw new BusinessException(ResultCode.OAUTH_MIGRATE_DISABLED);
        }

        // 3. 认证方式校验：本期仅实现 ed25519，配成其他值说明环境未按方案部署
        if (!AUTH_MODE_ED25519.equalsIgnoreCase(authMode)) {
            LogUtil.error(OAuthMigrateServiceImpl.class, "[OAuth] 迁移兑换认证方式不受支持: authMode={}", authMode);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "迁移兑换认证方式配置不受支持");
        }

        // 4. 来源校验：请求 IP 必须在白名单内（BackEndV3 出网 IP 已确认固定）
        if (!isIpAllowed(request.requestIp())) {
            LogUtil.warn(OAuthMigrateServiceImpl.class,
                    "[OAuth] 迁移兑换被拒绝（IP 不在白名单）: clientId={}, ip={}", request.clientId(), request.requestIp());
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 5. 参数存在性校验 + 时效校验：|now - ts| 必须落在允许窗口内
        Long uid = parseLongOrNull(request.uidText());
        Long timestamp = parseLongOrNull(request.tsText());
        if (uid == null || timestamp == null
                || !StringUtils.hasText(request.nonce()) || !StringUtils.hasText(request.kid())
                || !StringUtils.hasText(request.sig())) {
            throw new BusinessException(ResultCode.PARAM_ERROR);
        }
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (Math.abs(nowSeconds - timestamp) > clockSkewSeconds) {
            LogUtil.warn(OAuthMigrateServiceImpl.class,
                    "[OAuth] 迁移兑换被拒绝（时间戳超窗）: clientId={}, uid={}, ip={}",
                    request.clientId(), uid, request.requestIp());
            throw new BusinessException(ResultCode.OAUTH_MIGRATE_REPLAY);
        }

        // 6. 防重放：nonce 一次性占用，TTL 覆盖整个可接受时间窗
        Boolean firstUse = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyUtil.oauthMigrateNonce(request.nonce()), "1", clockSkewSeconds * 2, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(firstUse)) {
            LogUtil.warn(OAuthMigrateServiceImpl.class,
                    "[OAuth] 迁移兑换被拒绝（nonce 已被使用）: clientId={}, uid={}, ip={}",
                    request.clientId(), uid, request.requestIp());
            throw new BusinessException(ResultCode.OAUTH_MIGRATE_REPLAY);
        }

        // 7. 认证层校验：kid 定位公钥，对 canonical 串做 Ed25519 验签
        if (!verifySignature(request, uid, timestamp)) {
            LogUtil.warn(OAuthMigrateServiceImpl.class,
                    "[OAuth] 迁移兑换被拒绝（签名校验失败）: clientId={}, uid={}, ip={}, kid={}",
                    request.clientId(), uid, request.requestIp(), request.kid());
            throw new BusinessException(ResultCode.OAUTH_MIGRATE_SIGN_INVALID);
        }

        // 8. 客户端校验：存在、启用、审批通过且已开通直连认证能力（迁移复用同一开关）
        requireMigratableClient(request.clientId());

        // 9. 用户校验：必须存在且未被封禁。此步不可省，否则该端点就是绕过封禁的后门
        UserInfo user = userInfoMapper.selectById(uid);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == null || user.getStatus() < 0) {
            throw new BusinessException(ResultCode.USER_BANNED);
        }

        // 10. 限流：应用层按 IP / uid / client 三维度（网关层另有按 IP 限流）
        enforceRateLimit(request.requestIp(), uid, request.clientId());

        // 11. 签发：原子替换上一轮迁移凭证并写入新令牌对（见 OAuthTokenService#issueMigratedToken）
        OAuthTokenVO token = oauthTokenService.issueMigratedToken(request.clientId(), uid);

        // 12. 审计与日志：只记可审计字段，签名原文与令牌明文一律不入日志
        recordAudit(request, uid);
        long costMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        LogUtil.info(OAuthMigrateServiceImpl.class,
                "[OAuth] 迁移兑换成功: clientId={}, uid={}, ip={}, kid={}, origin={}, costMs={}",
                request.clientId(), uid, request.requestIp(), request.kid(), request.origin(), costMillis);

        return buildResponse(uid, token);
    }

    /**
     * 校验请求签名：kid 定位公钥后对 canonical 串做 Ed25519 验签
     *
     * @param request   请求参数
     * @param uid       已解析的 uid
     * @param timestamp 已解析的时间戳（秒）
     * @return true=验签通过；未知 kid 或验签失败均返回 false
     */
    private boolean verifySignature(MigrateTokenRequest request, Long uid, long timestamp) {
        String publicKey = resolvePublicKeys().get(request.kid());
        if (publicKey == null) {
            return false;
        }
        return SignUtil.ed25519Verify(publicKey, buildCanonical(request, uid, timestamp), request.sig());
    }

    /**
     * 构造待核验的 canonical 串（与 BackEndV3 侧签名内容逐字节一致）
     *
     * <p>纳入 HTTP 方法与路径，防止同一密钥签出的串被挪用到其他端点；纳入 kid 以支持
     * 公钥轮换。</p>
     *
     * @param request   请求参数
     * @param uid       已解析的 uid
     * @param timestamp 已解析的时间戳（秒）
     * @return canonical 串
     */
    private String buildCanonical(MigrateTokenRequest request, Long uid, long timestamp) {
        return "POST" + "\n" + MIGRATE_PATH + "\n"
                + request.kid() + "\n" + request.clientId() + "\n" + uid + "\n" + timestamp + "\n" + request.nonce();
    }

    /**
     * 解析 kid → 公钥映射（逗号分隔的 kid=base64 列表），解析结果缓存复用
     *
     * @return kid → 公钥映射（不可变）
     */
    private Map<String, String> resolvePublicKeys() {
        Map<String, String> cached = publicKeyCache;
        if (cached != null) {
            return cached;
        }
        Map<String, String> parsed = new HashMap<>();
        if (StringUtils.hasText(verifyKeys)) {
            for (String item : verifyKeys.split(",")) {
                String entry = item.trim();
                int separator = entry.indexOf('=');
                if (separator <= 0 || separator == entry.length() - 1) {
                    continue;
                }
                parsed.put(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
            }
        }
        Map<String, String> resolved = Map.copyOf(parsed);
        publicKeyCache = resolved;
        return resolved;
    }

    /**
     * 判断请求来源 IP 是否在白名单内
     *
     * @param ip 请求来源 IP
     * @return 未配置白名单时放行；已配置时要求精确匹配
     */
    private boolean isIpAllowed(String ip) {
        if (!StringUtils.hasText(ipAllowlist)) {
            return true;
        }
        for (String allowed : ipAllowlist.split(",")) {
            if (allowed.trim().equals(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 加载并校验迁移用的客户端：存在、所有者启用、管理员审批通过、已开通直连认证能力
     *
     * @param clientId 客户端 ID
     * @return 客户端实体
     */
    private OAuthClient requireMigratableClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        OAuthClient client = oauthClientMapper.selectById(clientId);
        if (client == null || client.getOwnerEnabled() == null || client.getOwnerEnabled() != 1) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        if (client.getAdminApproved() == null || client.getAdminApproved() != 1) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_BANNED);
        }
        if (client.getDirectAuthEnabled() == null || client.getDirectAuthEnabled() != 1) {
            throw new BusinessException(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED);
        }
        return client;
    }

    /**
     * 应用层限流：按 IP / uid / client 三个维度分别计数，任一维度超限即拒绝
     *
     * @param ip       请求来源 IP
     * @param uid      用户 uid
     * @param clientId 客户端 ID
     */
    private void enforceRateLimit(String ip, Long uid, String clientId) {
        String rateIp = StringUtils.hasText(ip) ? ip : "unknown";
        boolean allowed = RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("oauth-migrate-ip", rateIp), perIpPerMinute, RATE_WINDOW_SECONDS)
                && RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("oauth-migrate-uid", String.valueOf(uid)), perUidPerMinute, RATE_WINDOW_SECONDS)
                && RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("oauth-migrate-client", clientId), perClientPerMinute, RATE_WINDOW_SECONDS);
        if (!allowed) {
            throw new BusinessException(ResultCode.IP_RATE_LIMITED);
        }
    }

    /**
     * 记录一次迁移兑换审计（audit_log）
     *
     * <p>只落库 uid / client_id / origin / 旧 token 摘要 / IP / kid；签名原文与令牌明文
     * 一律不落库。审计写入失败只告警，不影响已完成的签发。</p>
     *
     * @param request 请求参数
     * @param uid     用户 uid
     */
    private void recordAudit(MigrateTokenRequest request, Long uid) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setOperatorType(AUDIT_OPERATOR_TYPE);
            auditLog.setOperatorId(uid);
            auditLog.setAction(AUDIT_ACTION);
            auditLog.setTarget(request.clientId());
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("origin", request.origin());
            detail.put("legacyTokenHash", request.legacyTokenHash());
            detail.put("ip", request.requestIp());
            detail.put("kid", request.kid());
            auditLog.setDetail(objectMapper.writeValueAsString(detail));
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            LogUtil.warn(OAuthMigrateServiceImpl.class,
                    "[OAuth] 迁移兑换审计落库失败: uid={}, clientId={}", uid, request.clientId(), e);
        }
    }

    /**
     * 组装迁移兑换响应（最小响应面：只回令牌与 scope，不回带用户资料）
     *
     * @param uid   用户 uid
     * @param token 已签发的令牌
     * @return 迁移响应
     */
    private MigrateTokenVO buildResponse(Long uid, OAuthTokenVO token) {
        MigrateTokenVO vo = new MigrateTokenVO();
        vo.setUid(uid);
        vo.setAccessToken(token.getAccessToken());
        vo.setTokenType(token.getTokenType());
        vo.setExpiresIn(token.getExpiresIn());
        vo.setRefreshToken(token.getRefreshToken());
        vo.setScope(token.getScope());
        return vo;
    }

    /**
     * 解析十进制长整型，非法时返回 null（由调用方统一按参数错误处理）
     *
     * @param text 待解析文本
     * @return 解析结果；为空或格式非法时返回 null
     */
    private static Long parseLongOrNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
