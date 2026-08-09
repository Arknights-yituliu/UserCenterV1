package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.OAuthUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RequestUtil;
import com.orange.entity.dto.SessionInfo;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.mapper.OAuthClientMapper;
import com.orange.service.OAuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 授权服务器核心实现（自研授权码模式）
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>授权码与令牌均存 Redis，删除 key 即吊销</li>
 *   <li>授权码一次性：兑换前先占用"已使用"标记，并发下只有一次能成功</li>
 *   <li>授权码绑定客户端与回调地址，防止跨客户端盗用</li>
 *   <li>PKCE S256 校验；客户端强制 PKCE 时未携带 challenge 直接拒绝</li>
 *   <li>refresh_token 一次性轮换，旧令牌立即失效</li>
 * </ul>
 *
 * @author UserCenter
 */
@Service
public class OAuthTokenServiceImpl implements OAuthTokenService {

    /** PKCE 算法常量：S256 */
    private static final String CODE_CHALLENGE_METHOD_S256 = "S256";

    private final OAuthClientMapper oauthClientMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    /** access_token 默认有效期（秒） */
    @Value("${uc.oauth.access-token-ttl-seconds:7200}")
    private long accessTokenTtlSeconds;

    /** refresh_token 默认有效期（秒） */
    @Value("${uc.oauth.refresh-token-ttl-seconds:2592000}")
    private long refreshTokenTtlSeconds;

    /** 授权码默认有效期（秒） */
    @Value("${uc.oauth.authorization-code-ttl-seconds:300}")
    private long authorizationCodeTtlSeconds;

    /** 登录页地址：未登录时 302 跳转（纯前端接入场景，为空则保持抛 80001） */
    @Value("${uc.oauth.login-page-url:}")
    private String loginPageUrl;

