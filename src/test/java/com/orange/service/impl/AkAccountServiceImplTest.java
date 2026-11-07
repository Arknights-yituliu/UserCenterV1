package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.exception.BusinessException;
import com.orange.entity.po.AkAccountBinding;
import com.orange.entity.po.AkDefaultSource;
import com.orange.entity.po.AkOperatorState;
import com.orange.entity.po.AkOperatorStateManualSummary;
import com.orange.entity.po.AkPlayerInfo;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorVO;
import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkDefaultSourceMapper;
import com.orange.mapper.AkPlayerInfoMapper;
import com.orange.mapper.AkOperatorStateManualMapper;
import com.orange.mapper.AkOperatorStateMapper;
import com.orange.service.AkOperatorStateManualService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AkAccountServiceImplTest {

    private static final long UID = 1001L;

    @Mock
    private AkAccountBindingMapper bindingMapper;

    @Mock
    private AkDefaultSourceMapper defaultSourceMapper;

    @Mock
    private AkPlayerInfoMapper playerInfoMapper;

    @Mock
    private AkOperatorStateMapper operatorMapper;

    @Mock
    private com.orange.mapper.AkItemDataMapper itemDataMapper;

    @Mock
    private AkOperatorStateManualMapper manualMapper;

    @Mock
    private com.orange.mapper.AkSkinStateMapper skinMapper;

    @Mock
    private AkOperatorStateManualService manualOperatorService;

    @Mock
    private AkOperatorSaveExecutor saveExecutor;

    @Mock
    private AkAccountUnbindExecutor unbindExecutor;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void listsBindingsByOwnerWithoutClientScope() {
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(Collections.emptyList());

        assertThat(service().listBoundAccounts(UID)).isEmpty();

        verify(bindingMapper).selectByOwnerOrderByUpdateTime(UID);
    }

    @Test
    void appendsManualSourceEntryUsingUserUid() {
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(Collections.emptyList());
        AkOperatorStateManualSummary summary = new AkOperatorStateManualSummary();
        summary.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(summary);

        List<AkAccountVO> accounts = service().listBoundAccounts(UID);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getAkUid()).isEqualTo(String.valueOf(UID));
        assertThat(accounts.get(0).getSource()).isEqualTo(AkAccountVO.SOURCE_MANUAL);
        assertThat(accounts.get(0).getCreateTime()).isNull();
        assertThat(accounts.get(0).getUpdateTime()).isEqualTo(summary.getUpdateTime());
    }

    @Test
    void readsOperatorsUsingOwnerBindingWithoutClientScope() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(1);
        when(operatorMapper.selectByAkUid("ak-1")).thenReturn(Collections.emptyList());

        OperatorListVO result = service().listOperators(UID, "ak-1", null);

        assertThat(result.getAkUid()).isEqualTo("ak-1");
        assertThat(result.getItems()).isEmpty();
        verify(bindingMapper).countBinding("ak-1", UID);
    }

    @Test
    void rejectsItemsWhenAkUidIsNotBound() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(0);

        assertThatThrownBy(() -> service().listItems(UID, "ak-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 80008);
        verify(itemDataMapper, never()).selectByAkUid(anyString());
    }

    @Test
    void returnsEmptyItemsWhenAccountNeverUploadedItems() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(1);
        when(itemDataMapper.selectByAkUid("ak-1")).thenReturn(null);

        com.orange.entity.vo.akoperator.ItemListVO result = service().listItems(UID, "ak-1");

        assertThat(result.getAkUid()).isEqualTo("ak-1");
        assertThat(result.getItems()).isEmpty();
        assertThat(result.getUpdateTime()).isNull();
    }

    @Test
    void parsesItemsFromStoredJsonSnapshot() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(1);
        com.orange.entity.po.AkItemData data = new com.orange.entity.po.AkItemData();
        data.setAkUid("ak-1");
        data.setItemData("[{\"itemId\":\"3001\",\"count\":111},{\"itemId\":\"3003\",\"count\":5}]");
        data.setUpdateTime(LocalDateTime.of(2026, 11, 2, 12, 0));
        when(itemDataMapper.selectByAkUid("ak-1")).thenReturn(data);

        com.orange.entity.vo.akoperator.ItemListVO result = service().listItems(UID, "ak-1");

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getItemId()).isEqualTo("3001");
        assertThat(result.getItems().get(0).getCount()).isEqualTo(111L);
        assertThat(result.getItems().get(1).getItemId()).isEqualTo("3003");
        assertThat(result.getItems().get(1).getCount()).isEqualTo(5L);
        assertThat(result.getUpdateTime()).isEqualTo(data.getUpdateTime());
    }

    @Test
    void readsManualOperatorsWhenSourceIsManualAndAkUidEqualsUserUid() {
        OperatorVO item = new OperatorVO();
        item.setId("char-1");
        when(manualOperatorService.listManualOperators(UID)).thenReturn(List.of(item));

        OperatorListVO result = service().listOperators(UID, String.valueOf(UID), AkAccountVO.SOURCE_MANUAL);

        assertThat(result.getAkUid()).isEqualTo(String.valueOf(UID));
        assertThat(result.getItems()).containsExactly(item);
        verify(bindingMapper, never()).countBinding(anyString(), eq(UID));
    }

    @Test
    void rejectsManualSourceWhenAkUidIsNotCurrentUserUid() {
        assertThatThrownBy(() -> service().listOperators(UID, "ak-1", AkAccountVO.SOURCE_MANUAL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002);
        verify(manualOperatorService, never()).listManualOperators(UID);
    }

    @Test
    void detectsManualSourceByExistingRecordsEvenWhenAllUnowned() {
        AkOperatorStateManualSummary summary = new AkOperatorStateManualSummary();
        summary.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(summary);
        when(manualOperatorService.listManualOperators(UID)).thenReturn(Collections.emptyList());

        OperatorListVO result = service().listOperators(UID, String.valueOf(UID), null);

        assertThat(result.getItems()).isEmpty();
        verify(bindingMapper, never()).countBinding(anyString(), eq(UID));
    }

    @Test
    void fallsBackToAutomaticDataWhenAkUidEqualsUserUidWithoutManualRecords() {
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(null);
        when(bindingMapper.countBinding(String.valueOf(UID), UID)).thenReturn(1);
        when(operatorMapper.selectByAkUid(String.valueOf(UID))).thenReturn(Collections.emptyList());

        OperatorListVO result = service().listOperators(UID, String.valueOf(UID), null);

        assertThat(result.getItems()).isEmpty();
        verify(bindingMapper).countBinding(String.valueOf(UID), UID);
    }

    @Test
    void filtersOutUnownedOperatorsFromAutomaticData() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(1);
        AkOperatorState owned = new AkOperatorState();
        owned.setId(1L);
        owned.setAkUid("ak-1");
        owned.setOperatorId("char-1");
        owned.setOwned(1);
        AkOperatorState unowned = new AkOperatorState();
        unowned.setId(2L);
        unowned.setAkUid("ak-1");
        unowned.setOperatorId("char-2");
        unowned.setOwned(0);
        when(operatorMapper.selectByAkUid("ak-1")).thenReturn(List.of(owned, unowned));

        OperatorListVO result = service().listOperators(UID, "ak-1", null);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getId()).isEqualTo("char-1");
    }

    @Test
    void putsDefaultSourceFirstEvenWhenAnotherEntryChangedLater() {
        AkAccountBinding binding = new AkAccountBinding();
        binding.setAkUid("ak-1");
        binding.setOwnerUid(UID);
        binding.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        binding.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(List.of(binding));

        AkPlayerInfo info = new AkPlayerInfo();
        info.setAkUid("ak-1");
        when(playerInfoMapper.selectByAkUids(anyList())).thenReturn(List.of(info));

        AkOperatorStateManualSummary summary = new AkOperatorStateManualSummary();
        summary.setUpdateTime(LocalDateTime.of(2026, 1, 3, 0, 0));
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(summary);

        AkDefaultSource preference = new AkDefaultSource();
        preference.setUid(UID);
        preference.setSource(AkAccountVO.SOURCE_SKLAND);
        preference.setAkUid("ak-1");
        when(defaultSourceMapper.selectByUid(UID)).thenReturn(preference);

        List<AkAccountVO> accounts = service().listBoundAccounts(UID);

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).getAkUid()).isEqualTo("ak-1");
        assertThat(accounts.get(0).isDefaultFlag()).isTrue();
        assertThat(accounts.get(1).getAkUid()).isEqualTo(String.valueOf(UID));
        assertThat(accounts.get(1).isDefaultFlag()).isFalse();
    }

    @Test
    void leavesNoDefaultFlagWhenPreferenceTargetIsMissing() {
        AkOperatorStateManualSummary summary = new AkOperatorStateManualSummary();
        summary.setUpdateTime(LocalDateTime.of(2026, 1, 3, 0, 0));
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(summary);

        AkDefaultSource preference = new AkDefaultSource();
        preference.setUid(UID);
        preference.setSource(AkAccountVO.SOURCE_SKLAND);
        preference.setAkUid("ak-missing");
        when(defaultSourceMapper.selectByUid(UID)).thenReturn(preference);

        List<AkAccountVO> accounts = service().listBoundAccounts(UID);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).isDefaultFlag()).isFalse();
    }

    @Test
    void readsDefaultSourceWhenAkUidIsAbsent() {
        AkDefaultSource preference = new AkDefaultSource();
        preference.setUid(UID);
        preference.setSource(AkAccountVO.SOURCE_MANUAL);
        preference.setAkUid(String.valueOf(UID));
        when(defaultSourceMapper.selectByUid(UID)).thenReturn(preference);
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(Collections.emptyList());

        AkOperatorStateManualSummary summary = new AkOperatorStateManualSummary();
        summary.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(summary);

        OperatorVO item = new OperatorVO();
        item.setId("char-1");
        when(manualOperatorService.listManualOperators(UID)).thenReturn(List.of(item));

        OperatorListVO result = service().listOperators(UID, null, null);

        assertThat(result.getAkUid()).isEqualTo(String.valueOf(UID));
        assertThat(result.getItems()).containsExactly(item);
    }

    @Test
    void rejectsSourceWhenAkUidIsAbsent() {
        assertThatThrownBy(() -> service().listOperators(UID, null, AkAccountVO.SOURCE_MANUAL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002);
        verify(bindingMapper, never()).selectByOwnerOrderByUpdateTime(UID);
    }

    @Test
    void rejectsDefaultReadWhenUserHasNoSource() {
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service().listOperators(UID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002);
    }

    @Test
    void setsSklandDefaultAfterOwnershipCheck() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(1);

        service().setDefaultSource(UID, "ak-1", AkAccountVO.SOURCE_SKLAND);

        verify(defaultSourceMapper).upsert(UID, AkAccountVO.SOURCE_SKLAND, "ak-1");
    }

    @Test
    void rejectsSklandDefaultWhenAccountNotBound() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(0);

        assertThatThrownBy(() -> service().setDefaultSource(UID, "ak-1", AkAccountVO.SOURCE_SKLAND))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 80008);
        verify(defaultSourceMapper, never()).upsert(eq(UID), anyString(), anyString());
    }

    @Test
    void setsManualDefaultWhenManualDataExists() {
        AkOperatorStateManualSummary summary = new AkOperatorStateManualSummary();
        summary.setUpdateTime(LocalDateTime.of(2026, 1, 2, 0, 0));
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(summary);

        service().setDefaultSource(UID, String.valueOf(UID), AkAccountVO.SOURCE_MANUAL);

        verify(defaultSourceMapper).upsert(UID, AkAccountVO.SOURCE_MANUAL, String.valueOf(UID));
    }

    @Test
    void rejectsManualDefaultWhenManualDataMissing() {
        when(manualMapper.selectSummaryByUid(UID)).thenReturn(null);

        assertThatThrownBy(() -> service().setDefaultSource(UID, String.valueOf(UID), AkAccountVO.SOURCE_MANUAL))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002);
        verify(defaultSourceMapper, never()).upsert(eq(UID), anyString(), anyString());
    }

    @Test
    void rejectsUnbindWhenAccountNotBound() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(0);

        assertThatThrownBy(() -> service().unbindAccount(UID, "ak-1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 80008);
        verify(unbindExecutor, never()).unbind(UID, "ak-1");
    }

    @Test
    void unbindsAccountWhenBound() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(1);

        service().unbindAccount(UID, "ak-1");

        verify(unbindExecutor).unbind(UID, "ak-1");
    }

    private AkAccountServiceImpl service() {
        return new AkAccountServiceImpl(bindingMapper, defaultSourceMapper, playerInfoMapper, operatorMapper,
                itemDataMapper, skinMapper, manualMapper, manualOperatorService, saveExecutor, unbindExecutor,
                stringRedisTemplate, new ObjectMapper());
    }
}
