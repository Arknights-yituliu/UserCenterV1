package com.orange.service.impl;

import com.orange.common.exception.BusinessException;
import com.orange.entity.po.AkOperatorStateManualSummary;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorVO;
import com.orange.mapper.AkAccountBindingMapper;
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
    private AkPlayerInfoMapper playerInfoMapper;

    @Mock
    private AkOperatorStateMapper operatorMapper;

    @Mock
    private AkOperatorStateManualMapper manualMapper;

    @Mock
    private AkOperatorStateManualService manualOperatorService;

    @Mock
    private AkOperatorSaveExecutor saveExecutor;

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
    void detectsManualOperatorsByAkUidWhenSourceIsAbsent() {
        OperatorVO item = new OperatorVO();
        item.setId("char-1");
        when(manualOperatorService.listManualOperators(UID)).thenReturn(List.of(item));

        OperatorListVO result = service().listOperators(UID, String.valueOf(UID), null);

        assertThat(result.getItems()).containsExactly(item);
        verify(bindingMapper, never()).countBinding(anyString(), eq(UID));
    }

    @Test
    void fallsBackToAutomaticDataWhenAkUidEqualsUserUidWithoutManualRecords() {
        when(manualOperatorService.listManualOperators(UID)).thenReturn(Collections.emptyList());
        when(bindingMapper.countBinding(String.valueOf(UID), UID)).thenReturn(1);
        when(operatorMapper.selectByAkUid(String.valueOf(UID))).thenReturn(Collections.emptyList());

        OperatorListVO result = service().listOperators(UID, String.valueOf(UID), null);

        assertThat(result.getItems()).isEmpty();
        verify(bindingMapper).countBinding(String.valueOf(UID), UID);
    }

    private AkAccountServiceImpl service() {
        return new AkAccountServiceImpl(bindingMapper, playerInfoMapper, operatorMapper,
                manualMapper, manualOperatorService, saveExecutor, stringRedisTemplate);
    }
}
