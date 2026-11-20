package com.orange.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.OAuthTokenResolver;
import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.common.util.RedisKeyUtil;
import com.orange.controller.oauth.OAuthController;
import com.orange.interceptor.*;
import com.orange.entity.po.UserInfo;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.AccountDeletionService;
import com.orange.service.OAuthTokenService;
import com.orange.service.UserService;
import com.orange.common.util.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用生产路由配置验证两种注销凭证隔离及账户存在性拦截。 */
class AccountDeletionMvcTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AccountDeletionService service;
    private UserInfoMapper users;
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, WebConfig.class, AccountLifecycleWebConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        service = context.getBean(AccountDeletionService.class);
        users = context.getBean(UserInfoMapper.class);
        values = context.getBean(StringRedisTemplate.class).opsForValue();
        when(users.selectById(9L)).thenReturn(new UserInfo());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        context.close();
    }

    @Test
    void userEndpointRequiresSession() throws Exception {
        mvc.perform(post("/user/account/delete"))
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(service);
    }

    @Test
    void sessionDeterminesUidAndBodyCannotOverrideIt() throws Exception {
        when(values.get(RedisKeyUtil.token("session"))).thenReturn("{\"uid\":9,\"clientId\":\"app\"}");
        mvc.perform(post("/user/account/delete").header("Authorization", "Bearer session")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"uid\":10,\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).deleteAccount(9L, "123456");
        verify(service, never()).deleteAccount(10L, "123456");
    }

    @Test
    void oauthAccessTokenCannotReplaceUserSession() throws Exception {
        mvc.perform(post("/user/account/delete").header("Authorization", "Bearer access"))
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(service);
        verifyNoInteractions(context.getBean(OAuthTokenResolver.class));
    }

    @Test
    void refreshBodyWorksWithoutSessionOrAccessHeader() throws Exception {
        mvc.perform(post("/oauth2/account/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"refresh\",\"uid\":10,\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).deleteByRefreshToken("refresh", "123456");
        verifyNoInteractions(context.getBean(OAuthTokenResolver.class));
        verifyNoInteractions(context.getBean(StringRedisTemplate.class).opsForValue());
    }

    @Test
    void oauthEndpointRequiresRefreshEvenWithAccessHeader() throws Exception {
        mvc.perform(post("/oauth2/account/delete").header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(service);
    }

    @Test
    void invalidRefreshReturnsTokenError() throws Exception {
        doThrow(new BusinessException(ResultCode.OAUTH_TOKEN_INVALID)).when(service).deleteByRefreshToken("expired", "123456");
        mvc.perform(post("/oauth2/account/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"expired\",\"code\":\"123456\"}"))
                .andExpect(jsonPath("$.code").value(90009));
    }

    @Test
    void bothEndpointsRequireSixDigitCode() throws Exception {
        when(values.get(RedisKeyUtil.token("session"))).thenReturn("{\"uid\":9}");
        mvc.perform(post("/user/account/delete").header("TCL-Token", "session")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10002));
        mvc.perform(post("/oauth2/account/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"refresh\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10002));
        mvc.perform(post("/oauth2/account/delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"refresh\",\"code\":\"bad\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(service);
    }

    @Test
    void sessionCodeSenderRequiresSessionAndIgnoresProvidedEmail() throws Exception {
        mvc.perform(post("/user/account/send-delete-code"))
                .andExpect(jsonPath("$.code").value(80001));
        when(values.get(RedisKeyUtil.token("session"))).thenReturn("{\"uid\":9}");
        mvc.perform(post("/user/account/send-delete-code").header("TCL-Token", "session")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"attacker@example.com\"}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).sendDeletionCode(9L, "127.0.0.1");
    }

    @Test
    void oauthCodeSenderUsesRefreshBodyWithoutAccessToken() throws Exception {
        mvc.perform(post("/oauth2/account/send-delete-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"refresh\"}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(service).sendDeletionCodeByRefreshToken("refresh", "127.0.0.1");
        verifyNoInteractions(context.getBean(OAuthTokenResolver.class));
    }

    @Test
    void staleSessionForDeletedAccountIsRejected() throws Exception {
        when(values.get(RedisKeyUtil.token("stale"))).thenReturn("{\"uid\":9}");
        when(users.selectById(9L)).thenReturn(null);
        mvc.perform(post("/user/account/delete").header("TCL-Token", "stale"))
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(service);
        assertNull(UserContext.getUid());
    }

    @Test
    void staleOAuthAccessCannotAccessDeletedAccountsResources() throws Exception {
        when(context.getBean(OAuthTokenResolver.class).resolve("stale-access"))
                .thenReturn(new OAuthTokenService.OAuthTokenPrincipal(9L, "app", "user.read"));
        when(users.selectById(9L)).thenReturn(null);
        mvc.perform(get("/oauth2/userinfo").header("Authorization", "Bearer stale-access"))
                .andExpect(jsonPath("$.code").value(80001));
        assertNull(UserContext.getUid());
    }

    @Test
    void stalePatCannotAccessDeletedAccountsResources() throws Exception {
        when(context.getBean(PatAuthInterceptor.class).preHandle(any(), any(), any())).thenAnswer(invocation -> {
            UserContext.setUid(9L);
            return true;
        });
        when(users.selectById(9L)).thenReturn(null);
        mvc.perform(get("/pat/resource").header("Authorization", "Bearer stale-pat"))
                .andExpect(jsonPath("$.code").value(80001));
    }

    @RestController
    static class TestResourceController {
        @GetMapping("/pat/resource")
        Result<Void> resource() {
            return Result.success();
        }
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean AccountDeletionService service() { return mock(AccountDeletionService.class); }
        @Bean UserInfoMapper users() { return mock(UserInfoMapper.class); }
        @Bean TestResourceController resources() { return new TestResourceController(); }
        @Bean UserController userController(AccountDeletionService service) {
            return new UserController(mock(UserService.class), mock(OAuthTokenService.class), service);
        }
        @Bean OAuthController oauthController(AccountDeletionService service) {
            return new OAuthController(mock(OAuthTokenService.class), service);
        }
        @Bean @SuppressWarnings("unchecked") StringRedisTemplate redis() {
            StringRedisTemplate redis = mock(StringRedisTemplate.class);
            when(redis.opsForValue()).thenReturn(mock(ValueOperations.class));
            return redis;
        }
        @Bean UserAuthInterceptor userAuth(StringRedisTemplate redis) {
            return new UserAuthInterceptor(redis, new ObjectMapper());
        }
        @Bean OAuthTokenResolver resolver() { return mock(OAuthTokenResolver.class); }
        @Bean OAuthAuthInterceptor oauthAuth(OAuthTokenResolver resolver) {
            return new OAuthAuthInterceptor(resolver);
        }
        @Bean PatAuthInterceptor patAuth() { return mock(PatAuthInterceptor.class); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
    }
}
