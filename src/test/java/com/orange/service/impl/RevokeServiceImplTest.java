package com.orange.service.impl;

import com.orange.common.util.RedisKeyUtil;
import com.orange.mapper.OAuthGrantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RevokeServiceImpl 单元测试：验证整体吊销用户令牌时同步维护授权台账
 *
 * @author UserCenter
 */
@ExtendWith(MockitoExtension.class)
class RevokeServiceImplTest {

    private static final long UID = 1001L;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private OAuthGrantMapper oauthGrantMapper;

    private RevokeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RevokeServiceImpl(redisTemplate, oauthGrantMapper);
    }

    /**
     * 吊销用户全部 OAuth 令牌后，必须把台账按 uid 置为已吊销并清理反向索引
     */
    @Test
    void revokeUserTokensDeletesTokensAndMarksGrantsRevoked() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        String indexKey = RedisKeyUtil.uidOauth(UID);
        when(setOperations.members(indexKey))
                .thenReturn(Set.of("access:access-1", "refresh:refresh-1"));

        service.revokeUserTokens(UID);

        verify(redisTemplate).delete(RedisKeyUtil.oauthAccess("access-1"));
        verify(redisTemplate).delete(RedisKeyUtil.oauthRefresh("refresh-1"));
        verify(oauthGrantMapper).markRevokedByUid(UID);
        verify(redisTemplate).delete(indexKey);
    }
}
