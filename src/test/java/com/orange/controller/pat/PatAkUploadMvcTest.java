package com.orange.controller.pat;

import com.orange.common.context.PatContext;
import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.common.util.PatUtil;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.dto.pat.PatPayload;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.interceptor.OAuthAuthInterceptor;
import com.orange.interceptor.PatAuthInterceptor;
import com.orange.interceptor.UserAuthInterceptor;
import com.orange.interceptor.WebConfig;
import com.orange.service.AkAccountService;
import com.orange.service.PatPrincipal;
import com.orange.service.PatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 使用生产路由和 PAT 鉴权，验证上传权限、请求契约及用户身份来源；不连接外部服务。 */
class PatAkUploadMvcTest {

    private static final String PATH = "/pat/ak-accounts/operators/save";
    private static final long UID = 7001L;
    private static final String PAT = PatUtil.generate();
    private static final String BODY = """
            {
              "playerInfo": {"akUid": "other-account", "channelName": "官服",
                             "channelMasterId": "1001", "nickName": "博士"},
              "operators": [{"id": "char_002_amiya", "level": 80}],
              "items": [{"itemId": "3001", "count": 111}],
              "skins": [{"skinId": "char_002_amiya@default", "gainTs": 123}],
              "playerData": "raw player/info text"
            }
            """;

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mockMvc;
    private PatService patService;
    private AkAccountService akAccountService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class, WebConfig.class);
        context.refresh();
        patService = context.getBean(PatService.class);
        akAccountService = context.getBean(AkAccountService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        assertThat(UserContext.getUid()).isNull();
        assertThat(UserContext.getScope()).isNull();
        assertThatThrownBy(PatContext::require).isInstanceOf(BusinessException.class);
        context.close();
    }

    @Test
    void rejectsMissingPatBeforeSaving() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(patService, akAccountService);
    }

    @Test
    void rejectsAccessTokenBeforeLookingUpPat() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(patService, akAccountService);
    }

    @Test
    void rejectsExpiredOrRevokedPat() throws Exception {
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80001));
        verifyNoInteractions(akAccountService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"arknights-data.write", "gama-data.write", "all"})
    void acceptsWriteScopeWithoutRequiringPayload(String scope) throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal(scope, null, true));
        when(akAccountService.saveOperators(eq(UID), any())).thenReturn(new OperatorSaveResultVO(1, 2, 3));

        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.createdCount").value(1))
                .andExpect(jsonPath("$.data.updatedCount").value(2))
                .andExpect(jsonPath("$.data.unchangedCount").value(3));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"arknights-data.read", "gama-data.read", "endfield-data.write", "config.write"})
    void rejectsScopeWithoutArknightsWritePermission(String scope) throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal(scope, null, true));
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(80008));
        verifyNoInteractions(akAccountService);
    }

    @Test
    void uploadsOtherAccountAndUsesPatUserRegardlessOfPayloadOrCallerUid() throws Exception {
        PatPayload payload = new PatPayload();
        payload.setGameUid("pat-read-account");
        payload.setSource("manual");
        when(patService.resolve(PAT)).thenReturn(principal("arknights-data.write", payload, true));
        when(akAccountService.saveOperators(eq(UID), any())).thenReturn(new OperatorSaveResultVO(1, 0, 0));

        mockMvc.perform(post(PATH).param("uid", "9999").param("akUid", "query-account")
                        .header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"playerInfo\":", "\"uid\": 9999, \"playerInfo\":")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<OperatorSaveRequest> captor = ArgumentCaptor.forClass(OperatorSaveRequest.class);
        verify(akAccountService).saveOperators(eq(UID), captor.capture());
        OperatorSaveRequest request = captor.getValue();
        assertThat(request.getPlayerInfo().getAkUid()).isEqualTo("other-account");
        assertThat(request.getPlayerInfo().getChannelName()).isEqualTo("官服");
        assertThat(request.getPlayerInfo().getChannelMasterId()).isEqualTo("1001");
        assertThat(request.getPlayerInfo().getNickName()).isEqualTo("博士");
        assertThat(request.getOperators().get(0).getLevel()).isEqualTo(80);
        assertThat(request.getItems().get(0).getCount()).isEqualTo(111L);
        assertThat(request.getSkins().get(0).getGainTs()).isEqualTo(123);
        assertThat(request.getPlayerData()).isEqualTo("raw player/info text");
    }

    @Test
    void acceptsLegacyPatThroughTclTokenHeader() throws Exception {
        String legacy = "0123456789abcdef".repeat(4);
        when(patService.resolve(legacy)).thenReturn(principal("arknights-data.write", null, false));
        when(akAccountService.saveOperators(eq(UID), any())).thenReturn(new OperatorSaveResultVO(1, 0, 0));
        mockMvc.perform(post(PATH).header("TCL-Token", legacy)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(akAccountService).saveOperators(eq(UID), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"playerInfo\":{\"akUid\":\"ak-1\"},\"operators\":[]}",
            "{\"playerInfo\":{\"akUid\":\" \"},\"operators\":[{\"id\":\"amiya\"}]}",
            "{\"playerInfo\":{\"akUid\":\"ak-1\"},\"operators\":[{\"id\":\"amiya\",\"level\":-1}]}",
            "{\"playerInfo\":{\"akUid\":\"ak-1\"},\"operators\":[{\"id\":\"amiya\"}],\"items\":[{\"itemId\":\"3001\",\"count\":-1}]}",
            "{\"playerInfo\":{\"akUid\":\"ak-1\"},\"operators\":[{\"id\":\"amiya\"}],\"skins\":[{\"skinId\":\"\"}]}"
    })
    void validatesSameNestedUploadFieldsAsOauth(String body) throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal("arknights-data.write", null, true));
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10002));
        verifyNoInteractions(akAccountService);
    }

    @Test
    void propagatesExistingSaveBusinessErrors() throws Exception {
        when(patService.resolve(PAT)).thenReturn(principal("arknights-data.write", null, true));
        when(akAccountService.saveOperators(eq(UID), any()))
                .thenThrow(new BusinessException(ResultCode.OPERATOR_SAVE_CONFLICT));
        mockMvc.perform(post(PATH).header("Authorization", "Bearer " + PAT)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.OPERATOR_SAVE_CONFLICT.getCode()));
    }

    private PatPrincipal principal(String scope, PatPayload payload, boolean prefixed) {
        return new PatPrincipal(UID, "this_land_cloud", scope, payload, prefixed);
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {

        @Bean
        PatService patService() {
            return mock(PatService.class);
        }

        @Bean
        AkAccountService akAccountService() {
            return mock(AkAccountService.class);
        }

        @Bean
        UserAuthInterceptor userAuthInterceptor() {
            return mock(UserAuthInterceptor.class);
        }

        @Bean
        OAuthAuthInterceptor oauthAuthInterceptor() {
            return mock(OAuthAuthInterceptor.class);
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
    }
}
