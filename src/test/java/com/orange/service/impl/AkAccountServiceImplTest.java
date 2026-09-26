package com.orange.service.impl;

import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkPlayerInfoMapper;
import com.orange.mapper.AkOperatorStateMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
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
    void readsOperatorsUsingOwnerBindingWithoutClientScope() {
        when(bindingMapper.countBinding("ak-1", UID)).thenReturn(1);
        when(operatorMapper.selectByAkUid("ak-1")).thenReturn(Collections.emptyList());

        OperatorListVO result = service().listOperators(UID, "ak-1");

        assertThat(result.getAkUid()).isEqualTo("ak-1");
        assertThat(result.getItems()).isEmpty();
        verify(bindingMapper).countBinding("ak-1", UID);
    }

    private AkAccountServiceImpl service() {
        return new AkAccountServiceImpl(bindingMapper, playerInfoMapper, operatorMapper,
                saveExecutor, stringRedisTemplate);
    }
}
