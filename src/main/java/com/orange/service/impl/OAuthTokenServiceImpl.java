package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.OAuthUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RequestUtil;
import com.orange.entity.dto.SessionInfo;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.oauth.ConsentInfoVO;
import com.orange.entity.vo.oauth.LoginTicketVO;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.UserInfoVO;
import com.orange.mapper.OAuthClientMapper;
import com.orange.mapper.UserInfoMapper;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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

    /** uid 反向索引中 access_token 成员前缀 */
    private static final String OAUTH_ACCESS_MEMBER_PREFIX = "access:";

    /** uid 反向索引中 refresh_token 成员前缀 */
    private static final String OAUTH_REFRESH_MEMBER_PREFIX = "refresh:";

    /** 已知 scope 的中文描述映射（未知 scope 直接展示原始标识） */
    private static final Map<String, String> SCOPE_DESCRIPTIONS = Map.of(
            "user.read", "查看你的账号基础资料（昵称、头像）",
            "user.email", "读取你的绑定邮箱",
            "user.profile", "查看并修改你的个人资料");

    private final OAuthClientMapper oauthClientMapper;
    private final UserInfoMapper userInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder;

    /** access_token 默认有效期（秒） */
    @Value("${user-center.oauth.access-token-ttl-seconds:7200}")
    private long accessTokenTtlSeconds;

    /** refresh_token 默认有效期（秒） */
    @Value("${user-center.oauth.refresh-token-ttl-seconds:7776000}")
    private long refreshTokenTtlSeconds;

    /** 授权码默认有效期（秒） */
    @Value("${user-center.oauth.authorization-code-ttl-seconds:300}")
    private long authorizationCodeTtlSeconds;

    /** 登录页地址：未登录时 302 跳转（纯前端接入场景，为空则保持抛 80001） */
    @Value("${user-center.oauth.login-page-url:}")
    private String loginPageUrl;

    /** 跨站登录票据有效期（秒）：默认 5 分钟，一次性使用 */
    @Value("${user-center.oauth.login-ticket-ttl-seconds:300}")
    private long loginTicketTtlSeconds;

    /** 授权确认页地址：requireAuthConsent=1 的客户端授权时 302 跳转（纯前端页面接入） */
    @Value("${user-center.oauth.consent-page-url:}")
    private String consentPageUrl;

    /** 授权确认单有效期（秒）：默认 5 分钟 */
    @Value("${user-center.oauth.consent-ttl-seconds:300}")
    private long consentTtlSeconds;

    /**
     * 构造器注入依赖
     *
     * @param oauthClientMapper   OAuth 客户端 Mapper
     * @param userInfoMapper      用户表 Mapper（userinfo 组装用户资料）
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     */
    public OAuthTokenServiceImpl(OAuthClientMapper oauthClientMapper, UserInfoMapper userInfoMapper,
                                 StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.oauthClientMapper = oauthClientMapper;
        this.userInfoMapper = userInfoMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * 解析当前登录用户：优先跨站登录票据（uc_ticket），其次请求头携带的本系统会话 token
     *
     * <p>静默解析：未登录或会话无效时返回 null（不抛异常），由调用方决定跳登录页或报错。</p>
     *
     * @param request HTTP 请求
     * @return 用户 uid，未登录返回 null
     */
    private Long resolveLoginUid(HttpServletRequest request) {
        // 1. 优先解析跨站登录票据（短时一次性，随 authorize 的 query/form 参数携带）
        Long uid = resolveUidByTicket(request.getParameter("uc_ticket"));
        if (uid != null) {
            return uid;
        }
        // 2. 其次解析请求头携带的本系统会话 token（Authorization: Bearer / UC-Token）
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

    /**
     * 解析一次性跨站登录票据对应的用户 uid（消费即失效，防重放）
     *
     * <p>校验顺序：先用 setIfAbsent 抢占"已使用"标记（并发/重放仅一次成功），
     * 再读取票据内 uid，读取后删除票据数据。</p>
     *
     * @param ticket 登录票据（可为空）
     * @return 用户 uid，票据无效/已使用/为空时返回 null
     */
    private Long resolveUidByTicket(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        // 一次性占用标记：只有第一个请求能拿到 true，后续重放直接拒绝
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyUtil.oauthTicketUsed(ticket), "1", loginTicketTtlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            return null;
        }
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthTicket(ticket));
        if (record == null) {
            return null;
        }
        // 消费后删除票据数据，配合 used 标记实现双保险
        stringRedisTemplate.delete(RedisKeyUtil.oauthTicket(ticket));
        Object uid = record.get("uid");
        return (uid instanceof Number) ? ((Number) uid).longValue() : null;
    }

    /**
     * 签发跨站登录票据：登录页登录成功后携带本系统会话 token 调用，换取短时一次性凭证
     *
     * @param request HTTP 请求（Authorization / UC-Token 携带会话 token）
     * @return 一次性登录票据
     */
    @Override
    public LoginTicketVO createLoginTicket(HttpServletRequest request) {
        Long uid = resolveLoginUid(request);
        if (uid == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
        String ticket = OAuthUtil.generateToken();
        Map<String, Object> record = new HashMap<>();
        record.put("uid", uid);
        writeJson(RedisKeyUtil.oauthTicket(ticket), record, loginTicketTtlSeconds);
        LoginTicketVO vo = new LoginTicketVO();
        vo.setTicket(ticket);
        vo.setExpiresIn(loginTicketTtlSeconds);
        return vo;
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
            // 未登录：302 到 UC 登录页，登录成功后携带 uc_ticket 回跳当前 authorize 地址（走跨站登录票据）
            // 回跳地址拼入请求全部参数（GET query 与 POST form 统一处理），避免登录后丢失参数
            Map<String, String[]> params = new TreeMap<>(request.getParameterMap());
            StringBuilder back = new StringBuilder(request.getRequestURL().toString()).append('?');
            for (Map.Entry<String, String[]> entry : params.entrySet()) {
                for (String value : entry.getValue()) {
                    back.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');
                }
            }
            back.setLength(back.length() - 1);
            return loginPageUrl + (loginPageUrl.contains("?") ? "&" : "?")
                    + "redirect=" + URLEncoder.encode(back.toString(), StandardCharsets.UTF_8);
        }
        // 3. requireAuthConsent=1 的客户端：先生成一次性确认单，由确认页同意后再签发授权码
        OAuthClient client = requireEnabledClient(clientId);
        if (isConsentRequired(client)) {
            if (!StringUtils.hasText(consentPageUrl)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "客户端要求授权确认但未配置确认页地址 consent-page-url");
            }
            return buildConsentRedirectUrl(clientId, redirectUri, scope, state,
                    codeChallenge, codeChallengeMethod, uid);
        }
        // 4. 签发一次性授权码（内部完成 client/redirect_uri/scope/PKCE 校验）
        String code = createAuthorizationCode(clientId, redirectUri, scope, codeChallenge, codeChallengeMethod, uid);
        // 5. 拼装 302 跳转地址，附带 code 与 state
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
        validatePkce(client, codeChallenge, codeChallengeMethod);
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
    public String buildConsentRedirectUrl(String clientId, String redirectUri, String scope,
                                          String state, String codeChallenge, String codeChallengeMethod, Long uid) {
        // 1. 前置校验客户端状态
        OAuthClient client = requireEnabledClient(clientId);
        // 2. 预校验回调地址/scope/PKCE，无效请求直接拒绝，不进入确认页
        checkRedirectUri(client, redirectUri);
        String finalScope = normalizeScope(client, scope);
        validatePkce(client, codeChallenge, codeChallengeMethod);
        // 3. 生成一次性确认单 ID，把待确认参数存入 Redis（TTL=确认单有效期）
        String pendingId = OAuthUtil.generateToken();
        Map<String, Object> record = new HashMap<>();
        record.put("uid", uid);
        record.put("clientId", client.getId());
        record.put("redirectUri", redirectUri);
        record.put("scope", finalScope);
        record.put("state", state == null ? "" : state);
        record.put("codeChallenge", codeChallenge == null ? "" : codeChallenge);
        record.put("codeChallengeMethod", codeChallengeMethod == null ? "" : codeChallengeMethod);
        writeJson(RedisKeyUtil.oauthConsent(pendingId), record, consentTtlSeconds);
        // 4. 跳转确认页并携带确认单 ID
        return consentPageUrl + (consentPageUrl.contains("?") ? "&" : "?") + "pending_id=" + pendingId;
    }

    @Override
    public ConsentInfoVO getConsentInfo(String pendingId, HttpServletRequest request) {
        // 1. 确认单必须存在，且确认人必须是发起授权的用户本人
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthConsent(pendingId));
        if (record == null) {
            throw new BusinessException(ResultCode.OAUTH_CONSENT_INVALID);
        }
        Long uid = resolveLoginUid(request);
        if (uid == null || !uid.equals(((Number) record.get("uid")).longValue())) {
            throw new BusinessException(ResultCode.NOT_LOGIN, "请以发起授权的账号确认");
        }
        // 2. 组装确认页展示信息（客户端名称 + 权限中文描述）
        OAuthClient client = oauthClientMapper.selectById((String) record.get("clientId"));
        if (client == null) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        ConsentInfoVO vo = new ConsentInfoVO();
        vo.setUid(uid);
        vo.setClientId(client.getId());
        vo.setClientName(client.getClientName());
        vo.setRedirectUri((String) record.get("redirectUri"));
        List<ConsentInfoVO.ScopeItem> items = new ArrayList<>();
        for (String scope : ((String) record.get("scope")).split(",")) {
            items.add(new ConsentInfoVO.ScopeItem(scope.trim(), describeScope(scope.trim())));
        }
        vo.setScopes(items);
        return vo;
    }

    @Override
    public String confirmAuthorization(String pendingId, boolean approve, HttpServletRequest request) {
        // 1. 一次性占用确认单：并发/重复提交只有第一次能进入
        Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(
                RedisKeyUtil.oauthConsentUsed(pendingId), "1", consentTtlSeconds, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(ResultCode.OAUTH_CONSENT_INVALID);
        }
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthConsent(pendingId));
        if (record == null) {
            throw new BusinessException(ResultCode.OAUTH_CONSENT_INVALID);
        }
        // 2. 校验确认人必须是发起授权的用户本人
        Long uid = resolveLoginUid(request);
        if (uid == null || !uid.equals(((Number) record.get("uid")).longValue())) {
            throw new BusinessException(ResultCode.NOT_LOGIN, "请以发起授权的账号确认");
        }
        // 3. 消费确认单（配合 used 标记双保险防重放）
        stringRedisTemplate.delete(RedisKeyUtil.oauthConsent(pendingId));
        String redirectUri = (String) record.get("redirectUri");
        String state = (String) record.get("state");
        StringBuilder target = new StringBuilder(redirectUri)
                .append(redirectUri.contains("?") ? "&" : "?");
        if (approve) {
            // 4a. 同意：签发一次性授权码并回跳（内部再次校验 client/redirect_uri/scope/PKCE）
            String code = createAuthorizationCode((String) record.get("clientId"), redirectUri,
                    (String) record.get("scope"), (String) record.get("codeChallenge"),
                    (String) record.get("codeChallengeMethod"), uid);
            target.append("code=").append(code);
        } else {
            // 4b. 拒绝：按 OAuth 规范回跳 error=access_denied
            target.append("error=access_denied");
        }
        if (state != null && !state.isBlank()) {
            target.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8));
        }
        return target.toString();
    }

    /**
     * 判断客户端是否需要展示授权确认页
     *
     * @param client 客户端实体
     * @return 是否展示确认页
     */
    private boolean isConsentRequired(OAuthClient client) {
        return client.getRequireAuthConsent() != null && client.getRequireAuthConsent() == 1;
    }

    /**
     * scope 标识转中文描述（未知 scope 返回原始标识，避免确认页空白）
     *
     * @param scope 权限标识
     * @return 中文描述
     */
    private String describeScope(String scope) {
        return SCOPE_DESCRIPTIONS.getOrDefault(scope, scope);
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

    /**
     * 令牌签发统一入口：按授权类型分发到授权码兑换/令牌刷新，不支持的类型直接拒绝
     *
     * @param grantType    授权类型：authorization_code / refresh_token
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端传空）
     * @param code         授权码（authorization_code 时必填）
     * @param redirectUri  回调地址（authorization_code 时必填）
     * @param codeVerifier PKCE code_verifier
     * @param refreshToken 刷新令牌（refresh_token 时必填）
     * @return 令牌响应
     */
    @Override
    public OAuthTokenVO issueToken(String grantType, String clientId, String clientSecret,
                                   String code, String redirectUri, String codeVerifier, String refreshToken) {
        // 1. 授权码兑换：校验授权码/回调地址/客户端认证/PKCE 后签发令牌
        if ("authorization_code".equals(grantType)) {
            LogUtil.debug(OAuthTokenServiceImpl.class, "[OAuth] 授权码换令牌: clientId={}, redirectUri={}, codeVerifier={}",
                    clientId, redirectUri, StringUtils.hasText(codeVerifier) ? "yes" : "no");
            return exchangeToken(clientId, clientSecret, code, redirectUri, codeVerifier);
        }
        // 2. 刷新令牌：一次性轮换签发新令牌对
        if ("refresh_token".equals(grantType)) {
            LogUtil.debug(OAuthTokenServiceImpl.class, "[OAuth] 刷新令牌: clientId={}", clientId);
            return refreshToken(clientId, clientSecret, refreshToken);
        }
        // 3. 其他授权类型一律拒绝
        LogUtil.warn(OAuthTokenServiceImpl.class, "[OAuth] 不支持的 grant_type: {}", grantType);
        throw new BusinessException(ResultCode.OAUTH_GRANT_INVALID);
    }

    @Override
    public void revokeToken(String clientId, String clientSecret, String token) {
        // 1. 参数必填校验
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID, "缺少 token");
        }
        // 2. 客户端认证：吊销的必须是客户端自己名下的令牌
        OAuthClient client = requireEnabledClient(clientId);
        authenticateClient(client, clientSecret);
        // 3. 自动识别令牌类型：先按 access 吊销，查不到再按 refresh 吊销
        boolean revoked = revokeAccessToken(token) || revokeRefreshToken(token);
        // 4. 幂等：令牌不存在/已失效同样视为成功，不对外区分，避免泄露令牌是否有效
        LogUtil.debug(OAuthTokenServiceImpl.class, "[OAuth] 吊销令牌完成: clientId={}, revoked={}", clientId, revoked);
    }

    /**
     * 吊销单个 access_token（存在则删除）
     *
     * @param token access_token
     * @return 是否实际吊销（存在且已删除）
     */
    private boolean revokeAccessToken(String token) {
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthAccess(token));
        if (record == null) {
            return false;
        }
        stringRedisTemplate.delete(RedisKeyUtil.oauthAccess(token));
        Object uidObj = record.get("uid");
        if (uidObj != null) {
            stringRedisTemplate.opsForSet().remove(RedisKeyUtil.uidOauth(((Number) uidObj).longValue()),
                    OAUTH_ACCESS_MEMBER_PREFIX + token);
        }
        return true;
    }

    /**
     * 吊销 refresh_token，并连带吊销它派生出的 access_token
     * （access_token 本身无法作废，refresh 被吊销后其先前换出的 access 仍可能有效，需一并删除）
     *
     * @param token refresh_token
     * @return 是否实际吊销（存在且已删除）
     */
    private boolean revokeRefreshToken(String token) {
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthRefresh(token));
        if (record == null) {
            return false;
        }
        Object uidObj = record.get("uid");
        Long uid = uidObj == null ? null : ((Number) uidObj).longValue();
        String accessToken = (String) record.get("accessToken");
        if (StringUtils.hasText(accessToken)) {
            stringRedisTemplate.delete(RedisKeyUtil.oauthAccess(accessToken));
            if (uid != null) {
                stringRedisTemplate.opsForSet().remove(RedisKeyUtil.uidOauth(uid), OAUTH_ACCESS_MEMBER_PREFIX + accessToken);
            }
        }
        stringRedisTemplate.delete(RedisKeyUtil.oauthRefresh(token));
        if (uid != null) {
            stringRedisTemplate.opsForSet().remove(RedisKeyUtil.uidOauth(uid), OAUTH_REFRESH_MEMBER_PREFIX + token);
        }
        return true;
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
     * 查询 OAuth 用户信息：查库补齐用户基础资料，按 scope 组装响应
     * （邮箱仅授权 user.email 时返回明文，否则不下发）
     *
     * @param uid      用户 uid
     * @param clientId 签发令牌的客户端 ID
     * @param scope    授权范围
     * @return 用户信息 VO
     */
    @Override
    public UserInfoVO getUserInfo(Long uid, String clientId, String scope) {
        UserInfo user = userInfoMapper.selectById(uid);
        LogUtil.debug(OAuthTokenServiceImpl.class, "[OAuth] 用户信息: uid={}, clientId={}, scope={}", uid, clientId, scope);
        return UserInfoVO.of(new OAuthTokenPrincipal(uid, clientId, scope), user);
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

        // 维护 uid -> OAuth 令牌反向索引（带类型前缀，便于按 uid 批量吊销）
        stringRedisTemplate.opsForSet().add(RedisKeyUtil.uidOauth(uid),
                OAUTH_ACCESS_MEMBER_PREFIX + accessToken,
                OAUTH_REFRESH_MEMBER_PREFIX + refreshToken);

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
     * PKCE 预校验：携带 challenge 时必须为 S256；客户端强制 PKCE 或公共客户端（无 secret）必须携带 challenge
     *
     * @param client              客户端实体
     * @param codeChallenge       PKCE code_challenge（可空）
     * @param codeChallengeMethod PKCE 算法
     */
    private void validatePkce(OAuthClient client, String codeChallenge, String codeChallengeMethod) {
        if (StringUtils.hasText(codeChallenge)) {
            if (!CODE_CHALLENGE_METHOD_S256.equalsIgnoreCase(codeChallengeMethod)) {
                throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID, "code_challenge_method 仅支持 S256");
            }
        } else if (isPkceRequired(client) || !StringUtils.hasText(client.getClientSecret())) {
            throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID, "该客户端必须使用 PKCE");
        }
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
