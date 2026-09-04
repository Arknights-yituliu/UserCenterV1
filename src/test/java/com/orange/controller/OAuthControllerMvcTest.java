package com.orange.controller;

import com.orange.common.config.CorsConfig;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.service.OAuthTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

/**
 * OAuth 表单端点与静态 CORS 白名单的 MVC 契约测试。
 *
 * <p>使用最小 WebMVC 上下文，只注册本控制器、异常处理器和 CORS 配置，避免数据库、
 * Redis 或完整应用启动影响 HTTP 参数绑定与跨域响应的验证。</p>
 */
class OAuthControllerMvcTest {

    private AnnotationConfigWebApplicationContext context;
    private OAuthTokenService oauthTokenService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "oauth-cors-test",
                Map.of("user-center.oauth.allowed-origins",
                        "https://spa.example.com, http://localhost:5173")));
        context.register(TestWebConfig.class);
        context.refresh();
        oauthTokenService = context.getBean(OAuthTokenService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void publicClientTokenRequestDoesNotRequireClientSecretParameter() throws Exception {
        OAuthTokenVO response = new OAuthTokenVO();
        response.setAccessToken("access-1");
        response.setTokenType("Bearer");
        response.setExpiresIn(7200L);
        when(oauthTokenService.issueToken(
                eq("authorization_code"), eq("client-1"), eq(null),
                eq("code-1"), eq("https://spa.example.com/callback"), eq("verifier-1"), eq(null)))
                .thenReturn(response);

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "client-1")
                        .param("code", "code-1")
                        .param("redirect_uri", "https://spa.example.com/callback")
                        .param("code_verifier", "verifier-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.access_token").value("access-1"))
                .andExpect(jsonPath("$.data.refresh_token").value(nullValue()));

        verify(oauthTokenService).issueToken(
                "authorization_code", "client-1", null,
                "code-1", "https://spa.example.com/callback", "verifier-1", null);
    }

    @Test
    void allowedOriginPreflightReturnsCorsHeaders() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "https://spa.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://spa.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void unknownOriginPreflightDoesNotReceiveCorsPermission() throws Exception {
        mockMvc.perform(options("/oauth2/token")
                        .header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void businessFailureKeepsUnifiedResponseEnvelope() throws Exception {
        when(oauthTokenService.issueToken(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ResultCode.OAUTH_PKCE_INVALID));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "client-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(90008))
                .andExpect(jsonPath("$.msg").value(ResultCode.OAUTH_PKCE_INVALID.getMessage()));
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {

        @Bean
        OAuthTokenService oauthTokenService() {
            return mock(OAuthTokenService.class);
        }

        @Bean
        OAuthController oauthController(OAuthTokenService oauthTokenService) {
            return new OAuthController(oauthTokenService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        CorsConfig corsConfig() {
            // 属性由测试 Environment 按生产中的 @Value 路径注入；测试值在逗号后带空格，
            // 同时验证 CorsConfig 的清理逻辑不会破坏精确 Origin 匹配。
            return new CorsConfig();
        }
    }
}