    /**
     * 构造器注入依赖
     *
     * @param oauthClientMapper   OAuth 客户端 Mapper
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     */
    public OAuthTokenServiceImpl(OAuthClientMapper oauthClientMapper, StringRedisTemplate stringRedisTemplate,
                                 ObjectMapper objectMapper) {
        this.oauthClientMapper = oauthClientMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 解析当前登录用户：从请求头/Cookie 取出本系统会话 token
     *
     * <p>静默解析：未登录或会话无效时返回 null（不抛异常），由调用方决定跳登录页或报错。</p>
     *
     * @param request HTTP 请求
     * @return 用户 uid，未登录返回 null
     */
    private Long resolveLoginUid(HttpServletRequest request) {
        String token = RequestUtil.resolveToken(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        String sessionJson = stringRedisTemplate.opsForValue().get(RedisKeyUtil.token(token));
        if (sessionJson == null) {
            return null;
        }
        try {
            SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
            if (session == null || session.getUid() == null) {
                return null;
            }
            return session.getUid();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String buildAuthorizeRedirectUrl(String responseType, String clientId, String redirectUri, String scope,
                                            String state, String codeChallenge, String codeChallengeMethod,
                                            HttpServletRequest request) {
        // 1. 仅支持授权码模式
        if (!"code".equals(responseType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "response_type 仅支持 code");
        }
        // 2. 解析当前登录用户（复用本系统会话 token，未登录走登录页）
        Long uid = resolveLoginUid(request);
        if (uid == null) {
            if (!StringUtils.hasText(loginPageUrl)) {
                throw new BusinessException(ResultCode.NOT_LOGIN, "请在授权前先登录用户中心");
            }
            // 未登录：302 到 UC 登录页，登录成功后回跳当前 authorize 地址（此时 Cookie 已携带）
            String back = request.getRequestURL().toString()
                    + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            return loginPageUrl + (loginPageUrl.contains("?") ? "&" : "?")
                    + "redirect=" + URLEncoder.encode(back, StandardCharsets.UTF_8);
        }
        // 3. 签发一次性授权码（内部完成 client/redirect_uri/scope/PKCE 校验）
        String code = createAuthorizationCode(clientId, redirectUri, scope, codeChallenge, codeChallengeMethod, uid);
        // 4. 拼装 302 跳转地址，附带 code 与 state
        StringBuilder target = new StringBuilder(redirectUri)
                .append(redirectUri.contains("?") ? "&" : "?")
                .append("code=").append(code);
        if (state != null && !state.isBlank()) {
            target.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        return target.toString();
    }

    @Override
    public String createAuthorizationCode(String clientId, String redirectUri, String scope,
                                          String codeChallenge, String codeChallengeMethod, Long uid) {
        // 1. 校验客户端状态
        OAuthClient client = requireEnabledClient(clientId);
        // 2. 校验回调地址白名单
        checkRedirectUri(client, redirectUri);
        // 3. 归一化并校验 scope（空则按客户端全部范围）
        String finalScope = normalizeScope(client, scope);
        // 4. PKCE 预校验：携带 challenge 时必须为 S256；客户端强制 PKCE 或公共客户端（无 secret）必须带 challenge
        if (StringUtils.hasText(codeChallenge)) {
            if (!CODE_CHALLENGE_METHOD_S256.equalsIgnoreCase(codeChallengeMethod)) {
                throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID, "code_challenge_method 仅支持 S256");
            }
        } else if (isPkceRequired(client) || !StringUtils.hasText(client.getClientSecret())) {
            throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID, "该客户端必须使用 PKCE");
        }
        // 5. 生成一次性授权码并存储
        String code = OAuthUtil.generateToken();
        Map<String, Object> record = new HashMap<>();
        record.put("clientId", clientId);
        record.put("uid", uid);
        record.put("scope", finalScope);
        record.put("redirectUri", redirectUri);
        record.put("codeChallenge", codeChallenge);
        writeJson(RedisKeyUtil.oauthCode(code), record, authorizationCodeTtlSeconds);
        return code;
    }

    @Override
    public OAuthTokenVO exchangeToken(String clientId, String clientSecret, String code,
                                      String redirectUri, String codeVerifier) {
        // 1. 一次性占用标记：并发重复兑换只有一个请求能拿到 true
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyUtil.oauthCodeUsed(code), "1", authorizationCodeTtlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(ResultCode.OAUTH_CODE_REUSED);
        }
        // 2. 读取授权码记录
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthCode(code));
        if (record == null) {
            throw new BusinessException(ResultCode.OAUTH_CODE_INVALID);
        }
        // 3. 绑定校验：授权码只能被原客户端在授权时的回调地址下兑换
        if (!clientId.equals(record.get("clientId"))) {
            throw new BusinessException(ResultCode.OAUTH_CODE_INVALID, "授权码与客户端不匹配");
        }
        if (!redirectUri.equals(record.get("redirectUri"))) {
            throw new BusinessException(ResultCode.OAUTH_CODE_INVALID, "回调地址与授权时不一致");
        }
        // 4. 客户端认证（校验密钥）
        OAuthClient client = requireEnabledClient(clientId);
        authenticateClient(client, clientSecret);
        // 5. PKCE 校验：授权时有 challenge 则必须提供匹配的 code_verifier
        String storedChallenge = (String) record.get("codeChallenge");
        if (StringUtils.hasText(storedChallenge)) {
            if (!StringUtils.hasText(codeVerifier) || !storedChallenge.equals(OAuthUtil.pkceS256(codeVerifier))) {
                throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID);
            }
        }
        // 6. 授权码用后即删（防重放）
        stringRedisTemplate.delete(RedisKeyUtil.oauthCode(code));
        // 7. 签发令牌
        Long uid = ((Number) record.get("uid")).longValue();
        String scope = (String) record.get("scope");
        return issueTokens(client, uid, scope);
    }

