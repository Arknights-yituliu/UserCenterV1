package com.orange.service.impl;

import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkDefaultSourceMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 游戏账号解绑写事务执行器测试：验证只删除绑定与默认偏好，不触碰共享数据
 *
 * @author UserCenter
 */
@ExtendWith(MockitoExtension.class)
class AkAccountUnbindExecutorTest {

    /** 测试用用户中心 UID */
    private static final Long UID = 10001L;

    /** 测试用游戏账号 UID */
    private static final String AK_UID = "ak-1";

    @Mock
    private AkAccountBindingMapper bindingMapper;

    @Mock
    private AkDefaultSourceMapper defaultSourceMapper;

    @InjectMocks
    private AkAccountUnbindExecutor executor;

    /**
     * 解绑只删除当前用户的绑定关系与指向该账号的默认偏好
     */
    @Test
    void removesBindingAndDefaultPreferenceOnly() {
        when(bindingMapper.deleteBinding(AK_UID, UID)).thenReturn(1);

        executor.unbind(UID, AK_UID);

        verify(bindingMapper).deleteBinding(AK_UID, UID);
        verify(defaultSourceMapper).deleteByTarget(UID, "skland", AK_UID);
    }
}
