package com.orange.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.PatUtil;
import com.orange.entity.dto.pat.PatIssueByRefreshRequest;
import com.orange.entity.dto.pat.PatIssueRequest;
import com.orange.entity.dto.pat.PatPayload;
import com.orange.entity.po.TlcPat;
import com.orange.entity.vo.pat.PatIssueVO;
import com.orange.entity.vo.pat.PatVO;
import com.orange.mapper.TlcPatMapper;
import com.orange.service.OAuthTokenService;
import com.orange.service.PatPrincipal;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PatServiceImpl} 的单元测试：签发校验、明文存储/回看、payload 口径、撤销语义
 *
 * @author UserCenter
 */
@ExtendWith(MockitoExtension.class)
class PatServiceImplTest {

    private static final long UID = 7001L;
    private static final String SCOPE = "user.read,config.read";

    @Mock
    private TlcPatMapper tlcPatMapper;

    @Mock
    private OAuthTokenService oauthTokenService;

    private PatServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TlcPat.class);
        service = new PatServiceImpl(tlcPatMapper, oauthTokenService, objectMapper);
    }

    @Test
    void issueStoresPlaintextTokenVerbatim() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        when(tlcPatMapper.insert(any(TlcPat.class))).thenAnswer(invocation -> {
            TlcPat pat = invocation.getArgument(0);
            pat.setId(11L);
            pat.setCreateTime(LocalDateTime.now());
            return 1;
        });

        PatIssueVO vo = service.issue(UID, request("ci-token", 3600L, null));

        assertThat(vo.getToken()).hasSize(72).startsWith("tlc_pat_");
        assertThat(vo.getTokenPrefix()).isEqualTo(vo.getToken().substring(0, 12));
        assertThat(vo.getScope()).isEqualTo(SCOPE);
        assertThat(vo.getClientId()).isEqualTo("this_land_cloud");

        TlcPat saved = captureInserted();
        assertThat(saved.getToken()).isEqualTo(vo.getToken());
        assertThat(saved.getTokenPrefix()).isEqualTo(vo.getTokenPrefix());
        assertThat(saved.getPayload()).isNull();
        assertThat(saved.getExpireTime()).isAfter(LocalDateTime.now().plusSeconds(3500));
    }

    @Test
    void issueRejectsMissingName() {
        assertThatThrownBy(() -> service.issue(UID, request("  ", 3600L, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.PARAM_ERROR.getCode()));
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issueRejectsMissingOrNonPositiveExpiry() {
        assertThatThrownBy(() -> service.issue(UID, request("ci", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expires_in");
        assertThatThrownBy(() -> service.issue(UID, request("ci", 0L, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expires_in");
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issueRejectsExpiryBeyondTenYears() {
        long tooLong = (365L * 10 + 32) * 24 * 60 * 60;

        assertThatThrownBy(() -> service.issue(UID, request("ci", tooLong, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expires_in 不能超过");
    }

    @Test
    void issueRejectsNonPersonalClientId() {
        assertThatThrownBy(() -> service.issue(UID, requestWithClientId("ci", 3600L, "some-app")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("client_id 固定");
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issuePropagatesScopeEscalation() {
        when(oauthTokenService.resolveSelfIssuedScope("endfield-data.write"))
                .thenThrow(new BusinessException(ResultCode.OAUTH_SCOPE_INVALID, "scope 不能超出凭证已有权限"));

        assertThatThrownBy(() -> service.issue(UID, request("ci", 3600L, "endfield-data.write")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.OAUTH_SCOPE_INVALID.getCode()));
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issueRejectsPayloadLargerThan4Kb() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        PatIssueRequest request = request("ci", 3600L, null);
        request.setPayload(payload("x".repeat(5000)));

        assertThatThrownBy(() -> service.issue(UID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("4KB");
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issueStoresEmptyPayloadAsNull() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        PatIssueRequest request = request("ci", 3600L, null);
        request.setPayload(new PatPayload());

        service.issue(UID, request);

        assertThat(captureInserted().getPayload()).isNull();
    }

    @Test
    void issueStoresPayloadAsJsonTextAndReturnsEntity() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        PatIssueRequest request = request("ci", 3600L, null);
        request.setPayload(payload("123456789"));
        request.getPayload().setServerId("1");

        PatIssueVO vo = service.issue(UID, request);

        assertThat(captureInserted().getPayload()).contains("\"game_uid\":\"123456789\"", "\"server_id\":\"1\"");
        assertThat(vo.getPayload()).isInstanceOf(PatPayload.class);
        assertThat(vo.getPayload().getGameUid()).isEqualTo("123456789");
        assertThat(vo.getPayload().getServerId()).isEqualTo("1");
    }

    @Test
    void revokeIsIdempotentWhenNothingDeleted() {
        when(tlcPatMapper.delete(any())).thenReturn(0);

        service.revoke(UID, 99L);

        verify(tlcPatMapper).delete(any());
    }

    @Test
    void revokeAllDeletesOnlyOwnRows() {
        when(tlcPatMapper.delete(any())).thenReturn(4);

        service.revokeAllByUid(UID);

        verify(tlcPatMapper).delete(any());
    }

    @Test
    void revokeByClientDeletesOwnTokenWithoutRestrictingIssuingApplication() {
        String token = PatUtil.generate();
        when(tlcPatMapper.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<TlcPat> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("token =", "uid =")
                    .doesNotContain("client_id");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(token, UID);
            return 1;
        });

        service.revokeByClient(UID, "app-alpha", token);

        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
        verify(tlcPatMapper).delete(any());
    }

    @Test
    void revokeByClientRejectsNonDirectApplicationBeforeDeletingTokens() {
        doThrow(new BusinessException(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED))
                .when(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");

        assertThatThrownBy(() -> service.revokeByClient(UID, "app-alpha", PatUtil.generate()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(90014));
        verify(tlcPatMapper, never()).delete(any());
    }

    @Test
    void revokeByClientRejectsMissingIdentityOrTokenBeforeDeleting() {
        assertThatThrownBy(() -> service.revokeByClient(null, "app-alpha", "token"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.revokeByClient(UID, null, "token"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.revokeByClient(UID, " ", "token"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.revokeByClient(UID, "app-alpha", null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.revokeByClient(UID, "app-alpha", " "))
                .isInstanceOf(BusinessException.class);
        verify(tlcPatMapper, never()).delete(any());
    }

    @Test
    void revokeByClientIsIdempotentWhenNothingDeleted() {
        when(tlcPatMapper.delete(any())).thenReturn(0);

        service.revokeByClient(UID, "app-alpha", PatUtil.generate());

        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
        verify(tlcPatMapper).delete(any());
    }

    @Test
    void revokeByClientIdDeletesOnlyCurrentUsersRecordAcrossApplications() {
        when(tlcPatMapper.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<TlcPat> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("id =", "uid =").doesNotContain("client_id");
            assertThat(query.getParamNameValuePairs().values()).containsExactlyInAnyOrder(21L, UID);
            return 1;
        });

        service.revokeByClientId(UID, "app-alpha", 21L);

        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
        verify(tlcPatMapper).delete(any());
    }

    @Test
    void revokeAllByClientDeletesOnlyCurrentUsersTokensAcrossApplications() {
        when(tlcPatMapper.delete(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<TlcPat> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("uid =").doesNotContain("client_id", "token =");
            assertThat(query.getParamNameValuePairs().values()).containsExactly(UID);
            return 3;
        });

        service.revokeAllByClient(UID, "app-alpha");

        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
        verify(tlcPatMapper).delete(any());
    }

    @Test
    void listParsesPayloadAndComputesRemainingSeconds() {
        TlcPat row = oldToken();
        row.setPayload("{\"game_uid\":\"123456789\",\"server_id\":\"1\"}");
        row.setExpireTime(LocalDateTime.now().plusSeconds(120));
        when(tlcPatMapper.selectList(any())).thenReturn(List.of(row));

        List<PatVO> list = service.list(UID);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getPayload().getGameUid()).isEqualTo("123456789");
        assertThat(list.get(0).getPayload().getServerId()).isEqualTo("1");
        assertThat(list.get(0).getExpiresInSeconds()).isBetween(100L, 120L);
        assertThat(list.get(0).getTokenPrefix()).isEqualTo(row.getTokenPrefix());
        assertThat(list.get(0).getToken()).isEqualTo(row.getToken());
    }

    @Test
    void listIgnoresUnknownPayloadFieldsAndLeavesMissingFieldsNull() throws Exception {
        TlcPat row = oldToken();
        // 历史行里的任意 KV 与未来版本新增的键都不得让列表解析失败
        row.setPayload("{\"game_uid\":123456789,\"game_name\":\"明日方舟\","
                + "\"keep\":\"me\",\"future_field\":{\"a\":1}}");
        when(tlcPatMapper.selectList(any())).thenReturn(List.of(row));

        PatPayload payload = service.list(UID).get(0).getPayload();

        assertThat(payload).isNotNull();
        assertThat(payload.getGameUid()).isEqualTo("123456789");
        assertThat(payload.getGameName()).isEqualTo("明日方舟");
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(payload));
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("game_uid").asText()).isEqualTo("123456789");
        assertThat(json.get("game_name").asText()).isEqualTo("明日方舟");
    }

    @Test
    void listTreatsAllUnknownPayloadAsAbsent() {
        TlcPat row = oldToken();
        row.setPayload("{\"keep\":\"me\"}");
        when(tlcPatMapper.selectList(any())).thenReturn(List.of(row));

        assertThat(service.list(UID).get(0).getPayload()).isNull();
    }

    @Test
    void listByClientReturnsOwnTokensAcrossApplicationsIncludingExpiredTokens() {
        TlcPat personal = oldToken();
        TlcPat otherApp = oldToken();
        otherApp.setId(22L);
        otherApp.setClientId("app-beta");
        otherApp.setToken("other-app-token");
        otherApp.setExpireTime(LocalDateTime.now().minusSeconds(60));
        when(tlcPatMapper.selectList(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<TlcPat> query = invocation.getArgument(0);
            assertThat(query.getSqlSegment()).contains("uid =", "ORDER BY create_time DESC,id DESC")
                    .doesNotContain("client_id", "expire_time");
            assertThat(query.getParamNameValuePairs().values()).containsExactly(UID);
            return List.of(personal, otherApp);
        });

        List<PatVO> list = service.listByClient(UID, "app-alpha");

        assertThat(list).extracting(PatVO::getClientId).containsExactly("this_land_cloud", "app-beta");
        assertThat(list).extracting(PatVO::getToken).containsExactly(personal.getToken(), otherApp.getToken());
        assertThat(list.get(1).getExpiresInSeconds()).isNegative();
        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
    }

    @Test
    void listByClientRejectsNonDirectApplicationBeforeQueryingTokens() {
        doThrow(new BusinessException(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED))
                .when(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");

        assertThatThrownBy(() -> service.listByClient(UID, "app-alpha"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode()).isEqualTo(90014));
        verify(tlcPatMapper, never()).selectList(any());
    }

    @Test
    void listByClientDoesNotQueryTokensWithoutUserOrApplicationIdentity() {
        assertThatThrownBy(() -> service.listByClient(null, "app-alpha"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.listByClient(UID, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.listByClient(UID, " "))
                .isInstanceOf(BusinessException.class);
        verify(tlcPatMapper, never()).selectList(any());
    }


    @Test
    void issueByRefreshTokenUsesCredentialIdentityAndSelfIssuedScope() {
        when(oauthTokenService.resolveRefreshToken("refresh-raw"))
                .thenReturn(refreshPrincipal("app-alpha", "user.read,config.read"));
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        when(tlcPatMapper.insert(any(TlcPat.class))).thenAnswer(invocation -> {
            TlcPat pat = invocation.getArgument(0);
            pat.setId(31L);
            pat.setCreateTime(LocalDateTime.now());
            return 1;
        });

        PatIssueByRefreshRequest request = refreshRequest("refresh-raw", "ci-token", 3600L, null);
        request.setPayload(payload("123456789"));
        request.getPayload().setServerId("1");
        PatIssueVO vo = service.issueByRefreshToken(request);

        assertThat(vo.getClientId()).isEqualTo("app-alpha");
        assertThat(vo.getScope()).isEqualTo(SCOPE);
        assertThat(vo.getToken()).hasSize(72).startsWith("tlc_pat_");
        assertThat(vo.getPayload()).isInstanceOf(PatPayload.class);
        assertThat(vo.getPayload().getGameUid()).isEqualTo("123456789");
        assertThat(vo.getPayload().getServerId()).isEqualTo("1");
        TlcPat saved = captureInserted();
        assertThat(saved.getUid()).isEqualTo(UID);
        assertThat(saved.getClientId()).isEqualTo("app-alpha");
        assertThat(saved.getToken()).isEqualTo(vo.getToken());
        assertThat(saved.getPayload()).contains("game_uid", "\"server_id\":\"1\"");
        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
        verify(oauthTokenService, never()).resolveSelfIssuedScopeWithin(any(), any());
    }

    @Test
    void issueByRefreshTokenCanSelectUserSideScopesBeyondCredentialScope() {
        when(oauthTokenService.resolveRefreshToken("refresh-raw"))
                .thenReturn(refreshPrincipal("app-alpha", "user.read"));
        when(oauthTokenService.resolveSelfIssuedScope("endfield-data.write"))
                .thenReturn("endfield-data.write");

        PatIssueVO vo = service.issueByRefreshToken(
                refreshRequest("refresh-raw", "ci", 3600L, "endfield-data.write"));

        assertThat(vo.getScope()).isEqualTo("endfield-data.write");
        assertThat(captureInserted().getScope()).isEqualTo("endfield-data.write");
        verify(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");
        verify(oauthTokenService, never()).resolveSelfIssuedScopeWithin(any(), any());
    }

    @Test
    void issueByRefreshTokenRejectsInvalidRefreshToken() {
        when(oauthTokenService.resolveRefreshToken("bad"))
                .thenThrow(new BusinessException(ResultCode.OAUTH_TOKEN_INVALID));

        assertThatThrownBy(() -> service.issueByRefreshToken(refreshRequest("bad", "ci", 3600L, null)))
                .isInstanceOf(BusinessException.class);
        verify(tlcPatMapper, never()).insert(any());
    }

    @Test
    void issueByRefreshTokenRequiresDirectAuthEnabledClient() {
        when(oauthTokenService.resolveRefreshToken("refresh-raw"))
                .thenReturn(refreshPrincipal("app-alpha", SCOPE));
        doThrow(new BusinessException(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED))
                .when(oauthTokenService).requireDirectAuthEnabledClient("app-alpha");

        assertThatThrownBy(() -> service.issueByRefreshToken(
                refreshRequest("refresh-raw", "ci", 3600L, null)))
                .isInstanceOf(BusinessException.class);
        verify(tlcPatMapper, never()).insert(any());
    }

    @Test
    void issueByRefreshTokenRejectsScopesOutsideSelfIssuedScopes() {
        when(oauthTokenService.resolveRefreshToken("refresh-raw"))
                .thenReturn(refreshPrincipal("app-alpha", "user.read"));
        when(oauthTokenService.resolveSelfIssuedScope("all"))
                .thenThrow(new BusinessException(ResultCode.OAUTH_SCOPE_INVALID));

        assertThatThrownBy(() -> service.issueByRefreshToken(
                refreshRequest("refresh-raw", "ci", 3600L, "all")))
                .isInstanceOf(BusinessException.class);
        verify(tlcPatMapper, never()).insert(any());
    }

    @Test
    void issueByRefreshTokenValidatesIssueParams() {
        when(oauthTokenService.resolveRefreshToken("refresh-raw"))
                .thenReturn(refreshPrincipal("app-alpha", SCOPE));

        assertThatThrownBy(() -> service.issueByRefreshToken(
                refreshRequest("refresh-raw", "  ", 3600L, null)))
                .isInstanceOf(BusinessException.class);
        verify(tlcPatMapper, never()).insert(any());
    }

    @Test
    void issueByRefreshTokenRejectsNullRequest() {
        assertThatThrownBy(() -> service.issueByRefreshToken(null))
                .isInstanceOf(BusinessException.class);
        verify(tlcPatMapper, never()).insert(any());
    }

    private OAuthTokenService.RefreshTokenPrincipal refreshPrincipal(String clientId, String scope) {
        return new OAuthTokenService.RefreshTokenPrincipal(UID, clientId, scope, "family-1");
    }

    private PatPayload payload(String gameUid) {
        PatPayload payload = new PatPayload();
        payload.setGameUid(gameUid);
        return payload;
    }

    private PatIssueByRefreshRequest refreshRequest(String refreshToken, String name,
                                                     Long expiresIn, String scope) {
        PatIssueByRefreshRequest request = new PatIssueByRefreshRequest();
        request.setRefreshToken(refreshToken);
        request.setName(name);
        request.setExpiresIn(expiresIn);
        request.setScope(scope);
        return request;
    }

    private PatIssueRequest request(String name, Long expiresIn, String scope) {
        PatIssueRequest request = new PatIssueRequest();
        request.setName(name);
        request.setExpiresIn(expiresIn);
        request.setScope(scope);
        return request;
    }

    private PatIssueRequest requestWithClientId(String name, Long expiresIn, String clientId) {
        PatIssueRequest request = request(name, expiresIn, null);
        request.setClientId(clientId);
        return request;
    }

    private TlcPat oldToken() {
        TlcPat old = new TlcPat();
        old.setId(21L);
        old.setUid(UID);
        old.setClientId("this_land_cloud");
        old.setName("old-name");
        old.setToken("old-token");
        old.setTokenPrefix("tlc_pat_old");
        old.setScope(SCOPE);
        old.setExpiresIn(86400L);
        old.setExpireTime(LocalDateTime.now().plusSeconds(86400));
        old.setPayload("{\"keep\":\"me\"}");
        return old;
    }

    private TlcPat captureInserted() {
        ArgumentCaptor<TlcPat> captor = ArgumentCaptor.forClass(TlcPat.class);
        verify(tlcPatMapper).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void resolveRejectsMalformedTokenWithoutQuery() {
        assertThat(service.resolve("not-a-pat")).isNull();
        assertThat(service.resolve(null)).isNull();
        verify(tlcPatMapper, never()).selectOne(any());
    }

    @Test
    void resolveReturnsPrincipalWithParsedPayload() {
        TlcPat row = oldToken();
        row.setPayload("{\"game_uid\":\"123456789\",\"server_id\":\"1\"}");
        when(tlcPatMapper.selectOne(any())).thenReturn(row);

        PatPrincipal principal = service.resolve(PatUtil.generate());

        assertThat(principal.getUid()).isEqualTo(UID);
        assertThat(principal.getClientId()).isEqualTo("this_land_cloud");
        assertThat(principal.getScope()).isEqualTo(SCOPE);
        assertThat(principal.getPayload().getGameUid()).isEqualTo("123456789");
        assertThat(principal.getPayload().getServerId()).isEqualTo("1");
        assertThat(principal.isPrefixed()).isTrue();
    }

    @Test
    void resolveIgnoresUnknownPayloadFields() {
        TlcPat row = oldToken();
        row.setPayload("{\"game_uid\":\"123456789\",\"legacy_blob\":{\"x\":1},\"future_flag\":true}");
        when(tlcPatMapper.selectOne(any())).thenReturn(row);

        PatPrincipal principal = service.resolve(PatUtil.generate());

        assertThat(principal.getPayload().getGameUid()).isEqualTo("123456789");
        assertThat(principal.getPayload().isEmpty()).isFalse();
    }

    @Test
    void resolveAcceptsLegacyTokenWithoutPrefix() {
        TlcPat row = oldToken();
        row.setPayload(null);
        when(tlcPatMapper.selectOne(any())).thenReturn(row);

        PatPrincipal principal = service.resolve("0123456789abcdef".repeat(4));

        assertThat(principal.isPrefixed()).isFalse();
        assertThat(principal.getPayload()).isNotNull();
        assertThat(principal.getPayload().isEmpty()).isTrue();
    }

    @Test
    void resolveToleratesBrokenPayload() {
        TlcPat row = oldToken();
        row.setPayload("not-a-json");
        when(tlcPatMapper.selectOne(any())).thenReturn(row);

        assertThat(service.resolve(PatUtil.generate()).getPayload().isEmpty()).isTrue();
    }

    @Test
    void resolveReturnsNullWhenTokenRowMissing() {
        when(tlcPatMapper.selectOne(any())).thenReturn(null);

        assertThat(service.resolve(PatUtil.generate())).isNull();
    }
}
