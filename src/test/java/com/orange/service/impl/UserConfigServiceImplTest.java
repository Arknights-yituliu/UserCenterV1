package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.UserContext;
import com.orange.common.exception.BadRequestException;
import com.orange.common.exception.ConfigConflictException;
import com.orange.entity.dto.userconfig.UserConfigSaveRequest;
import com.orange.entity.po.UserConfig;
import com.orange.entity.po.UserConfigQuota;
import com.orange.entity.vo.UserConfigQuotaVO;
import com.orange.entity.vo.UserConfigSaveVO;
import com.orange.mapper.AuditLogMapper;
import com.orange.mapper.UserConfigMapper;
import com.orange.mapper.UserConfigQuotaMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserConfigServiceImplTest {

    private static final long UID = 1001L;
    private static final String CLIENT_ID = "client-a";

    @Mock
    private UserConfigMapper userConfigMapper;

    @Mock
    private UserConfigQuotaMapper quotaMapper;

    @Mock
    private AuditLogMapper auditLogMapper;

    private UserConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserConfigServiceImpl(
                userConfigMapper, quotaMapper, auditLogMapper, new ObjectMapper());
        UserContext.setClientId(CLIENT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createsConfigAndUpdatesQuota() {
        UserConfigQuota quota = quota(0, 512000);
        when(quotaMapper.selectForUpdate(UID)).thenReturn(quota);
        when(quotaMapper.updateById(quota)).thenReturn(1);
        when(userConfigMapper.insert(any(UserConfig.class))).thenAnswer(invocation -> {
            UserConfig config = invocation.getArgument(0);
            config.setId(88L);
            return 1;
        });

        UserConfigSaveRequest request = createRequest();
        UserConfigSaveVO result = service.saveConfig(UID, request);

        assertThat(result.getId()).isEqualTo(88L);
        assertThat(result.getHash())
                .isEqualTo("015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862");

        ArgumentCaptor<UserConfig> configCaptor = ArgumentCaptor.forClass(UserConfig.class);
        verify(userConfigMapper).insert(configCaptor.capture());
        assertThat(configCaptor.getValue().getConfig()).isEqualTo("{\"a\":1}");
        assertThat(configCaptor.getValue().getConfigBytes()).isEqualTo(7);
        assertThat(quota.getUsedBytes()).isEqualTo(7);
        verify(quotaMapper).initialize(UID, 512000);
    }

    @Test
    void saveIfMatchRejectsStaleHashAndKeepsQuotaUnchanged() {
        String currentHash = "a".repeat(64);
        UserConfig current = config(9L, currentHash, 10);
        UserConfigQuota quota = quota(10, 512000);
        when(quotaMapper.selectForUpdate(UID)).thenReturn(quota);
        when(userConfigMapper.selectOwnedByIdForUpdate(9L, UID, CLIENT_ID)).thenReturn(current);

        UserConfigSaveRequest request = updateRequest(9L, "b".repeat(64));

        assertThatThrownBy(() -> service.saveConfigIfMatch(UID, request))
                .isInstanceOfSatisfying(ConfigConflictException.class,
                        exception -> assertThat(exception.getCurrentHash()).isEqualTo(currentHash));
        assertThat(quota.getUsedBytes()).isEqualTo(10);
        verify(userConfigMapper, never()).updateIfHashMatches(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any());
        verify(quotaMapper, never()).updateById(any(UserConfigQuota.class));
    }

    @Test
    void saveIfMatchRejectsMissingExpectedHash() {
        UserConfigSaveRequest request = new UserConfigSaveRequest();
        request.setId(9L);
        request.setCategory("editor");
        request.setVersion("v1");
        request.setName("default");
        request.setConfig(Map.of("a", 1));

        assertThatThrownBy(() -> service.saveConfigIfMatch(UID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expectedHash");
        verify(quotaMapper, never()).initialize(any(), anyLong());
    }

    @Test
    void saveConfigOverwritesByIdWithoutHashCheck() {
        UserConfig current = config(9L, "old-hash-not-needed", 10);
        UserConfigQuota quota = quota(100, 512000);
        when(quotaMapper.selectForUpdate(UID)).thenReturn(quota);
        when(userConfigMapper.selectOwnedByIdForUpdate(9L, UID, CLIENT_ID)).thenReturn(current);
        when(userConfigMapper.updateOwnedById(
                any(), any(), any(), any(), any(), any(), any(), anyLong())).thenReturn(1);
        when(quotaMapper.updateById(quota)).thenReturn(1);

        // 覆盖更新：不携带 expectedHash 也应成功
        UserConfigSaveRequest request = updateRequest(9L, null);
        UserConfigSaveVO result = service.saveConfig(UID, request);

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getHash())
                .isEqualTo("015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862");
        assertThat(quota.getUsedBytes()).isEqualTo(97);
        verify(userConfigMapper).updateOwnedById(
                9L, UID, CLIENT_ID, null, null, "{\"a\":1}", result.getHash(), 7L);
    }

    @Test
    void saveIfMatchUpdatesWhenHashMatchesAndAppliesQuotaDelta() {
        String currentHash = "A".repeat(64);
        UserConfig current = config(9L, currentHash.toLowerCase(), 10);
        UserConfigQuota quota = quota(100, 512000);
        when(quotaMapper.selectForUpdate(UID)).thenReturn(quota);
        when(userConfigMapper.selectOwnedByIdForUpdate(9L, UID, CLIENT_ID)).thenReturn(current);
        when(userConfigMapper.updateIfHashMatches(
                any(), any(), any(), any(), any(), any(), any(), anyLong(), any())).thenReturn(1);
        when(quotaMapper.updateById(quota)).thenReturn(1);

        UserConfigSaveRequest request = updateRequest(9L, currentHash);
        UserConfigSaveVO result = service.saveConfigIfMatch(UID, request);

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getHash())
                .isEqualTo("015abd7f5cc57a2dd94b7590f04ad8084273905ee33ec5cebeae62276a97f862");
        assertThat(quota.getUsedBytes()).isEqualTo(97);
        verify(userConfigMapper).updateIfHashMatches(
                9L, UID, CLIENT_ID, null, null, "{\"a\":1}", result.getHash(), 7L,
                currentHash.toLowerCase());
    }

    @Test
    void distinguishesExplicitNullExpectedHashFromMissingField() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        UserConfigSaveRequest missing = objectMapper.readValue(
                "{\"category\":\"editor\",\"version\":\"v1\",\"name\":\"default\",\"config\":{}}",
                UserConfigSaveRequest.class);
        UserConfigSaveRequest explicitNull = objectMapper.readValue(
                "{\"category\":\"editor\",\"version\":\"v1\",\"name\":\"default\","
                        + "\"config\":{},\"expectedHash\":null}",
                UserConfigSaveRequest.class);

        assertThat(missing.isExpectedHashPresent()).isFalse();
        assertThat(explicitNull.isExpectedHashPresent()).isTrue();
        assertThat(explicitNull.getExpectedHash()).isNull();
    }

    @Test
    void returnsDefaultQuotaWithoutCreatingRow() {
        when(quotaMapper.selectById(UID)).thenReturn(null);

        UserConfigQuotaVO result = service.getQuota(UID);

        assertThat(result.getUsedBytes()).isZero();
        assertThat(result.getLimitBytes()).isEqualTo(512000);
        assertThat(result.getRemainingBytes()).isEqualTo(512000);
        verify(quotaMapper, never()).initialize(any(), anyLong());
    }

    @Test
    void physicalDeleteReleasesQuota() {
        UserConfig current = config(9L, "a".repeat(64), 100);
        UserConfigQuota quota = quota(250, 512000);
        when(userConfigMapper.selectOwnedById(9L, UID, CLIENT_ID)).thenReturn(current);
        when(quotaMapper.selectForUpdate(UID)).thenReturn(quota);
        when(userConfigMapper.selectOwnedByIdForUpdate(9L, UID, CLIENT_ID)).thenReturn(current);
        when(userConfigMapper.deleteOwnedById(9L, UID, CLIENT_ID)).thenReturn(1);
        when(quotaMapper.updateById(quota)).thenReturn(1);

        service.deleteConfig(UID, 9L);

        assertThat(quota.getUsedBytes()).isEqualTo(150);
        verify(userConfigMapper).deleteOwnedById(9L, UID, CLIENT_ID);
    }

    private UserConfigSaveRequest createRequest() {
        UserConfigSaveRequest request = new UserConfigSaveRequest();
        request.setCategory("editor");
        request.setVersion("v1");
        request.setName("default");
        request.setConfig(Map.of("a", 1));
        request.setExpectedHash(null);
        return request;
    }

    private UserConfigSaveRequest updateRequest(long id, String expectedHash) {
        UserConfigSaveRequest request = createRequest();
        request.setId(id);
        request.setExpectedHash(expectedHash);
        return request;
    }

    private UserConfig config(long id, String hash, long bytes) {
        UserConfig config = new UserConfig();
        config.setId(id);
        config.setUid(UID);
        config.setClientId(CLIENT_ID);
        config.setCategory("editor");
        config.setVersion("v1");
        config.setName("default");
        config.setContentHash(hash);
        config.setConfigBytes(bytes);
        return config;
    }

    private UserConfigQuota quota(long usedBytes, long limitBytes) {
        UserConfigQuota quota = new UserConfigQuota();
        quota.setUid(UID);
        quota.setUsedBytes(usedBytes);
        quota.setLimitBytes(limitBytes);
        return quota;
    }
}
