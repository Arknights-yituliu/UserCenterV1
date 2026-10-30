package com.orange.service.impl;

import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.akoperator.ManualOperatorSaveRequest;
import com.orange.entity.dto.akoperator.OperatorItemRequest;
import com.orange.entity.po.AkOperatorManual;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.entity.vo.akoperator.OperatorVO;
import com.orange.mapper.AkOperatorManualMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AkOperatorManualServiceImplTest {

    private static final long UID = 1001L;

    @Mock
    private AkOperatorManualMapper manualMapper;

    @Mock
    private AkOperatorManualSaveExecutor saveExecutor;

    @Test
    void listsManualOperatorsWithOwnedAndZeroDefaults() {
        AkOperatorManual row = new AkOperatorManual();
        row.setId(9L);
        row.setUid(UID);
        row.setOperatorId("char_002_amiya");
        row.setOwned(1);
        row.setRarity(null);
        when(manualMapper.selectByUid(UID)).thenReturn(Collections.singletonList(row));

        List<OperatorVO> result = service().listManualOperators(UID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo("char_002_amiya");
        assertThat(result.get(0).getRecordId()).isEqualTo(9L);
        assertThat(result.get(0).getOwned()).isEqualTo(1);
        assertThat(result.get(0).getRarity()).isZero();
        verify(manualMapper).selectByUid(UID);
    }

    @Test
    void normalizesMissingValuesToZeroBeforeSave() {
        when(saveExecutor.save(eq(UID), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new OperatorSaveResultVO(1, 0, 0));

        OperatorItemRequest item = new OperatorItemRequest();
        item.setId("char_002_amiya");
        item.setOwned(1);
        ManualOperatorSaveRequest request = new ManualOperatorSaveRequest();
        request.setOperators(Collections.singletonList(item));

        OperatorSaveResultVO result = service().saveManualOperators(UID, request);

        ArgumentCaptor<List<AkOperatorManual>> captor = ArgumentCaptor.forClass(List.class);
        verify(saveExecutor).save(eq(UID), captor.capture());
        AkOperatorManual record = captor.getValue().get(0);
        assertThat(record.getUid()).isEqualTo(UID);
        assertThat(record.getOperatorId()).isEqualTo("char_002_amiya");
        assertThat(record.getOwned()).isEqualTo(1);
        assertThat(record.getLevel()).isZero();
        assertThat(result.getCreatedCount()).isEqualTo(1);
    }

    @Test
    void rejectsDuplicateOperatorIdInSameRequest() {
        OperatorItemRequest first = new OperatorItemRequest();
        first.setId("char_002_amiya");
        OperatorItemRequest second = new OperatorItemRequest();
        second.setId("char_002_amiya");
        ManualOperatorSaveRequest request = new ManualOperatorSaveRequest();
        request.setOperators(List.of(first, second));

        assertThatThrownBy(() -> service().saveManualOperators(UID, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002);
    }

    private AkOperatorManualServiceImpl service() {
        return new AkOperatorManualServiceImpl(manualMapper, saveExecutor);
    }
}
