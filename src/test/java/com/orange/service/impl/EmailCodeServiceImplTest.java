package com.orange.service.impl;

import com.orange.common.config.UserAccentProperties;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证共享 IP 的分钟、小时限流与独立邮箱发送间隔。 */
class EmailCodeServiceImplTest {

    private static final String IP = "127.0.0.1";
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private MailService mailService;
    private EmailCodeServiceImpl service;
    private Map<String, Long> counters;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        mailService = mock(MailService.class);
        counters = new HashMap<>();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenAnswer(invocation ->
                counters.merge(invocation.getArgument(0), 1L, Long::sum));
        service = new EmailCodeServiceImpl(redisTemplate, mailService,
                mock(UserInfoMapper.class), new UserAccentProperties());
    }

    @Test
    void sendsCodeWithIndependentMinuteAndHourlyIpWindows() {
        service.sendCode("user@example.com", "login", IP);

        verify(redisTemplate).expire(RedisKeyUtil.rate("send-code", IP), 60L, TimeUnit.SECONDS);
        verify(redisTemplate).expire(RedisKeyUtil.rate("send-code-hour", IP), 3600L, TimeUnit.SECONDS);
        verify(redisTemplate).expire(RedisKeyUtil.rate("send-code-email", "user@example.com"), 60L, TimeUnit.SECONDS);
        verify(valueOperations).set(eq(RedisKeyUtil.code("user@example.com")), anyString(),
                eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void tenDifferentEmailsCanSendButEleventhRequestIsLimitedAcrossUsages() {
        for (int index = 0; index < 10; index++) {
            service.sendCode("user-" + index + "@example.com", index % 2 == 0 ? "login" : "reset", IP);
        }

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendCode("eleventh@example.com", "register", IP));

        assertEquals(ResultCode.CODE_SEND_TOO_FREQUENT.getCode(), exception.getCode());
        verify(mailService, times(10)).sendText(anyString(), anyString(), anyString(), any());
        verify(redisTemplate, times(1)).expire(RedisKeyUtil.rate("send-code", IP), 60L, TimeUnit.SECONDS);
    }

    @Test
    void fiftyFirstHourlyRequestIsLimitedEvenWithNewMinuteQuota() {
        counters.put(RedisKeyUtil.rate("send-code-hour", IP), 49L);
        service.sendCode("fiftieth@example.com", "login", IP);
        counters.remove(RedisKeyUtil.rate("send-code", IP));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendCode("fifty-first@example.com", "login", IP));

        assertEquals(ResultCode.CODE_SEND_TOO_FREQUENT.getCode(), exception.getCode());
        verify(mailService, times(1)).sendText(anyString(), anyString(), anyString(), any());
    }

    @Test
    void sameEmailCannotResendFromAnotherIpWithinOneMinute() {
        service.sendCode("user@example.com", "login", IP);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendCode("user@example.com", "reset", "192.0.2.2"));

        assertEquals(ResultCode.CODE_SEND_TOO_FREQUENT.getCode(), exception.getCode());
        verify(mailService, times(1)).sendText(anyString(), anyString(), anyString(), any());
    }

    @Test
    void differentIpHasIndependentSendQuota() {
        counters.put(RedisKeyUtil.rate("send-code", IP), 10L);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.sendCode("user@example.com", "login", IP));
        assertEquals(ResultCode.CODE_SEND_TOO_FREQUENT.getCode(), exception.getCode());
        verifyNoInteractions(mailService);

        service.sendCode("user@example.com", "login", "192.0.2.2");

        verify(mailService).sendText(eq("user@example.com"), anyString(), anyString(), any());
    }
}
