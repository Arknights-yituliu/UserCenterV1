package com.orange.service.impl;

import com.orange.common.config.UserAccentProperties;
import com.orange.common.util.RedisKeyUtil;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.MailService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证验证码有效期和 IP、邮箱发送间隔沿用原 YAML 策略。 */
class EmailCodeServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void sendsCodeWithFiveMinuteExpiryAndOneMinuteRateWindows() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        EmailCodeServiceImpl service = new EmailCodeServiceImpl(redisTemplate, mock(MailService.class),
                mock(UserInfoMapper.class), new UserAccentProperties());

        service.sendCode("user@example.com", "login", "127.0.0.1");

        verify(redisTemplate).expire(RedisKeyUtil.rate("send-code", "127.0.0.1"), 60L, TimeUnit.SECONDS);
        verify(redisTemplate).expire(RedisKeyUtil.rate("send-code-email", "user@example.com"), 60L, TimeUnit.SECONDS);
        verify(valueOperations).set(eq(RedisKeyUtil.code("user@example.com")), anyString(),
                eq(300L), eq(TimeUnit.SECONDS));
    }
}
