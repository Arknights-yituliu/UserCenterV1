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
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用生产 WebConfig、PAT 拦截器与真实读写服务，仅模拟数据库、Redis 与令牌查询。 */
class PatEfPlayerDataMvcTest {

    private static final String PATH = "/pat/ef-accounts/player-data";
    private static final long UID = 7001L;
    private static final String PAT = PatUtil.generate();
    private static final String LEGACY_PAT = "0123456789abcdef".repeat(4);
    private static final String SAVE_BODY = """
            {"roleId":"other-role","serverId":"2","serverType":"official","serverName":"官服",
             "playerData":{"base":{"level":60},"chars":[{"id":"char-1"}]}}
            """;

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private PatService patService;
    private EfAccountBindingMapper bindingMapper;
    private EfPlayerDataMapper playerDataMapper;
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class, WebConfig.class);
        context.refresh();
        patService = context.getBean(PatService.class);
        bindingMapper = context.getBean(EfAccountBindingMapper.class);
        playerDataMapper = context.getBean(EfPlayerDataMapper.class);
        stringRedisTemplate = context.getBean(StringRedisTemplate.class);
        // 排除 Spring 初始化回调，仅检查请求处理期间是否访问限流设施。
        clearInvocations(stringRedisTemplate);
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

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ordinary-access-token", "not-a-pat"})
    void uploadRejectsMissingOrMalformedCredentials(String token) throws Exception {
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + (token == null ? "" : token))
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(patService, bindingMapper, playerDataMapper, stringRedisTemplate);
    }

    @Test
    void uploadRejectsUnknownOrExpiredPat() throws Exception {
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(bindingMapper, playerDataMapper, stringRedisTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"endfield-data.write", "gama-data.write", "all"})
    void uploadAcceptsWriteScopesAndDoesNotRequirePayloadOrExistingBinding(String scope) throws Exception {
        authorize(scope, null);
        allowUpload();
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roleId").value("other-role"))
                .andExpect(jsonPath("$.data.serverId").value("2"))
                .andExpect(jsonPath("$.data.updateTime").isString());
        verify(bindingMapper).upsertBinding("other-role", "2", UID, "official", "官服");
        verify(bindingMapper, never()).countBinding(anyString(), anyString(), any());
        verify(playerDataMapper).upsertPlayerData(eq("other-role"), eq("2"),
                eq("{\"base\":{\"level\":60},\"chars\":[{\"id\":\"char-1\"}]}"), any(LocalDateTime.class));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"endfield-data.read", "gama-data.read", "arknights-data.write"})
    void uploadRejectsScopesWithoutEndfieldWrite(String scope) throws Exception {
        authorize(scope, null);
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80008));
        verifyNoInteractions(bindingMapper, playerDataMapper, stringRedisTemplate);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uploadUsesBodyRoleAndServerAndPatUserForBothTokenGenerations(boolean prefixed) throws Exception {
        String token = prefixed ? PAT : LEGACY_PAT;
        when(patService.resolve(token)).thenReturn(new PatPrincipal(UID, "this_land_cloud",
                "endfield-data.write", payload("pat-read-role", "1"), prefixed));
        allowUpload();
        mockMvc.perform(post(PATH + "/save").header("TCL-Token", token)
                        .param("roleId", "query-role").param("serverId", "3").param("uid", "9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(SAVE_BODY.replace("\"roleId\":", "\"uid\":9999,\"roleId\":")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(bindingMapper).upsertBinding("other-role", "2", UID, "official", "官服");
        verify(bindingMapper, never()).countBinding(anyString(), anyString(), any());
    }

    @Test
    void uploadDefaultsOmittedServerToEmptyString() throws Exception {
        authorize("endfield-data.write", null);
        allowUpload();
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"role-1\",\"playerData\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.serverId").value(""));
        verify(bindingMapper).upsertBinding("role-1", "", UID, null, null);
        verify(playerDataMapper).upsertPlayerData(eq("role-1"), eq(""), eq("{}"), any(LocalDateTime.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"roleId\":\"role-1\"}",
            "{\"roleId\":\"   \",\"playerData\":{}}"})
    void uploadValidatesRequiredFields(String body) throws Exception {
        authorize("endfield-data.write", null);
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(bindingMapper, playerDataMapper, stringRedisTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"roleId\":\"role with spaces\",\"playerData\":{}}",
            "{\"roleId\":\"role-1\",\"serverId\":\"非ASCII\",\"playerData\":{}}",
            "{\"roleId\":\"role-1\",\"playerData\":[]}",
            "{\"roleId\":\"role-1\",\"playerData\":\"text\"}"})
    void uploadRejectsInvalidIdentityOrNonObjectDataBeforeRateLimiting(String body) throws Exception {
        authorize("endfield-data.write", null);
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(bindingMapper, playerDataMapper, stringRedisTemplate);
    }

    @Test
    void uploadPreservesRoleRateLimitAndDoesNotWriteWhenQuotaIsUsed() throws Exception {
        authorize("endfield-data.write", null);
        ValueOperations<String, String> operations = allowUpload();
        when(operations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(2)))).thenReturn(false);
        mockMvc.perform(post(PATH + "/save").header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(SAVE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(30006));
        verifyNoInteractions(bindingMapper, playerDataMapper);
    }

    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> allowUpload() {
        ValueOperations<String, String> operations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(operations);
        when(operations.setIfAbsent(anyString(), eq("1"), eq(Duration.ofSeconds(2)))).thenReturn(true);
        return operations;
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
        StringRedisTemplate stringRedisTemplate() { return mock(StringRedisTemplate.class); }

        @Bean
        EfPlayerDataService efPlayerDataService(EfAccountBindingMapper bindings, EfPlayerDataMapper data,
                                               StringRedisTemplate redis) {
            return new EfPlayerDataServiceImpl(bindings, data, new ObjectMapper(), redis);
        }

        @Bean
        PatEfPlayerDataController controller(EfPlayerDataService service) { return new PatEfPlayerDataController(service); }

        @Bean
        GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(); }
    }
}
