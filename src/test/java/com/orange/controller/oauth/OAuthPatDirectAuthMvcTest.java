package com.orange.controller.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.OAuthTokenResolver;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.interceptor.OAuthAuthInterceptor;
import com.orange.interceptor.PatAuthInterceptor;
import com.orange.interceptor.UserAuthInterceptor;
import com.orange.interceptor.WebConfig;
import com.orange.mapper.TlcPatMapper;
import com.orange.service.OAuthTokenService;
import com.orange.service.PatService;
import com.orange.service.impl.PatServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用真实 PAT 服务与生产路径配置验证所有 OAuth2 PAT 入口的直连应用准入。 */
class OAuthPatDirectAuthMvcTest {

    private AnnotationConfigWebApplicationContext context;
    private OAuthTokenService oauthTokenService;
    private TlcPatMapper mapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class, WebConfig.class);
        context.refresh();
        oauthTokenService = context.getBean(OAuthTokenService.class);
        mapper = context.getBean(TlcPatMapper.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @ParameterizedTest
    @CsvSource({
            "issue,90014", "list,90014", "revoke-token,90014", "revoke-id,90014", "revoke-all,90014",
            "issue,90001", "list,90001", "revoke-token,90001", "revoke-id,90001", "revoke-all,90001",
            "issue,90013", "list,90013", "revoke-token,90013", "revoke-id,90013", "revoke-all,90013"
    })
    void allEndpointsRejectApplicationsFailingDirectAuthAdmission(String action, int code) throws Exception {
        if ("issue".equals(action)) {
            when(oauthTokenService.resolveRefreshToken("refresh-raw"))
                    .thenReturn(new OAuthTokenService.RefreshTokenPrincipal(7001L, "app-alpha", "user.read", "f1"));
        } else {
            when(context.getBean(OAuthTokenResolver.class).resolve("access-raw"))
                    .thenReturn(new OAuthTokenService.OAuthTokenPrincipal(7001L, "app-alpha", "user.read"));
        }
        ResultCode resultCode = switch (code) {
            case 90001 -> ResultCode.OAUTH_CLIENT_INVALID;
            case 90013 -> ResultCode.OAUTH_CLIENT_BANNED;
            default -> ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED;
        };
        doThrow(new BusinessException(resultCode))
                .when(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");

        mockMvc.perform(request(action))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));

        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
        verifyNoInteractions(mapper);
    }

    private MockHttpServletRequestBuilder request(String action) {
        return switch (action) {
            case "issue" -> post("/oauth2/pat").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"refresh_token\":\"refresh-raw\",\"name\":\"ci\",\"expires_in\":3600}");
            case "list" -> get("/oauth2/pat/list").header("Authorization", "Bearer access-raw");
            case "revoke-id" -> post("/oauth2/pat/revoke").header("Authorization", "Bearer access-raw")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"id\":21}");
            case "revoke-token" -> post("/oauth2/pat/revoke").header("Authorization", "Bearer access-raw")
                    .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tlc_pat_target-token\"}");
            default -> post("/oauth2/pat/revoke-all").header("Authorization", "Bearer access-raw");
        };
    }

    @Configuration
    @EnableWebMvc
    static class TestConfig {
        @Bean
        OAuthTokenService oauthTokenService() {
            return mock(OAuthTokenService.class);
        }

        @Bean
        TlcPatMapper tlcPatMapper() {
            return mock(TlcPatMapper.class);
        }

        @Bean
        PatService patService(OAuthTokenService service, TlcPatMapper mapper) {
            return new PatServiceImpl(mapper, service, new ObjectMapper());
        }

        @Bean
        OAuthPatController controller(PatService service) {
            return new OAuthPatController(service);
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
        UserAuthInterceptor userAuthInterceptor() {
            return mock(UserAuthInterceptor.class);
        }

        @Bean
        PatAuthInterceptor patAuthInterceptor() {
            return mock(PatAuthInterceptor.class);
        }

        @Bean
        GlobalExceptionHandler exceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
