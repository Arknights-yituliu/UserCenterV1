package com.orange.controller.pat;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.common.util.PatUtil;
import com.orange.entity.dto.pat.PatPayload;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.interceptor.PatAuthInterceptor;
import com.orange.service.AkAccountService;
import com.orange.service.PatPrincipal;
import com.orange.service.PatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PAT 干员数据接口的 MVC 契约测试
 *
 * <p>最小 WebMVC 上下文，只注册拦截器、控制器与异常处理器，验证三件事：拦截器确实挂在
 * {@code /pat/**} 上（不带凭证能被拦下）、账号只来自 payload、归属校验失败按 80008 返回。
 * 不启动数据库与 Redis。</p>
 *
 * @author UserCenter
 */
class PatAkAccountMvcTest {

    private static final long UID = 7001L;

    private static final String SCOPE = "ak-data.read";

    private static final String PAT = PatUtil.generate();

    private AnnotationConfigWebApplicationContext context;

    private PatService patService;

    private AkAccountService akAccountService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();
        patService = context.getBean(PatService.class);
        akAccountService = context.getBean(AkAccountService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void missingPatIsRejectedWith80001() throws Exception {
        mockMvc.perform(get("/pat/ak-accounts/operators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_LOGIN.getCode()))
                .andExpect(jsonPath("$.msg").value("缺少 PAT"));
    }

    @Test
    void malformedPatIsRejectedWithoutQuerying() throws Exception {
        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer not-a-pat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.NOT_LOGIN.getCode()));
    }

    @Test
    void validPatReadsAccountFromPayload() throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal(SCOPE, payload("123456789")));
        when(akAccountService.listOperators(UID, "123456789", "skland"))
                .thenReturn(new OperatorListVO("123456789", List.of()));

        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer " + PAT))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.akUid").value("123456789"))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void payloadWithoutGameUidIsRejectedWith10002() throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal(SCOPE, new PatPayload()));

        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer " + PAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_VALID_ERROR.getCode()))
                .andExpect(jsonPath("$.msg").value("PAT 未配置 game_uid"));
    }

    @Test
    void blankGameUidIsRejectedInsteadOfFallingBackToDefaultSource() throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal(SCOPE, payload("   ")));

        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer " + PAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_VALID_ERROR.getCode()));

        verify(akAccountService, never()).listOperators(any(), any(), any());
    }

    @Test
    void queryParamsAreIgnoredAndAccountStillComesFromPat() throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal(SCOPE, payload("123456789")));
        when(akAccountService.listOperators(UID, "123456789", "skland"))
                .thenReturn(new OperatorListVO("123456789", List.of()));

        mockMvc.perform(get("/pat/ak-accounts/operators").param("akUid", "999")
                        .header("Authorization", "Bearer " + PAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.akUid").value("123456789"));

        verify(akAccountService).listOperators(UID, "123456789", "skland");
    }

    @Test
    void legacyTokenWithoutPrefixFallsBackToDefaultSource() throws Exception {
        // 旧令牌是 64 位小写 hex、没有 payload 约定：账号交给业务层按「默认来源 → 最近变更」选择，
        // 与 OAuth 版不传 akUid 的表现一致
        String legacy = "0123456789abcdef".repeat(4);
        when(patService.resolve(legacy)).thenReturn(
                new PatPrincipal(UID, "this_land_cloud", SCOPE, new PatPayload(), false));
        when(akAccountService.listOperators(UID, null, null))
                .thenReturn(new OperatorListVO("123456789", List.of()));

        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer " + legacy))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.akUid").value("123456789"));
    }

    @Test
    void legacyTokenIgnoresPayloadEvenIfPresent() throws Exception {
        // 表单上旧令牌本不该有 payload；即使库里有残留，也不参与选账号
        String legacy = "0123456789abcdef".repeat(4);
        when(patService.resolve(legacy)).thenReturn(
                new PatPrincipal(UID, "this_land_cloud", SCOPE, payload("999"), false));
        when(akAccountService.listOperators(UID, null, null))
                .thenReturn(new OperatorListVO("123456789", List.of()));

        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer " + legacy))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.akUid").value("123456789"));
    }

    @Test
    void missingScopeIsRejectedWith80008() throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal("config.read", payload("123456789")));

        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer " + PAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    void accountNotOwnedIsRejectedWith80008() throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal(SCOPE, payload("999")));
        when(akAccountService.listOperators(UID, "999", "skland"))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN));

        mockMvc.perform(get("/pat/ak-accounts/operators")
                        .header("Authorization", "Bearer " + PAT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.FORBIDDEN.getCode()));
    }

    private PatPrincipal principal(String scope, PatPayload payload) {
        return new PatPrincipal(UID, "this_land_cloud", scope, payload, true);
    }

    private PatPayload payload(String gameUid) {
        PatPayload payload = new PatPayload();
        payload.setGameUid(gameUid);
        return payload;
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig implements WebMvcConfigurer {

        @Bean
        PatService patService() {
            return mock(PatService.class);
        }

        @Bean
        AkAccountService akAccountService() {
            return mock(AkAccountService.class);
        }

        @Bean
        PatAuthInterceptor patAuthInterceptor(PatService patService) {
            return new PatAuthInterceptor(patService);
        }

        @Bean
        PatAkAccountController patAkAccountController(AkAccountService akAccountService) {
            return new PatAkAccountController(akAccountService);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {
            registry.addInterceptor(patAuthInterceptor(patService())).addPathPatterns("/pat/**");
        }
    }
}
