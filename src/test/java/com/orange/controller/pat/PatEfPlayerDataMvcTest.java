package com.orange.controller.pat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.orange.common.context.PatContext;
import com.orange.common.context.UserContext;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.common.util.PatUtil;
import com.orange.entity.dto.pat.PatPayload;
import com.orange.entity.po.EfPlayerData;
import com.orange.interceptor.OAuthAuthInterceptor;
import com.orange.interceptor.PatAuthInterceptor;
import com.orange.interceptor.UserAuthInterceptor;
import com.orange.interceptor.WebConfig;
import com.orange.mapper.EfAccountBindingMapper;
import com.orange.mapper.EfPlayerDataMapper;
import com.orange.service.EfPlayerDataService;
import com.orange.service.PatPrincipal;
import com.orange.service.PatService;
import com.orange.service.impl.EfPlayerDataServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用生产 WebConfig、PAT 拦截器与真实取数服务，仅模拟数据库与令牌查询。 */
class PatEfPlayerDataMvcTest {

    private static final String PATH = "/pat/ef-accounts/player-data";
    private static final long UID = 7001L;
    private static final String PAT = PatUtil.generate();
    private static final String LEGACY_PAT = "0123456789abcdef".repeat(4);

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private PatService patService;
    private EfAccountBindingMapper bindingMapper;
    private EfPlayerDataMapper playerDataMapper;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class, WebConfig.class);
        context.refresh();
        patService = context.getBean(PatService.class);
        bindingMapper = context.getBean(EfAccountBindingMapper.class);
        playerDataMapper = context.getBean(EfPlayerDataMapper.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        assertThat(UserContext.getUid()).isNull();
        assertThatThrownBy(PatContext::require).isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 80001);
        context.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-a-pat", "ordinary-access-token"})
    void rejectsMissingOrMalformedCredentials(String token) throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + (token == null ? "" : token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(patService, bindingMapper, playerDataMapper);
    }

    @Test
    void rejectsMissingHeaderAndUnknownOrExpiredPat() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(jsonPath("$.code").value(80001));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(bindingMapper, playerDataMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"endfield-data.read", "gama-data.read", "all"})
    void readsPayloadRoleAndServerWithSupportedScopes(String scope) throws Exception {
        authorize(scope, payload(" role-1 ", " 1 "));
        boundData("role-1", "1");

        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT)
                        .param("uid", "9999").param("roleId", "other-role").param("serverId", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roleId").value("role-1"))
                .andExpect(jsonPath("$.data.serverId").value("1"))
                .andExpect(jsonPath("$.data.playerData.base.level").value(60))
                .andExpect(jsonPath("$.data.updateTime").value("2026-11-19T12:00:00"));

        verify(bindingMapper).countBinding("role-1", "1", UID);
        verify(playerDataMapper).selectByRoleAndServer("role-1", "1");
    }

    @ParameterizedTest
    @ValueSource(strings = {"arknights-data.read", "endfield-data.write", "config.read", ""})
    void rejectsMissingReadScopeBeforeReadingData(String scope) throws Exception {
        authorize(scope, payload("role-1", "1"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(80008));
        verifyNoInteractions(bindingMapper, playerDataMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingGameUidNeverFallsBackToAnotherRole(String gameUid) throws Exception {
        authorize("endfield-data.read", payload(gameUid, "1"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(bindingMapper, playerDataMapper);
    }

    @Test
    void missingPayloadIsRejected() throws Exception {
        authorize("endfield-data.read", null);
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(bindingMapper, playerDataMapper);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingServerUsesExactEmptyServerBinding(String serverId) throws Exception {
        authorize("endfield-data.read", payload("role-1", serverId));
        boundData("role-1", "");
        mockMvc.perform(get(PATH).header("TCL-Token", PAT))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.serverId").value(""));
        verify(bindingMapper).countBinding("role-1", "", UID);
    }

    @Test
    void unboundRoleOrServerCannotReadData() throws Exception {
        authorize("endfield-data.read", payload("role-1", "2"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(80008));
        verify(bindingMapper).countBinding("role-1", "2", UID);
        verifyNoInteractions(playerDataMapper);
    }

    @Test
    void invalidRoleOrServerIsRejectedBeforeQuerying() throws Exception {
        authorize("endfield-data.read", payload("role with spaces", "1"));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(10002));
        authorize("endfield-data.read", payload("role-1", "x".repeat(17)));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(bindingMapper, playerDataMapper);
    }

    @Test
    void boundRoleWithoutDataReturnsSuccessfulNull() throws Exception {
        authorize("endfield-data.read", payload("role-1", "1"));
        when(bindingMapper.countBinding("role-1", "1", UID)).thenReturn(1);
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + PAT))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(header().string("Cache-Control", "private, no-cache"));
    }

    @Test
    void legacyPatUsesLatestOwnedDataAndIgnoresPayloadAndQueryParameters() throws Exception {
        when(patService.resolve(LEGACY_PAT)).thenReturn(new PatPrincipal(UID, "this_land_cloud",
                "endfield-data.read", payload("ignored-role", "ignored-server"), false));
        when(playerDataMapper.selectLatestByOwner(UID)).thenReturn(data("latest-role", "2"));

        mockMvc.perform(get(PATH).header("UC-Token", LEGACY_PAT)
                        .param("roleId", "other-role").param("serverId", "1").param("uid", "9999"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roleId").value("latest-role"))
                .andExpect(jsonPath("$.data.serverId").value("2"))
                .andExpect(header().string("Cache-Control", "private, no-cache"));
        verify(playerDataMapper).selectLatestByOwner(UID);
        verifyNoInteractions(bindingMapper);
    }

    @Test
    void legacyPatWithoutAvailableDataReturns10002() throws Exception {
        when(patService.resolve(LEGACY_PAT)).thenReturn(new PatPrincipal(UID, "this_land_cloud",
                "endfield-data.read", new PatPayload(), false));
        mockMvc.perform(get(PATH).header("Authorization", "Bearer " + LEGACY_PAT))
                .andExpect(jsonPath("$.code").value(10002));
        verify(playerDataMapper).selectLatestByOwner(UID);
    }

    private void authorize(String scope, PatPayload payload) {
        when(patService.resolve(PAT)).thenReturn(new PatPrincipal(UID, "this_land_cloud", scope, payload, true));
    }

    private PatPayload payload(String roleId, String serverId) {
        PatPayload payload = new PatPayload();
        payload.setGameUid(roleId);
        payload.setServerId(serverId);
        return payload;
    }

    private void boundData(String roleId, String serverId) {
        when(bindingMapper.countBinding(roleId, serverId, UID)).thenReturn(1);
        when(playerDataMapper.selectByRoleAndServer(roleId, serverId)).thenReturn(data(roleId, serverId));
    }

    private EfPlayerData data(String roleId, String serverId) {
        EfPlayerData data = new EfPlayerData();
        data.setRoleId(roleId);
        data.setServerId(serverId);
        data.setPlayerData("{\"base\":{\"level\":60}}");
        data.setUpdateTime(LocalDateTime.of(2026, 11, 19, 12, 0));
        return data;
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig implements WebMvcConfigurer {

        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.stream().filter(MappingJackson2HttpMessageConverter.class::isInstance)
                    .map(MappingJackson2HttpMessageConverter.class::cast)
                    .forEach(converter -> converter.getObjectMapper()
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
        }

        @Bean
        PatService patService() { return mock(PatService.class); }

        @Bean
        EfAccountBindingMapper bindingMapper() { return mock(EfAccountBindingMapper.class); }

        @Bean
        EfPlayerDataMapper playerDataMapper() { return mock(EfPlayerDataMapper.class); }

        @Bean
        UserAuthInterceptor userAuthInterceptor() { return mock(UserAuthInterceptor.class); }

        @Bean
        OAuthAuthInterceptor oauthAuthInterceptor() { return mock(OAuthAuthInterceptor.class); }

        @Bean
        PatAuthInterceptor patAuthInterceptor(PatService patService) { return new PatAuthInterceptor(patService); }

        @Bean
        EfPlayerDataService efPlayerDataService(EfAccountBindingMapper bindings, EfPlayerDataMapper data) {
            return new EfPlayerDataServiceImpl(bindings, data, new ObjectMapper(), mock(StringRedisTemplate.class));
        }

        @Bean
        PatEfPlayerDataController controller(EfPlayerDataService service) { return new PatEfPlayerDataController(service); }

        @Bean
        GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(); }
    }
}
