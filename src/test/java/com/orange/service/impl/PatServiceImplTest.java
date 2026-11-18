package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.PatUtil;
import com.orange.entity.dto.pat.PatIssueRequest;
import com.orange.entity.po.TlcPat;
import com.orange.entity.vo.pat.PatIssueVO;
import com.orange.entity.vo.pat.PatVO;
import com.orange.mapper.TlcPatMapper;
import com.orange.service.OAuthTokenService;
import com.orange.service.PatPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        assertThat(saved.getSource()).isEqualTo("session");
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
        when(oauthTokenService.resolveSelfIssuedScope("ef-data.write"))
                .thenThrow(new BusinessException(ResultCode.OAUTH_SCOPE_INVALID, "scope 不能超出凭证已有权限"));

        assertThatThrownBy(() -> service.issue(UID, request("ci", 3600L, "ef-data.write")))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getCode())
                        .isEqualTo(ResultCode.OAUTH_SCOPE_INVALID.getCode()));
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issueRejectsPayloadThatIsNotObject() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        PatIssueRequest request = request("ci", 3600L, null);
        request.setPayload(List.of(1, 2));

        assertThatThrownBy(() -> service.issue(UID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("payload 必须是 JSON 对象");
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issueRejectsPayloadLargerThan4Kb() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        PatIssueRequest request = request("ci", 3600L, null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("blob", "x".repeat(5000));
        request.setPayload(payload);

        assertThatThrownBy(() -> service.issue(UID, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("4KB");
        verify(tlcPatMapper, never()).insert(any(TlcPat.class));
    }

    @Test
    void issueStoresEmptyPayloadAsNull() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        PatIssueRequest request = request("ci", 3600L, null);
        request.setPayload(Map.of());

        service.issue(UID, request);

        assertThat(captureInserted().getPayload()).isNull();
    }

    @Test
    void issueStoresPayloadVerbatim() {
        when(oauthTokenService.resolveSelfIssuedScope(null)).thenReturn(SCOPE);
        PatIssueRequest request = request("ci", 3600L, null);
        request.setPayload(Map.of("a", 1, "b", Map.of("c", true)));

        service.issue(UID, request);

        assertThat(captureInserted().getPayload()).contains("\"a\":1").contains("\"c\":true");
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
    void listParsesPayloadAndComputesRemainingSeconds() {
        TlcPat row = oldToken();
        row.setPayload("{\"a\":1}");
        row.setExpireTime(LocalDateTime.now().plusSeconds(120));
        when(tlcPatMapper.selectList(any())).thenReturn(List.of(row));

        List<PatVO> list = service.list(UID);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getPayload()).isEqualTo(Map.of("a", 1));
        assertThat(list.get(0).getExpiresInSeconds()).isBetween(100L, 120L);
        assertThat(list.get(0).getTokenPrefix()).isEqualTo(row.getTokenPrefix());
        assertThat(list.get(0).getToken()).isEqualTo(row.getToken());
    }

    @Test
    void listByClientAlsoReturnsPlaintext() {
        TlcPat row = oldToken();
        when(tlcPatMapper.selectList(any())).thenReturn(List.of(row));

        List<PatVO> list = service.listByClient(UID, "this_land_cloud");

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getToken()).isEqualTo(row.getToken());
        verify(oauthTokenService).requireDirectAuthEnabledClient("this_land_cloud");
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
        old.setSource("session");
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
        row.setPayload("{\"ak_uid\":\"123456789\"}");
        when(tlcPatMapper.selectOne(any())).thenReturn(row);

        PatPrincipal principal = service.resolve(PatUtil.generate());

        assertThat(principal.getUid()).isEqualTo(UID);
        assertThat(principal.getClientId()).isEqualTo("this_land_cloud");
        assertThat(principal.getScope()).isEqualTo(SCOPE);
        assertThat(principal.getPayload()).containsEntry("ak_uid", "123456789");
        assertThat(principal.isPrefixed()).isTrue();
    }

    @Test
    void resolveAcceptsLegacyTokenWithoutPrefix() {
        TlcPat row = oldToken();
        row.setSource("legacy");
        row.setPayload(null);
        when(tlcPatMapper.selectOne(any())).thenReturn(row);

        PatPrincipal principal = service.resolve("0123456789abcdef".repeat(4));

        assertThat(principal.isPrefixed()).isFalse();
        assertThat(principal.getPayload()).isEmpty();
    }

    @Test
    void resolveToleratesBrokenPayload() {
        TlcPat row = oldToken();
        row.setPayload("not-a-json");
        when(tlcPatMapper.selectOne(any())).thenReturn(row);

        assertThat(service.resolve(PatUtil.generate()).getPayload()).isEmpty();
    }

    @Test
    void resolveReturnsNullWhenTokenRowMissing() {
        when(tlcPatMapper.selectOne(any())).thenReturn(null);

        assertThat(service.resolve(PatUtil.generate())).isNull();
    }
}
