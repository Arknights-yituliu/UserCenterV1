package com.orange.controller.oauth;

import com.orange.common.context.OAuthTokenResolver;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.entity.vo.pat.PatIssueVO;
import com.orange.entity.vo.pat.PatVO;
import com.orange.interceptor.OAuthAuthInterceptor;
import com.orange.service.PatService;
import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;
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
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 应用侧 PAT 接口的 MVC 契约测试
 *
 * <p>验证拦截器注册的关键边界：根路径 {@code POST /oauth2/pat} 凭请求体 refresh_token
 * 签发、不经过 access_token 拦截器；而 {@code /list}、{@code /revoke} 仍必须携带
 * access_token。测试内的路径注册与生产 {@code WebConfig} 保持一致。</p>
 *
 * @author UserCenter
 */
class OAuthPatMvcTest {

    private AnnotationConfigWebApplicationContext context;

    private PatService patService;

    private OAuthTokenResolver oauthTokenResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();
        patService = context.getBean(PatService.class);
        oauthTokenResolver = context.getBean(OAuthTokenResolver.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void issueDoesNotRequireAccessToken() throws Exception {
        PatIssueVO vo = new PatIssueVO();
        vo.setToken("tlc_pat_test-token");
        vo.setClientId("app-alpha");
        when(patService.issueByRefreshToken(any())).thenReturn(vo);

        mockMvc.perform(post("/oauth2/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"refresh-raw\",\"name\":\"ci\",\"expires_in\":3600}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("tlc_pat_test-token"))
                .andExpect(jsonPath("$.data.clientId").value("app-alpha"));

        verify(patService).issueByRefreshToken(any());
        verifyNoInteractions(oauthTokenResolver);
    }

    @Test
    void nonObjectPayloadIsRejectedAsParamError() throws Exception {
        mockMvc.perform(post("/oauth2/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"refresh-raw\",\"name\":\"ci\",\"expires_in\":3600,"
                                + "\"payload\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));

        verify(patService, never()).issueByRefreshToken(any());
    }

    @Test
    void unknownPayloadFieldsAreAcceptedAndIgnored() throws Exception {
        PatIssueVO vo = new PatIssueVO();
        vo.setToken("tlc_pat_test-token");
        when(patService.issueByRefreshToken(any())).thenReturn(vo);

        mockMvc.perform(post("/oauth2/pat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"refresh-raw\",\"name\":\"ci\",\"expires_in\":3600,"
                                + "\"payload\":{\"game_uid\":\"12345678\",\"game_name\":\"明日方舟\","
                                + "\"future_field\":{\"a\":1}}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(patService).issueByRefreshToken(any());
    }

    @Test
    void listStillRequiresAccessToken() throws Exception {
        mockMvc.perform(get("/oauth2/pat/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_LOGIN.getCode()));

        verify(patService, never()).listByClient(any(), any());
    }

    @Test
    void listUsesAccessTokenIdentityAndReturnsTokensFromOtherApplications() throws Exception {
        when(oauthTokenResolver.resolve("access-raw"))
                .thenReturn(new OAuthTokenPrincipal(7001L, "app-alpha", "user.read"));
        PatVO personal = new PatVO();
        personal.setClientId("this_land_cloud");
        personal.setToken("personal-token");
        PatVO otherApp = new PatVO();
        otherApp.setClientId("app-beta");
        otherApp.setToken("other-app-token");
        when(patService.listByClient(7001L, "app-alpha")).thenReturn(List.of(personal, otherApp));

        mockMvc.perform(get("/oauth2/pat/list")
                        .header("Authorization", "Bearer access-raw")
                        .param("uid", "9999")
                        .param("client_id", "app-beta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].clientId").value("this_land_cloud"))
                .andExpect(jsonPath("$.data[0].token").value("personal-token"))
                .andExpect(jsonPath("$.data[1].clientId").value("app-beta"));

        verify(patService).listByClient(7001L, "app-alpha");
    }

    @Test
    void revokeStillRequiresAccessToken() throws Exception {
        mockMvc.perform(post("/oauth2/pat/revoke")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tlc_pat_test-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_LOGIN.getCode()));

        verify(patService, never()).revokeByClient(any(), any(), any());
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig implements WebMvcConfigurer {

        @Bean
        PatService patService() {
            return mock(PatService.class);
        }

        @Bean
        OAuthTokenResolver oauthTokenResolver() {
            return mock(OAuthTokenResolver.class);
        }

        @Bean
        OAuthAuthInterceptor oauthAuthInterceptor(OAuthTokenResolver resolver) {
            return new OAuthAuthInterceptor(resolver);
        }

        @Bean
        OAuthPatController oAuthPatController(PatService patService) {
            return new OAuthPatController(patService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(oauthAuthInterceptor(oauthTokenResolver()))
                    .addPathPatterns("/oauth2/pat", "/oauth2/pat/**")
                    .excludePathPatterns("/oauth2/pat");
        }
    }
}
