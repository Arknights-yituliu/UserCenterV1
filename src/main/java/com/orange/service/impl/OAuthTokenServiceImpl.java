package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.OAuthClientAuthMethod;
import com.orange.common.enums.OAuthGrantType;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.OAuthUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RequestUtil;
import com.orange.entity.dto.SessionInfo;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.po.OAuthGrant;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.oauth.ConsentInfoVO;
import com.orange.entity.vo.oauth.LoginTicketVO;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.OAuthClientGrantGroupVO;
import com.orange.entity.vo.oauth.OAuthGrantItemVO;
import com.orange.entity.vo.oauth.RefreshGrantVO;
import com.orange.entity.vo.oauth.UserInfoVO;
import com.orange.mapper.OAuthClientMapper;
import com.orange.mapper.OAuthGrantMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.OAuthTokenStore;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * OAuth2 授权服务器核心实现（自研授权码模式）
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>授权码与令牌均存 Redis，删除 key 即吊销</li>
 *   <li>授权码在全部安全校验通过后由 Lua 原子消费，非法请求不能提前烧毁合法 code</li>
 *   <li>授权码绑定客户端与回调地址，防止跨客户端盗用</li>
 *   <li>PKCE S256 校验；客户端强制 PKCE 时未携带 challenge 直接拒绝</li>
 *   <li>refresh_token 为固定凭证：有效期内可反复刷新，刷新只签发新 access_token，
 *       不删除不换发；吊销 refresh_token 时级联吊销同 uid 同 client 的 access_token</li>
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

    /** 反向索引惰性清理触发概率分母（每签发 1/100 概率触发一次） */
    private static final int GRANT_INDEX_CLEANUP_PROBABILITY = 100;

    /** 反向索引惰性清理最小集合规模：成员太少时清理价值低，直接跳过 */
    private static final int GRANT_INDEX_CLEANUP_MIN_SIZE = 500;

    /** 反向索引单次惰性清理最多检查的成员数，避免一次性拉取整表造成长尾 */
    private static final int GRANT_INDEX_CLEANUP_CHECK_LIMIT = 200;

    /** 已知 scope 的中文描述映射（未知 scope 直接展示原始标识） */
    private static final Map<String, String> SCOPE_DESCRIPTIONS = Map.of(
            "user.read", "查看你的账号基础资料（昵称、头像）",
            "user.email", "读取你的绑定邮箱",
            "user.profile", "查看并修改你的个人资料");

    private final OAuthClientMapper oauthClientMapper;
    private final OAuthGrantMapper oauthGrantMapper;
    private final UserInfoMapper userInfoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthTokenStore oauthTokenStore;
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
     * @param oauthClientMapper  OAuth 客户端 Mapper
     * @param oauthGrantMapper   授权台账 Mapper
     * @param userInfoMapper     用户表 Mapper（userinfo 组装用户资料）
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper       JSON 序列化器
     * @param oauthTokenStore    OAuth 一次性凭证原子存储
     */
    public OAuthTokenServiceImpl(OAuthClientMapper oauthClientMapper, OAuthGrantMapper oauthGrantMapper,
                                 UserInfoMapper userInfoMapper,
                                 StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper,
                                 OAuthTokenStore oauthTokenStore) {
        this.oauthClientMapper = oauthClientMapper;
        this.oauthGrantMapper = oauthGrantMapper;
        this.userInfoMapper = userInfoMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.oauthTokenStore = oauthTokenStore;
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
        // 2. 在跳转登录页前先验证客户端及授权能力，避免无效/未授权客户端也能进入登录流程。
        OAuthClient client = requireEnabledClient(clientId);
        requireGrantAllowed(client, OAuthGrantType.AUTHORIZATION_CODE);
        checkRedirectUri(client, redirectUri);

        // 3. 解析当前登录用户（复用本系统会话 token，未登录走登录页）
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
        // 4. requireAuthConsent=1 的客户端：先生成一次性确认单，由确认页同意后再签发授权码
        if (isConsentRequired(client)) {
            if (!StringUtils.hasText(consentPageUrl)) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "客户端要求授权确认但未配置确认页地址 consent-page-url");
            }
            return buildConsentRedirectUrl(clientId, redirectUri, scope, state,
                    codeChallenge, codeChallengeMethod, uid);
        }
        // 5. 签发一次性授权码（内部再次完成 client/redirect_uri/scope/PKCE 校验）
        String code = createAuthorizationCode(clientId, redirectUri, scope, codeChallenge, codeChallengeMethod, uid);
        // 6. 拼装 302 跳转地址，附带 code 与 state
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
        // 2. 客户端必须显式登记 authorization_code；数据库字段不能再只是展示信息。
        requireGrantAllowed(client, OAuthGrantType.AUTHORIZATION_CODE);
        // 3. 校验回调地址白名单
        checkRedirectUri(client, redirectUri);
        // 4. 归一化并校验 scope（空则按客户端全部范围）
        String finalScope = normalizeScope(client, scope);
        // 5. PKCE 预校验：携带 challenge 时必须为 S256；公共客户端始终必须带 challenge。
        validatePkce(client, codeChallenge, codeChallengeMethod);
        // 6. 生成一次性授权码并存储
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
        // 2. 确认客户端具备授权码能力；确认单不能成为绕过 grant type 的入口。
        requireGrantAllowed(client, OAuthGrantType.AUTHORIZATION_CODE);
        // 3. 预校验回调地址/scope/PKCE，无效请求直接拒绝，不进入确认页
        checkRedirectUri(client, redirectUri);
        String finalScope = normalizeScope(client, scope);
        validatePkce(client, codeChallenge, codeChallengeMethod);
        // 4. 生成一次性确认单 ID，把待确认参数存入 Redis（TTL=确认单有效期）
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
        // 5. 跳转确认页并携带确认单 ID
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
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.OAUTH_CODE_INVALID, "缺少授权码");
        }

        // 1. 只读取、不消费授权码。客户端、回调地址、认证和 PKCE 全部通过之前，
        // 任何失败请求都不能改变 Redis 状态，否则攻击者可用错误 verifier 提前烧毁 code。
        String rawRecord = oauthTokenStore.readAuthorizationCode(code);
        if (rawRecord == null) {
            if (oauthTokenStore.isAuthorizationCodeUsed(code)) {
                throw new BusinessException(ResultCode.OAUTH_CODE_REUSED);
            }
            throw new BusinessException(ResultCode.OAUTH_CODE_INVALID);
        }
        Map<String, Object> record = readJsonMapValue(rawRecord);

        // 2. 授权码只能由原客户端、在授权时绑定的完整 redirect_uri 下兑换。
        if (!Objects.equals(clientId, record.get("clientId"))) {
            throw new BusinessException(ResultCode.OAUTH_CODE_INVALID, "授权码与客户端不匹配");
        }
        if (!Objects.equals(redirectUri, record.get("redirectUri"))) {
            throw new BusinessException(ResultCode.OAUTH_CODE_INVALID, "回调地址与授权时不一致");
        }

        // 3. 即使签发 code 时已经检查过，也必须在兑换时重新检查客户端状态与 grant。
        // 两个步骤之间客户端可能被停用，或数据库配置可能已被管理员调整。
        OAuthClient client = requireEnabledClient(clientId);
        requireGrantAllowed(client, OAuthGrantType.AUTHORIZATION_CODE);
        authenticateClient(client, clientSecret);

        // 4. PKCE 校验。公共客户端在签发阶段必然绑定 challenge；这里验证 verifier
        // 后才允许进入原子消费步骤。
        String storedChallenge = (String) record.get("codeChallenge");
        if (StringUtils.hasText(storedChallenge)) {
            if (!StringUtils.hasText(codeVerifier) || !storedChallenge.equals(OAuthUtil.pkceS256(codeVerifier))) {
                throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID);
            }
        } else if (isPkceRequired(client) || OAuthClientAuthMethod.NONE.matches(client.getAuthMethods())) {
            // 防御历史脏数据：即使旧版本曾为公共客户端签发过未绑定 challenge 的 code，
            // 新版本也不能在兑换阶段接受它。
            throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID, "授权码未绑定 PKCE challenge");
        }

        // 5. compare-and-delete 由 Lua 原子完成。并发请求可能同时完成上述只读校验，
        // 但最终只有一个请求能删除匹配记录并写入 used 标记。
        if (!oauthTokenStore.consumeAuthorizationCode(code, rawRecord, authorizationCodeTtlSeconds)) {
            throw new BusinessException(ResultCode.OAUTH_CODE_REUSED);
        }

        // 6. 授权码成功消费后签发令牌。是否附带 refresh token 由客户端登记值决定。
        Long uid = ((Number) record.get("uid")).longValue();
        String scope = (String) record.get("scope");
        return issueTokens(client, uid, scope);
    }

    @Override
    public OAuthTokenVO refreshToken(String clientId, String clientSecret, String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID, "缺少 refresh_token");
        }

        // 1. 读取原始 JSON。Lua 稍后会比较完全相同的文本，以确认业务层验证过的
        // 正是即将消费的记录，而不是并发期间被替换过的值。
        String rawRecord = oauthTokenStore.readRefreshToken(refreshToken);
        if (rawRecord == null) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID);
        }
        Map<String, Object> record = readJsonMapValue(rawRecord);

        // 2. 先校验令牌归属，再校验客户端当前状态、认证方式和 refresh_token grant。
        // 任一步失败都不会对令牌做任何变更。
        if (!Objects.equals(clientId, record.get("clientId"))) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID, "刷新令牌与客户端不匹配");
        }
        OAuthClient client = requireEnabledClient(clientId);
        requireGrantAllowed(client, OAuthGrantType.REFRESH_TOKEN);
        authenticateClient(client, clientSecret);

        // 3. 预生成新 access token 和 JSON。生成动作没有外部副作用；只有 Lua 成功后
        // 这些值才会出现在 Redis 和响应中，因此失败请求不会留下半成品令牌。
        Long uid = ((Number) record.get("uid")).longValue();
        String scope = (String) record.get("scope");
        long accessTtl = resolveAccessTokenTtl(client);
        String newAccessToken = OAuthUtil.generateToken();
        String accessJson = writeJsonValue(createAccessRecord(client, uid, scope));

        OAuthTokenStore.RefreshAccessRequest request = new OAuthTokenStore.RefreshAccessRequest(
                refreshToken, rawRecord,
                newAccessToken, accessJson, accessTtl,
                uid);

        // 4. Lua 将“校验 refresh 仍有效、写入新 access、更新反向索引”作为原子状态转换。
        // refresh_token 为固定凭证，不删除不换发；失败说明它已被吊销或失效，返回 90009。
        if (!oauthTokenStore.issueAccessFromRefresh(request)) {
            throw new BusinessException(ResultCode.OAUTH_TOKEN_INVALID, "刷新令牌已失效或已被吊销");
        }
        // 刷新只签发新 access_token：refresh_token 未变、scope 未变，均无需回传
        maybeCleanupExpiredGrantMembers(uid);
        return buildTokenResponse(newAccessToken, null, accessTtl, null);
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
        // 2. 刷新令牌：签发新 access_token（refresh_token 固定复用，不换发）
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
        // 3. 自动识别令牌类型，并把已认证的 clientId 传入底层归属检查。
        // 不属于当前客户端的 token 必须保持原样，不能因调用方知道 token 字符串就允许越权吊销。
        boolean revoked = revokeAccessToken(token, clientId) || revokeRefreshToken(token, clientId);
        // 4. 幂等：不存在、已失效或属于其他客户端都对外表现为成功，避免令牌探测。
        LogUtil.debug(OAuthTokenServiceImpl.class, "[OAuth] 吊销令牌完成: clientId={}, revoked={}", clientId, revoked);
    }

    /**
     * 吊销单个 access_token（存在则删除）
     *
     * @param token            access_token
     * @param expectedClientId 已通过认证的调用方客户端 ID
     * @return 是否实际吊销（存在且已删除）
     */
    private boolean revokeAccessToken(String token, String expectedClientId) {
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthAccess(token));
        // 必须先比较归属再执行任何删除或索引变更。false 不区分“不存在”和“不属于”，
        // 上层会按 RFC 7009 的幂等语义统一返回成功。
        if (record == null || !Objects.equals(expectedClientId, record.get("clientId"))) {
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
     * @param token            refresh_token
     * @param expectedClientId 已通过认证的调用方客户端 ID
     * @return 是否实际吊销（存在且已删除）
     */
    private boolean revokeRefreshToken(String token, String expectedClientId) {
        Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthRefresh(token));
        // 归属不匹配时连派生 access token 也不能触碰，否则可借 refresh token 字符串
        // 越权中断另一个客户端的会话。
        if (record == null || !Objects.equals(expectedClientId, record.get("clientId"))) {
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
        // 同步置台账为已吊销；Redis 已删除即吊销生效，台账更新失败只告警不阻断
        try {
            oauthGrantMapper.markRevokedByTokenHash(sha256Hex(token));
        } catch (Exception e) {
            LogUtil.warn(OAuthTokenServiceImpl.class, "[OAuth] 更新授权台账吊销状态失败: clientId={}", expectedClientId, e);
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
     * 查看当前用户名下全部仍有效的 refresh_token 授权记录（按授权时间倒序）。
     *
     * <p>直接查询授权台账表（LEFT JOIN oauth_client 补齐应用名），由数据库过滤
     * 已吊销与已过期记录；令牌的签发/吊销状态由签发与吊销流程同步到台账，允许最终一致。</p>
     *
     * @param uid 用户 uid
     * @return refresh_token 授权记录列表
     */
    @Override
    public List<OAuthClientGrantGroupVO> listUserRefreshTokens(Long uid) {
        // 扁平查询结果已按授权时间倒序：分组后每组/组内条目天然保持该顺序
        List<RefreshGrantVO> grants = oauthGrantMapper.selectValidGrants(uid);
        Map<String, OAuthClientGrantGroupVO> groups = new LinkedHashMap<>();
        for (RefreshGrantVO grant : grants) {
            OAuthClientGrantGroupVO group = groups.get(grant.getClientId());
            if (group == null) {
                group = new OAuthClientGrantGroupVO();
                group.setClientId(grant.getClientId());
                group.setClientName(grant.getClientName());
                groups.put(grant.getClientId(), group);
            }
            OAuthGrantItemVO item = new OAuthGrantItemVO();
            item.setScope(grant.getScope());
            item.setCreatedAt(grant.getCreatedAt());
            item.setExpiresInSeconds(grant.getExpiresInSeconds());
            group.getGrants().add(item);
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * 撤销指定用户对某应用（OAuth 客户端）的授权（按应用整体撤销，幂等）
     *
     * <p>先把授权台账按 uid+clientId 置为已吊销（成功后列表即不再展示该应用）；
     * 再遍历 uid 反向索引，按记录归属删除该 clientId 的全部 access/refresh token。
     * Redis 操作异常会向上抛出，可由调用方重试收敛（重试幂等，不会产生脏数据）。</p>
     *
     * @param uid      用户 uid
     * @param clientId 客户端 ID
     */
    @Override
    public void revokeClientAuthorization(Long uid, String clientId) {
        // 1. 台账先置为已吊销：列表/统计立即收敛；无记录时也幂等成功
        oauthGrantMapper.markRevokedByUidAndClient(uid, clientId);

        // 2. 清理 Redis 反向索引中属于该 clientId 的令牌
        String indexKey = RedisKeyUtil.uidOauth(uid);
        Set<String> members = stringRedisTemplate.opsForSet().members(indexKey);
        if (members == null) {
            return;
        }
        for (String member : members) {
            String tokenKey = resolveTokenKeyByMember(member);
            if (tokenKey == null) {
                continue;
            }
            String raw = stringRedisTemplate.opsForValue().get(tokenKey);
            if (raw == null) {
                // 令牌 key 已消失（过期/被吊销）：顺手摘除索引残留成员
                stringRedisTemplate.opsForSet().remove(indexKey, member);
                continue;
            }
            Map<String, Object> record = readJsonMapValue(raw);
            if (!Objects.equals(clientId, record.get("clientId"))) {
                continue;
            }
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.opsForSet().remove(indexKey, member);
        }
    }

    /**
     * 把反向索引成员（access:/refresh: 前缀）映射为对应的令牌 key
     *
     * @param member 反向索引成员
     * @return 令牌 key；无法识别的成员返回 null
     */
    private String resolveTokenKeyByMember(String member) {
        if (member.startsWith(OAUTH_ACCESS_MEMBER_PREFIX)) {
            String token = member.substring(OAUTH_ACCESS_MEMBER_PREFIX.length());
            return RedisKeyUtil.oauthAccess(token);
        }
        if (member.startsWith(OAUTH_REFRESH_MEMBER_PREFIX)) {
            String token = member.substring(OAUTH_REFRESH_MEMBER_PREFIX.length());
            return RedisKeyUtil.oauthRefresh(token);
        }
        return null;
    }

    /**
     * 记录一次 refresh_token 授权到台账表
     *
     * <p>令牌仍在 Redis 中签发；台账供“我的授权”列表与审计查询。写入失败不阻断签发，
     * 只记录告警，避免数据库故障拖垮核心令牌流程。</p>
     *
     * @param client        签发令牌的客户端
     * @param uid           用户 uid
     * @param scope         授权范围
     * @param refreshToken  refresh_token 明文（只存摘要）
     * @param refreshTtl    有效期（秒）
     */
    private void recordGrant(OAuthClient client, Long uid, String scope,
                             String refreshToken, long refreshTtl) {
        try {
            OAuthGrant grant = new OAuthGrant();
            grant.setUid(uid);
            grant.setClientId(client.getId());
            grant.setScope(scope);
            grant.setTokenHash(sha256Hex(refreshToken));
            LocalDateTime now = LocalDateTime.now();
            grant.setIssueTime(now);
            grant.setExpireTime(now.plusSeconds(refreshTtl));
            grant.setRevoked(0);
            oauthGrantMapper.insert(grant);
        } catch (Exception e) {
            LogUtil.warn(OAuthTokenServiceImpl.class, "[OAuth] 写入授权台账失败: uid={}, clientId={}",
                    uid, client.getId(), e);
        }
    }

    /**
     * 计算字符串的 SHA-256 十六进制摘要（用于台账存储 refresh_token 摘要）
     *
     * @param value 原始字符串
     * @return 64 位十六进制摘要
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                String segment = Integer.toHexString(0xFF & b);
                if (segment.length() == 1) {
                    hex.append('0');
                }
                hex.append(segment);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "令牌摘要计算失败");
        }
    }

    /**
     * 签发客户端获准获得的令牌并写入 Redis。
     *
     * <p>access token 始终签发；只有客户端登记了 {@code refresh_token} grant 时才创建
     * refresh token、Redis 记录、反向索引成员，并同步写入授权台账表。
     * 这样 grant_types 不再只是展示字段。</p>
     *
     * @param client 客户端
     * @param uid    用户 uid
     * @param scope  授权范围
     * @return 令牌响应
     */
    private OAuthTokenVO issueTokens(OAuthClient client, Long uid, String scope) {
        long accessTtl = resolveAccessTokenTtl(client);

        String accessToken = OAuthUtil.generateToken();
        writeJson(RedisKeyUtil.oauthAccess(accessToken), createAccessRecord(client, uid, scope), accessTtl);
        stringRedisTemplate.opsForSet().add(
                RedisKeyUtil.uidOauth(uid), OAUTH_ACCESS_MEMBER_PREFIX + accessToken);

        String refreshToken = null;
        if (isGrantAllowed(client, OAuthGrantType.REFRESH_TOKEN)) {
            long refreshTtl = resolveRefreshTokenTtl(client);
            refreshToken = OAuthUtil.generateToken();
            writeJson(RedisKeyUtil.oauthRefresh(refreshToken),
                    createRefreshRecord(client, uid, scope), refreshTtl);
            stringRedisTemplate.opsForSet().add(
                    RedisKeyUtil.uidOauth(uid), OAUTH_REFRESH_MEMBER_PREFIX + refreshToken);
            // 授权台账：持久化查询与审计，失败不阻断签发
            recordGrant(client, uid, scope, refreshToken, refreshTtl);
        }

        maybeCleanupExpiredGrantMembers(uid);
        return buildTokenResponse(accessToken, refreshToken, accessTtl, scope);
    }

    /**
     * 概率性惰性清理 uid 反向索引中的过期令牌成员
     *
     * <p>令牌 key 各自带 TTL 自动过期，但反向索引 Set 永不过期、Redis 不会摘除死成员；
     * 逐条吊销只能清理当次，整体吊销前集合会随每次刷新持续膨胀。因此每次签发
     * access_token 时以低概率触发一次清理：只检查有意义的规模（集合较大），且单次
     * 检查数量受限，把偶发开销控制在可接受范围。清理失败只告警，不阻断签发。</p>
     *
     * @param uid 用户 uid
     */
    private void maybeCleanupExpiredGrantMembers(Long uid) {
        if (ThreadLocalRandom.current().nextInt(GRANT_INDEX_CLEANUP_PROBABILITY) != 0) {
            return;
        }
        String indexKey = RedisKeyUtil.uidOauth(uid);
        try {
            Set<String> members = stringRedisTemplate.opsForSet().members(indexKey);
            if (members == null || members.size() < GRANT_INDEX_CLEANUP_MIN_SIZE) {
                return;
            }
            int checked = 0;
            for (String member : members) {
                if (checked >= GRANT_INDEX_CLEANUP_CHECK_LIMIT) {
                    break;
                }
                checked++;
                String tokenKey = null;
                if (member.startsWith(OAUTH_ACCESS_MEMBER_PREFIX)) {
                    String token = member.substring(OAUTH_ACCESS_MEMBER_PREFIX.length());
                    tokenKey = RedisKeyUtil.oauthAccess(token);
                } else if (member.startsWith(OAUTH_REFRESH_MEMBER_PREFIX)) {
                    String token = member.substring(OAUTH_REFRESH_MEMBER_PREFIX.length());
                    tokenKey = RedisKeyUtil.oauthRefresh(token);
                }
                // 令牌 key 已消失（过期/被吊销）则摘除残留成员；删除幂等
                if (tokenKey != null && !Boolean.TRUE.equals(stringRedisTemplate.hasKey(tokenKey))) {
                    stringRedisTemplate.opsForSet().remove(indexKey, member);
                }
            }
        } catch (Exception e) {
            LogUtil.warn(OAuthTokenServiceImpl.class, "[OAuth] 反向索引惰性清理失败: uid={}", uid, e);
        }
    }

    /** 创建持久化到 Redis 的 access token 记录。 */
    private Map<String, Object> createAccessRecord(OAuthClient client, Long uid, String scope) {
        Map<String, Object> record = new HashMap<>();
        record.put("uid", uid);
        record.put("clientId", client.getId());
        record.put("scope", scope);
        return record;
    }

    /** 创建 refresh token 记录（固定凭证，不绑定派生 access；吊销时按 uid+clientId 级联）。 */
    private Map<String, Object> createRefreshRecord(OAuthClient client, Long uid, String scope) {
        Map<String, Object> record = new HashMap<>();
        record.put("uid", uid);
        record.put("clientId", client.getId());
        record.put("scope", scope);
        return record;
    }

    /** 读取客户端覆盖值或全局默认 access token TTL。 */
    private long resolveAccessTokenTtl(OAuthClient client) {
        return client.getAccessTokenTtl() != null ? client.getAccessTokenTtl() : accessTokenTtlSeconds;
    }

    /** 读取客户端覆盖值或全局默认 refresh token TTL。 */
    private long resolveRefreshTokenTtl(OAuthClient client) {
        return client.getRefreshTokenTtl() != null ? client.getRefreshTokenTtl() : refreshTokenTtlSeconds;
    }

    /** 组装 OAuth 标准字段响应；refreshToken/scope 传 null 时序列化省略（刷新场景仅返回新 access_token） */
    private OAuthTokenVO buildTokenResponse(String accessToken, String refreshToken, long accessTtl, String scope) {
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
        if (client == null || client.getOwnerEnabled() == null || client.getOwnerEnabled() != 1) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        if (client.getAdminApproved() == null || client.getAdminApproved() != 1) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_BANNED);
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
     * 强制要求客户端登记指定授权类型。
     *
     * <p>数据库仍使用英文逗号保存 grantTypes。解析时逐项严格校验白名单；若发现空项
     * 或未知值，说明存量配置发生漂移，此时拒绝服务而不是猜测管理员意图。</p>
     *
     * @param client    客户端实体
     * @param grantType 当前流程要求的授权类型
     */
    private void requireGrantAllowed(OAuthClient client, OAuthGrantType grantType) {
        if (!isGrantAllowed(client, grantType)) {
            throw new BusinessException(ResultCode.OAUTH_GRANT_INVALID,
                    "客户端未登记授权类型: " + grantType.getValue());
        }
    }

    /**
     * 判断客户端是否登记授权类型，同时验证整段数据库配置没有未知值。
     *
     * @param client    客户端实体
     * @param grantType 要查询的授权类型
     * @return 是否登记
     */
    private boolean isGrantAllowed(OAuthClient client, OAuthGrantType grantType) {
        if (!StringUtils.hasText(client.getGrantTypes())) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID, "客户端 grant_types 配置为空");
        }
        boolean allowed = false;
        for (String configured : client.getGrantTypes().split(",", -1)) {
            if (!OAuthGrantType.supports(configured)) {
                throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID,
                        "客户端存在不受支持的 grant_types 配置");
            }
            if (grantType.matches(configured)) {
                allowed = true;
            }
        }
        return allowed;
    }

    /**
     * 严格按数据库声明的认证方式执行客户端认证。
     *
     * <p>{@code none} 客户端必须没有存量密钥；即使请求附带了 client_secret 也不把它
     * 当作认证依据。{@code client_secret_post} 客户端则必须同时具备 BCrypt 哈希和
     * 正确的表单密钥。未知声明一律视为客户端配置错误。</p>
     *
     * @param client       客户端实体
     * @param clientSecret 请求携带的密钥
     */
    private void authenticateClient(OAuthClient client, String clientSecret) {
        String authMethod = client.getAuthMethods();
        if (OAuthClientAuthMethod.NONE.matches(authMethod)) {
            if (StringUtils.hasText(client.getClientSecret())) {
                throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID, "公共客户端配置不一致");
            }
            return;
        }
        if (OAuthClientAuthMethod.CLIENT_SECRET_POST.matches(authMethod)) {
            if (!StringUtils.hasText(client.getClientSecret())
                    || !StringUtils.hasText(clientSecret)
                    || !passwordEncoder.matches(clientSecret, client.getClientSecret())) {
                throw new BusinessException(ResultCode.OAUTH_SECRET_INVALID);
            }
            return;
        }
        throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID, "客户端认证方式不受支持");
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
     * PKCE 预校验：携带 challenge 时必须为 S256；公共客户端或配置强制 PKCE 的客户端必须携带 challenge。
     *
     * <p>公共客户端的判断依据是 auth_methods 声明，不再从 client_secret 是否为空推断。
     * 这样残留密钥会在客户端认证阶段被作为配置漂移拒绝，而不会悄悄改变客户端类型。</p>
     *
     * @param client              客户端实体
     * @param codeChallenge       PKCE code_challenge（可空）
     * @param codeChallengeMethod PKCE 算法
     */
    private void validatePkce(OAuthClient client, String codeChallenge, String codeChallengeMethod) {
        if (StringUtils.hasText(codeChallenge)) {
            if (!CODE_CHALLENGE_METHOD_S256.equals(codeChallengeMethod)) {
                throw new BusinessException(ResultCode.OAUTH_PKCE_INVALID, "code_challenge_method 仅支持 S256");
            }
        } else if (isPkceRequired(client) || OAuthClientAuthMethod.NONE.matches(client.getAuthMethods())) {
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
        stringRedisTemplate.opsForValue().set(key, writeJsonValue(value), ttl, TimeUnit.SECONDS);
    }

    /**
     * 将对象序列化为 Redis 使用的 JSON 文本。刷新轮换会先生成文本再交给 Lua，
     * 保证脚本比较和写入的都是确定值。
     */
    private String writeJsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
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
    private Map<String, Object> readJsonMap(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        return readJsonMapValue(json);
    }

    /**
     * 解析已读取的 Redis JSON。调用方保留原始文本时仍使用本方法转换业务字段，
     * 避免二次读取造成校验对象与原子比较对象不一致。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMapValue(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "OAuth 数据反序列化失败");
        }
    }
}
