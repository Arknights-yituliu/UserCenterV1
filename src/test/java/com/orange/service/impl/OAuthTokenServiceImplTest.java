package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.OAuthUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.RefreshGrantVO;
import com.orange.mapper.OAuthClientMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.OAuthTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth 令牌服务安全边界测试。
 *
 * <p>Redis Lua 的原子性由集成测试验证；本类验证业务层只有在客户端、grant、回调地址、
 * 密钥和 PKCE 全部通过后才调用原子存储，并验证失败分支不会误删其他客户端令牌。</p>
 */
@ExtendWith(MockitoExtension.class)
class OAuthTokenServiceImplTest {

    private static final String REDIRECT_URI = "https://spa.example.com/oauth/callback";
    private static final String VERIFIER = "a-secure-pkce-verifier-with-sufficient-entropy-1234567890";

    @Mock
    private OAuthClientMapper oauthClientMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private OAuthTokenStore oauthTokenStore;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OAuthTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        service = new OAuthTokenServiceImpl(
                oauthClientMapper, userInfoMapper, redisTemplate, objectMapper, oauthTokenStore);
        ReflectionTestUtils.setField(service, "accessTokenTtlSeconds", 7200L);
        ReflectionTestUtils.setField(service, "refreshTokenTtlSeconds", 86400L);
        ReflectionTestUtils.setField(service, "authorizationCodeTtlSeconds", 300L);
    }

    @Test
    void publicClientExchangesCodeWithoutSecret() throws Exception {
        OAuthClient client = client("none", "authorization_code,refresh_token", null, 1);
        prepareAuthorizationCode(client, "code-1", VERIFIER);

        OAuthTokenVO token = service.exchangeToken("client-1", null, "code-1", REDIRECT_URI, VERIFIER);

        assertNotNull(token.getAccessToken());
        assertNotNull(token.getRefreshToken());
        verify(oauthTokenStore).consumeAuthorizationCode("code-1", authorizationCodeJson(VERIFIER), 300L);
    }

    @Test
    void wrongPkceDoesNotConsumeAuthorizationCode() throws Exception {
        OAuthClient client = client("none", "authorization_code,refresh_token", null, 1);
        prepareAuthorizationCode(client, "code-1", VERIFIER);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exchangeToken("client-1", null, "code-1", REDIRECT_URI, "wrong-verifier"));

        assertEquals(ResultCode.OAUTH_PKCE_INVALID.getCode(), exception.getCode());
        verify(oauthTokenStore, never()).consumeAuthorizationCode(anyString(), anyString(), anyLong());
    }

    @Test
    void publicClientWithResidualSecretIsRejected() throws Exception {
        OAuthClient client = client("none", "authorization_code,refresh_token", "stale-secret", 1);
        prepareAuthorizationCode(client, "code-1", VERIFIER);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.exchangeToken("client-1", null, "code-1", REDIRECT_URI, VERIFIER));

        assertEquals(ResultCode.OAUTH_CLIENT_INVALID.getCode(), exception.getCode());
        verify(oauthTokenStore, never()).consumeAuthorizationCode(anyString(), anyString(), anyLong());
    }

    @Test
    void confidentialClientRequiresCorrectSecret() throws Exception {
        String encodedSecret = new BCryptPasswordEncoder().encode("correct-secret");
        OAuthClient client = client("client_secret_post", "authorization_code,refresh_token", encodedSecret, 0);
        prepareAuthorizationCode(client, "code-1", null);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> service.exchangeToken("client-1", null, "code-1", REDIRECT_URI, null));
        assertEquals(ResultCode.OAUTH_SECRET_INVALID.getCode(), missing.getCode());

        OAuthTokenVO token = service.exchangeToken(
                "client-1", "correct-secret", "code-1", REDIRECT_URI, null);
        assertNotNull(token.getAccessToken());
    }

    @Test
    void authorizationCodeGrantMustBeRegistered() {
        OAuthClient client = client("none", "refresh_token", null, 1);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createAuthorizationCode(
                        "client-1", REDIRECT_URI, null, OAuthUtil.pkceS256(VERIFIER), "S256", 9L));

        assertEquals(ResultCode.OAUTH_GRANT_INVALID.getCode(), exception.getCode());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void clientCannotAuthorizeBeforeAdminApproval() {
        OAuthClient client = client("none", "authorization_code", null, 1);
        client.setAdminApproved(0);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createAuthorizationCode(
                        "client-1", REDIRECT_URI, null, OAuthUtil.pkceS256(VERIFIER), "S256", 9L));

        assertEquals(ResultCode.OAUTH_CLIENT_BANNED.getCode(), exception.getCode());
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void clientWithoutRefreshGrantReceivesAccessTokenOnly() throws Exception {
        OAuthClient client = client("none", "authorization_code", null, 1);
        prepareAuthorizationCode(client, "code-1", VERIFIER);

        OAuthTokenVO token = service.exchangeToken("client-1", null, "code-1", REDIRECT_URI, VERIFIER);

        assertNotNull(token.getAccessToken());
        assertNull(token.getRefreshToken());
        verify(valueOperations).set(anyString(), anyString(), eq(7200L), eq(java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void refreshGrantMustBeRegistered() throws Exception {
        OAuthClient client = client("none", "authorization_code", null, 1);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);
        when(oauthTokenStore.readRefreshToken("refresh-1")).thenReturn(refreshTokenJson("client-1"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refreshToken("client-1", null, "refresh-1"));

        assertEquals(ResultCode.OAUTH_GRANT_INVALID.getCode(), exception.getCode());
        verify(oauthTokenStore, never()).issueAccessFromRefresh(any());
    }

    @Test
    void refreshFailsWhenRefreshTokenWasRevoked() throws Exception {
        OAuthClient client = client("none", "authorization_code,refresh_token", null, 1);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);
        when(oauthTokenStore.readRefreshToken("refresh-1")).thenReturn(refreshTokenJson("client-1"));
        when(oauthTokenStore.issueAccessFromRefresh(any())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.refreshToken("client-1", null, "refresh-1"));

        assertEquals(ResultCode.OAUTH_TOKEN_INVALID.getCode(), exception.getCode());
    }

    @Test
    void successfulRefreshIssuesAccessOnly() throws Exception {
        OAuthClient client = client("none", "authorization_code,refresh_token", null, 1);
        String oldJson = refreshTokenJson("client-1");
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);
        when(oauthTokenStore.readRefreshToken("refresh-1")).thenReturn(oldJson);
        when(oauthTokenStore.issueAccessFromRefresh(any())).thenReturn(true);

        OAuthTokenVO token = service.refreshToken("client-1", null, "refresh-1");

        assertNotNull(token.getAccessToken());
        // 固定凭证：refresh_token 不换发、scope 不变，响应均不返回这两个字段
        assertNull(token.getRefreshToken());
        assertNull(token.getScope());
        ArgumentCaptor<OAuthTokenStore.RefreshAccessRequest> captor =
                ArgumentCaptor.forClass(OAuthTokenStore.RefreshAccessRequest.class);
        verify(oauthTokenStore).issueAccessFromRefresh(captor.capture());
        OAuthTokenStore.RefreshAccessRequest request = captor.getValue();
        assertEquals("refresh-1", request.oldRefreshToken());
        assertEquals(oldJson, request.expectedOldRefreshJson());
        assertEquals(9L, request.uid());
        assertEquals(7200L, request.accessTtlSeconds());
    }

    @Test
    void cannotRevokeAnotherClientsToken() throws Exception {
        OAuthClient client = client("none", "authorization_code,refresh_token", null, 1);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);
        when(valueOperations.get(RedisKeyUtil.oauthAccess("foreign-token")))
                .thenReturn(accessTokenJson("other-client"));
        when(valueOperations.get(RedisKeyUtil.oauthRefresh("foreign-token"))).thenReturn(null);

        service.revokeToken("client-1", null, "foreign-token");

        verify(redisTemplate, never()).delete(anyString());
        verify(setOperations, never()).remove(anyString(), any());
    }

    @Test
    void listUserRefreshTokensFiltersAccessAndPrunesExpiredMembers() throws Exception {
        Long uid = 9L;
        String indexKey = RedisKeyUtil.uidOauth(uid);
        when(setOperations.members(indexKey)).thenReturn(new HashSet<>(Arrays.asList(
                "refresh:refresh-1", "access:access-1", "refresh:expired-refresh")));
        when(valueOperations.get(RedisKeyUtil.oauthRefresh("refresh-1")))
                .thenReturn(refreshTokenJson("client-1", 1700000000000L));
        when(valueOperations.get(RedisKeyUtil.oauthRefresh("expired-refresh"))).thenReturn(null);
        when(redisTemplate.getExpire(RedisKeyUtil.oauthRefresh("refresh-1"), TimeUnit.SECONDS))
                .thenReturn(3600L);

        OAuthClient client = client("none", "authorization_code,refresh_token", null, 1);
        client.setClientName("测试应用");
        when(oauthClientMapper.selectBatchIds(anyList())).thenReturn(List.of(client));

        List<RefreshGrantVO> result = service.listUserRefreshTokens(uid);

        assertEquals(1, result.size());
        RefreshGrantVO vo = result.get(0);
        assertEquals("client-1", vo.getClientId());
        assertEquals("测试应用", vo.getClientName());
        assertEquals("user.read", vo.getScope());
        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(1700000000000L), ZoneId.systemDefault()),
                vo.getCreatedAt());
        assertEquals(3600L, vo.getExpiresInSeconds());
        // access_token 成员被过滤，已过期 refresh 残留被惰性清理
        verify(setOperations).remove(indexKey, "refresh:expired-refresh");
    }

    @Test
    void listUserRefreshTokensSortedByCreateTimeDescWithLegacyLast() throws Exception {
        Long uid = 9L;
        String indexKey = RedisKeyUtil.uidOauth(uid);
        when(setOperations.members(indexKey)).thenReturn(new HashSet<>(Arrays.asList(
                "refresh:newer", "refresh:older", "refresh:legacy")));
        when(valueOperations.get(RedisKeyUtil.oauthRefresh("newer")))
                .thenReturn(refreshTokenJson("client-newer", 3000L));
        when(valueOperations.get(RedisKeyUtil.oauthRefresh("older")))
                .thenReturn(refreshTokenJson("client-older", 2000L));
        when(valueOperations.get(RedisKeyUtil.oauthRefresh("legacy")))
                .thenReturn(refreshTokenJson("client-legacy", null));
        when(redisTemplate.getExpire(RedisKeyUtil.oauthRefresh("newer"), TimeUnit.SECONDS)).thenReturn(100L);
        when(redisTemplate.getExpire(RedisKeyUtil.oauthRefresh("older"), TimeUnit.SECONDS)).thenReturn(200L);
        when(redisTemplate.getExpire(RedisKeyUtil.oauthRefresh("legacy"), TimeUnit.SECONDS)).thenReturn(300L);

        OAuthClient newer = clientWithId("client-newer", "none", "authorization_code,refresh_token", null, 1);
        OAuthClient older = clientWithId("client-older", "none", "authorization_code,refresh_token", null, 1);
        OAuthClient legacy = clientWithId("client-legacy", "none", "authorization_code,refresh_token", null, 1);
        when(oauthClientMapper.selectBatchIds(anyList())).thenReturn(List.of(newer, older, legacy));

        List<RefreshGrantVO> result = service.listUserRefreshTokens(uid);

        // 按授权时间倒序；存量令牌（无 createTime）排最后
        assertEquals(3, result.size());
        assertEquals("client-newer", result.get(0).getClientId());
        assertEquals("client-older", result.get(1).getClientId());
        assertEquals("client-legacy", result.get(2).getClientId());
        assertNull(result.get(2).getCreatedAt());
    }

    private void prepareAuthorizationCode(OAuthClient client, String code, String verifier) throws Exception {
        String json = authorizationCodeJson(verifier);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);
        when(oauthTokenStore.readAuthorizationCode(code)).thenReturn(json);
        // 失败路径会在原子消费前退出；将成功路径专用桩标为 lenient，严格验证由各测试
        // 对 consumeAuthorizationCode 的 verify/never() 断言承担。
        lenient().when(oauthTokenStore.consumeAuthorizationCode(code, json, 300L)).thenReturn(true);
    }

    private String authorizationCodeJson(String verifier) throws JsonProcessingException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("clientId", "client-1");
        record.put("uid", 9L);
        record.put("scope", "user.read");
        record.put("redirectUri", REDIRECT_URI);
        record.put("codeChallenge", verifier == null ? "" : OAuthUtil.pkceS256(verifier));
        return objectMapper.writeValueAsString(record);
    }

    private String refreshTokenJson(String clientId) throws JsonProcessingException {
        return refreshTokenJson(clientId, 1700000000000L);
    }

    private String refreshTokenJson(String clientId, Long createTime) throws JsonProcessingException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("uid", 9L);
        record.put("clientId", clientId);
        record.put("scope", "user.read");
        // createTime 为空模拟本次升级前签发的存量令牌
        if (createTime != null) {
            record.put("createTime", createTime);
        }
        return objectMapper.writeValueAsString(record);
    }

    private String accessTokenJson(String clientId) throws JsonProcessingException {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("uid", 9L);
        record.put("clientId", clientId);
        record.put("scope", "user.read");
        return objectMapper.writeValueAsString(record);
    }

    private OAuthClient client(String authMethod, String grantTypes, String secret, int requirePkce) {
        return clientWithId("client-1", authMethod, grantTypes, secret, requirePkce);
    }

    private OAuthClient clientWithId(String id, String authMethod, String grantTypes,
                                     String secret, int requirePkce) {
        OAuthClient client = new OAuthClient();
        client.setId(id);
        client.setAuthMethods(authMethod);
        client.setGrantTypes(grantTypes);
        client.setClientSecret(secret);
        client.setRedirectUris(REDIRECT_URI);
        client.setScopes("user.read");
        client.setRequirePkce(requirePkce);
        client.setOwnerEnabled(1);
        client.setAdminApproved(1);
        return client;
    }
}