    @Override
    public OAuthTokenVO refreshToken(String clientId, String clientSecret, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID, "缺少 refresh_token");
        }
        // 1. 读取刷新令牌记录
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthRefresh(refreshToken));
        if (record == null) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID);
        }
        // 2. 绑定校验 + 客户端认证
        if (!clientId.equals(record.get("clientId"))) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID, "刷新令牌与客户端不匹配");
        }
        OAuthClient client = requireEnabledClient(clientId);
        authenticateClient(client, clientSecret);
        // 3. 旧刷新令牌用后即删（一次性轮换）
        stringRedisTemplate.delete(RedisKeyUtil.oauthRefresh(refreshToken));
        // 4. 签发新的令牌对
        Long uid = ((Number) record.get("uid")).longValue();
        String scope = (String) record.get("scope");
        return issueTokens(client, uid, scope);
    }

    @Override
    public OAuthTokenPrincipal resolveAccessToken(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID);
        }
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthAccess(accessToken));
        if (record == null) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID);
        }
        Long uid = ((Number) record.get("uid")).longValue();
        return new OAuthTokenPrincipal(uid, (String) record.get("clientId"), (String) record.get("scope"));
    }

    /**
     * 签发 access_token 与 refresh_token 并写入 Redis
     *
     * @param client 客户端
     * @param uid    用户 uid
     * @param scope  授权范围
     * @return 令牌响应
     */
    private OAuthTokenVO issueTokens(OAuthClient client, Long uid, String scope) {
        long accessTtl = client.getAccessTokenTtl() != null ? client.getAccessTokenTtl() : accessTokenTtlSeconds;
        long refreshTtl = client.getRefreshTokenTtl() != null ? client.getRefreshTokenTtl() : refreshTokenTtlSeconds;

        String accessToken = OAuthUtil.generateToken();
        String refreshToken = OAuthUtil.generateToken();

        // access_token 记录
        Map<String, Object> accessRecord = new HashMap<>();
        accessRecord.put("uid", uid);
        accessRecord.put("clientId", client.getId());
        accessRecord.put("scope", scope);
        writeJson(RedisKeyUtil.oauthAccess(accessToken), accessRecord, accessTtl);

        // refresh_token 记录（关联 access_token，便于后续吊销联动）
        Map<String, Object> refreshRecord = new HashMap<>();
        refreshRecord.put("uid", uid);
        refreshRecord.put("clientId", client.getId());
        refreshRecord.put("scope", scope);
        refreshRecord.put("accessToken", accessToken);
        writeJson(RedisKeyUtil.oauthRefresh(refreshToken), refreshRecord, refreshTtl);

        OAuthTokenVO vo = new OAuthTokenVO();
        vo.setAccessToken(accessToken);
        vo.setTokenType("Bearer");
        vo.setExpiresIn(accessTtl);
        vo.setRefreshToken(refreshToken);
        vo.setScope(scope);
        return vo;
    }

    /**
     * 加载客户端并校验启用状态
     *
     * @param clientId 客户端 ID
     * @return 客户端实体
     */
    private OAuthClient requireEnabledClient(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        OAuthClient client = oauthClientMapper.selectById(clientId);
        if (client == null || client.getStatus() == null || client.getStatus() != 1) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        return client;
    }

    /**
     * 校验回调地址是否在白名单内（精确匹配）
     *
     * @param client      客户端实体
     * @param redirectUri 回调地址
     */
    private void checkRedirectUri(OAuthClient client, String redirectUri) {
        if (!StringUtils.hasText(redirectUri) || !StringUtils.hasText(client.getRedirectUris())) {
            throw new BusinessException(ResultCode.OAUTH_REDIRECT_URI_INVALID);
        }
        Set<String> whitelist = new HashSet<>(Arrays.asList(client.getRedirectUris().split(",")));
        if (!whitelist.contains(redirectUri)) {
            throw new BusinessException(ResultCode.OAUTH_REDIRECT_URI_INVALID);
        }
    }

    /**
     * 归一化并校验 scope：请求的 scope 必须是客户端已授权范围的子集
     *
     * @param client 客户端实体
     * @param scope  请求的 scope（可空）
     * @return 最终授权的 scope（逗号分隔）
     */
    private String normalizeScope(OAuthClient client, String scope) {
        Set<String> allowed = new HashSet<>(Arrays.asList(client.getScopes().split(",")));
        if (!StringUtils.hasText(scope)) {
            return String.join(",", allowed);
        }
        for (String s : scope.split(",")) {
            if (!allowed.contains(s.trim())) {
                throw new BusinessException(ResultCode.OAUTH_SCOPE_INVALID, "scope 不在授权范围内: " + s.trim());
            }
        }
        return scope;
    }

    /**
     * 客户端认证：需要密钥的客户端必须提供正确密钥（BCrypt 比对）
     *
     * @param client       客户端实体
     * @param clientSecret 请求携带的密钥
     */
    private void authenticateClient(OAuthClient client, String clientSecret) {
        boolean needsSecret = StringUtils.hasText(client.getClientSecret());
        if (needsSecret && (!StringUtils.hasText(clientSecret)
                || !passwordEncoder.matches(clientSecret, client.getClientSecret()))) {
            throw new BusinessException(ResultCode.OAUTH_SECRET_INVALID);
        }
    }

    /**
     * 判断客户端是否强制 PKCE
     *
     * @param client 客户端实体
     * @return 是否强制
     */
    private boolean isPkceRequired(OAuthClient client) {
        return client.getRequirePkce() != null && client.getRequirePkce() == 1;
    }

    /**
     * 写 JSON 对象到 Redis 并设置过期时间
     *
     * @param key   Redis key
     * @param value 待序列化对象
     * @param ttl   过期秒数
     */
    private void writeJson(String key, Object value, long ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "OAuth 数据序列化失败");
        }
    }

    /**
     * 从 Redis 读取 JSON 并反序列化为 Map
     *
     * @param key Redis key
     * @return 反序列化结果，key 不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "OAuth 数据反序列化失败");
        }
    }
}
