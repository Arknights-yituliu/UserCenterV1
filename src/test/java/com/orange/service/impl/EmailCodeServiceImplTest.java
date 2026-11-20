package com.orange.service.impl;

import com.orange.common.config.UserAccentProperties;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

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

    @Test
    void deletionCodeUsesSeparateKeyAndExplicitMailPurpose() {
        service.sendAccountDeletionCode(9L, "user@example.com", "app", IP);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(RedisKeyUtil.accountDeletionCode(9L)), stored.capture(),
                eq(300L), eq(TimeUnit.SECONDS));
        String encodedEmail = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("user@example.com".getBytes(StandardCharsets.UTF_8));
        String encodedClient = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("app".getBytes(StandardCharsets.UTF_8));
        assertTrue(stored.getValue().matches("0:" + encodedEmail + ":" + encodedClient + ":[0-9]{6}"));
        String code = stored.getValue().substring(stored.getValue().length() - 6);
        verify(mailService).sendText(eq("user@example.com"), eq("【TCL】账户注销验证码"),
                org.mockito.ArgumentMatchers.contains("注销验证码为 " + code), any());
        verify(valueOperations, never()).set(eq(RedisKeyUtil.code("user@example.com")), anyString(), any(Long.class), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verificationLoadsLuaResourceAndBindsEmailClientAndUid() {
        ArgumentCaptor<RedisScript<Long>> script = ArgumentCaptor.forClass(RedisScript.class);
        ArgumentCaptor<String> expected = ArgumentCaptor.forClass(String.class);
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(RedisKeyUtil.accountDeletionCode(9L))),
                anyString(), eq("5"))).thenReturn(1L);
        service.verifyAccountDeletionCode(9L, "user@example.com", "app", "123456");
        verify(redisTemplate).execute(script.capture(), eq(List.of(RedisKeyUtil.accountDeletionCode(9L))),
                expected.capture(), eq("5"));
        assertEquals(Long.class, script.getValue().getResultType());
        assertTrue(script.getValue().getScriptAsString().startsWith("-- 注销邮箱验证码"));
        String expectedBinding = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("user@example.com".getBytes(StandardCharsets.UTF_8)) + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString("app".getBytes(StandardCharsets.UTF_8))
                + ":123456";
        assertEquals(expectedBinding, expected.getValue());
        verify(valueOperations, never()).get(anyString());
    }

    @ParameterizedTest
    @CsvSource({"0,30002", "-1,30001", "-2,30001"})
    @SuppressWarnings("unchecked")
    void mapsLuaFailuresWithoutAcceptingCode(long result, int expectedCode) {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString(), anyString())).thenReturn(result);
        assertEquals(expectedCode, assertThrows(BusinessException.class,
                () -> service.verifyAccountDeletionCode(9L, "user@example.com", "app", "123456")).getCode());
    }

    @Test
    void missingLuaResultFailsClosed() {
        assertEquals(ResultCode.RATE_LIMITER_UNAVAILABLE.getCode(), assertThrows(BusinessException.class,
                () -> service.verifyAccountDeletionCode(9L, "user@example.com", "app", "123456")).getCode());
    }
}
