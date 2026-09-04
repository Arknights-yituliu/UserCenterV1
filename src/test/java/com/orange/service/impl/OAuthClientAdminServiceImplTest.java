package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.oauthclient.OAuthClientRegisterRequest;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.vo.oauth.OAuthClientCredentialVO;
import com.orange.entity.vo.oauth.OAuthClientVO;
import com.orange.mapper.OAuthClientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * OAuth 客户端注册安全规则测试。
 *
 * <p>这些测试直接捕获入库实体，同时检查接口响应，防止只修复响应外观却仍把公共客户端
 * 密钥写入数据库，或者只修复数据库却没有向管理端公开客户端类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class OAuthClientAdminServiceImplTest {

    @Mock
    private OAuthClientMapper oauthClientMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    private OAuthClientAdminServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OAuthClientAdminServiceImpl(oauthClientMapper, redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "maxClientsPerOwner", 10);
        lenient().when(oauthClientMapper.selectCount(any())).thenReturn(0L);
    }

    @Test
    void publicClientDoesNotGenerateOrStoreSecret() {
        OAuthClientRegisterRequest request = validRequest();
        request.setAuthMethod("none");
        request.setGrantTypes(Arrays.asList("refresh_token", "authorization_code", "refresh_token"));
        request.setScopes(Arrays.asList(" user.read ", "user.read", "user.email"));

        OAuthClientCredentialVO credential = service.register(7L, request);

        ArgumentCaptor<OAuthClient> captor = ArgumentCaptor.forClass(OAuthClient.class);
        verify(oauthClientMapper).insert(captor.capture());
        OAuthClient stored = captor.getValue();
        assertNull(stored.getClientSecret());
        assertEquals("none", stored.getAuthMethods());
        assertEquals("authorization_code,refresh_token", stored.getGrantTypes());
        assertEquals("user.read,user.email", stored.getScopes());
        assertEquals(1, stored.getOwnerEnabled());
        assertEquals(0, stored.getAdminApproved());
        assertEquals(0, stored.getDirectAuthEnabled());
        assertNull(credential.getClientSecret());
        assertEquals("none", credential.getAuthMethod());
        assertTrue(credential.getOwnerEnabled());
        assertFalse(credential.getAdminApproved());
        assertFalse(credential.getDirectAuthEnabled());
    }

    @Test
    void explicitlyDeclaredConfidentialClientGeneratesHashedSecret() {
        OAuthClientCredentialVO credential = service.register(7L, validRequest());

        ArgumentCaptor<OAuthClient> captor = ArgumentCaptor.forClass(OAuthClient.class);
        verify(oauthClientMapper).insert(captor.capture());
        OAuthClient stored = captor.getValue();
        assertEquals("client_secret_post", stored.getAuthMethods());
        assertNotNull(credential.getClientSecret());
        assertFalse(credential.getClientSecret().isBlank());
        assertFalse(credential.getClientSecret().equals(stored.getClientSecret()));
        assertEquals("client_secret_post", credential.getAuthMethod());
        assertTrue(new BCryptPasswordEncoder().matches(credential.getClientSecret(), stored.getClientSecret()));
    }

    @Test
    void rejectsMissingAuthMethod() {
        OAuthClientRegisterRequest request = validRequest();
        request.setAuthMethod(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsMissingGrantTypes() {
        OAuthClientRegisterRequest request = validRequest();
        request.setGrantTypes(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsUnknownAuthMethod() {
        OAuthClientRegisterRequest request = validRequest();
        request.setAuthMethod("client_secret_basic");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsAuthMethodWithWhitespaceInsteadOfSilentlyTrimmingIt() {
        OAuthClientRegisterRequest request = validRequest();
        request.setAuthMethod(" none ");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsUnknownGrantType() {
        OAuthClientRegisterRequest request = validRequest();
        request.setGrantTypes(Arrays.asList("authorization_code", "password"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rejectsRefreshGrantWithoutAuthorizationCode() {
        OAuthClientRegisterRequest request = validRequest();
        request.setGrantTypes(Collections.singletonList("refresh_token"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void publicClientCannotRotateSecret() {
        OAuthClient client = client("none", "authorization_code,refresh_token");
        client.setClientSecret(null);
        client.setOwnerUid(7L);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.rotateSecret(7L, "client-1"));

        assertEquals(ResultCode.ILLEGAL_OPERATION.getCode(), exception.getCode());
    }

    @Test
    void newlyRegisteredClientCannotBeEnabledBeforeAdminApproval() {
        OAuthClient client = client("none", "authorization_code");
        client.setOwnerUid(7L);
        client.setAdminApproved(0);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.setOwnerEnabled(7L, "client-1", true));

        assertEquals(ResultCode.OAUTH_CLIENT_BANNED.getCode(), exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost.example.com/callback",
            "https://spa.example.com/callback#fragment",
            "https://user@spa.example.com/callback"
    })
    void rejectsUnsafeRedirectUri(String redirectUri) {
        OAuthClientRegisterRequest request = validRequest();
        request.setRedirectUris(Collections.singletonList(redirectUri));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:5173/callback",
            "http://127.0.0.1:5173/callback",
            "http://[::1]:5173/callback"
    })
    void allowsHttpOnlyForExactLoopbackHosts(String redirectUri) {
        OAuthClientRegisterRequest request = validRequest();
        request.setRedirectUris(Collections.singletonList(redirectUri));

        service.register(7L, request);

        ArgumentCaptor<OAuthClient> captor = ArgumentCaptor.forClass(OAuthClient.class);
        verify(oauthClientMapper).insert(captor.capture());
        assertEquals(redirectUri, captor.getValue().getRedirectUris());
    }

    @Test
    void rejectsWebsiteOriginWithPath() {
        OAuthClientRegisterRequest request = validRequest();
        request.setWebsiteOrigin("https://spa.example.com/oauth");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(7L, request));

        assertEquals(ResultCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void managementViewExposesAuthMethodAndGrantTypes() {
        OAuthClient client = client("none", "authorization_code,refresh_token");
        client.setOwnerUid(7L);
        client.setDirectAuthEnabled(1);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        OAuthClientVO vo = service.getClient(7L, "client-1");

        assertEquals("none", vo.getAuthMethod());
        assertEquals(Arrays.asList("authorization_code", "refresh_token"), vo.getGrantTypes());
        assertTrue(vo.getOwnerEnabled());
        assertTrue(vo.getAdminApproved());
        assertTrue(vo.getDirectAuthEnabled());
    }

    private OAuthClientRegisterRequest validRequest() {
        OAuthClientRegisterRequest request = new OAuthClientRegisterRequest();
        request.setClientName("Example SPA");
        request.setRedirectUris(Collections.singletonList("https://spa.example.com/oauth/callback"));
        request.setScopes(Collections.singletonList("user.read"));
        request.setAuthMethod("client_secret_post");
        request.setGrantTypes(Arrays.asList("authorization_code", "refresh_token"));
        request.setWebsiteOrigin("https://spa.example.com");
        return request;
    }

    private OAuthClient client(String authMethod, String grantTypes) {
        OAuthClient client = new OAuthClient();
        client.setId("client-1");
        client.setClientName("Example SPA");
        client.setAuthMethods(authMethod);
        client.setGrantTypes(grantTypes);
        client.setRedirectUris("https://spa.example.com/oauth/callback");
        client.setScopes("user.read");
        client.setRequirePkce(1);
        client.setOwnerEnabled(1);
        client.setAdminApproved(1);
        return client;
    }
}
