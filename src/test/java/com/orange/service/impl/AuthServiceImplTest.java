package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.config.OAuthDirectProperties;
import com.orange.common.config.UserAccentProperties;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.GlobalExceptionHandler;
import com.orange.common.util.Result;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.po.UserInfo;
import com.orange.mapper.LoginLogMapper;
import com.orange.mapper.OAuthClientMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.EmailCodeService;
import com.orange.service.OAuthTokenService;
import com.orange.service.RevokeService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证 Java 配置中的会话有效期、登录锁定与限流策略在业务调用中生效。 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String IP = "127.0.0.1";
    private static final String ACCOUNT = "user@example.com";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private SetOperations<String, String> setOperations;
    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private EmailCodeService emailCodeService;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new AuthServiceImpl(userInfoMapper, mock(LoginLogMapper.class), mock(OAuthClientMapper.class),
                emailCodeService, mock(RevokeService.class), mock(OAuthTokenService.class), redisTemplate,
                new ObjectMapper().findAndRegisterModules(), mock(Validator.class), new UserAccentProperties(),
                new OAuthDirectProperties());
    }

    @Test
    void sessionExpiresAfterNinetyDays() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        String token = service.createSession(9L, "client-1");

        verify(valueOperations).set(eq(RedisKeyUtil.token(token)), anyString(),
                eq(7776000L), eq(TimeUnit.SECONDS));
        verify(setOperations).add(RedisKeyUtil.uidSession(9L), token);
    }

    @Test
    void twentyFirstLoginAttemptIsRejectedBeforeCheckingCredentials() {
        when(valueOperations.increment(RedisKeyUtil.rate("login-ip", IP))).thenReturn(21L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(passwordRequest(), IP, "test", "client-1"));

        assertEquals(ResultCode.IP_RATE_LIMITED.getCode(), exception.getCode());
        verifyNoInteractions(userInfoMapper, emailCodeService);
    }

    @Test
    void firstLoginFailureUsesSeparateRequestAndFailureWindows() {
        when(valueOperations.increment(RedisKeyUtil.rate("login-ip", IP))).thenReturn(1L);
        when(valueOperations.increment(RedisKeyUtil.loginFail(ACCOUNT))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(passwordRequest(), IP, "test", "client-1"));

        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        verify(redisTemplate).expire(RedisKeyUtil.rate("login-ip", IP), 60L, TimeUnit.SECONDS);
        verify(redisTemplate).expire(RedisKeyUtil.loginFail(ACCOUNT), 900L, TimeUnit.SECONDS);
    }

    @Test
    void fifthLoginFailureLocksAccountForThirtyMinutes() {
        when(valueOperations.increment(RedisKeyUtil.rate("login-ip", IP))).thenReturn(2L);
        when(valueOperations.increment(RedisKeyUtil.loginFail(ACCOUNT))).thenReturn(5L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(passwordRequest(), IP, "test", "client-1"));

        assertEquals(ResultCode.LOGIN_LOCKED.getCode(), exception.getCode());
        verify(valueOperations).set(RedisKeyUtil.loginLock(ACCOUNT), "1", 1800L, TimeUnit.SECONDS);
        verify(redisTemplate).delete(RedisKeyUtil.loginFail(ACCOUNT));
    }

    @Test
    void fifthEmailCodeFailureUsesTheSameThirtyMinuteAccountLock() {
        when(valueOperations.increment(RedisKeyUtil.rate("login-ip", IP))).thenReturn(1L);
        when(valueOperations.increment(RedisKeyUtil.loginFail(ACCOUNT))).thenReturn(5L);
        doThrow(new BusinessException(ResultCode.CODE_ERROR)).when(emailCodeService).verifyCode(ACCOUNT, "wrong");
        LoginRequest request = new LoginRequest();
        request.setAccountType("email");
        request.setEmail(ACCOUNT);
        request.setVerificationCode("wrong");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(request, IP, "test", "client-1"));

        assertEquals(ResultCode.LOGIN_LOCKED.getCode(), exception.getCode());
        verify(valueOperations).set(RedisKeyUtil.loginLock(ACCOUNT), "1", 1800L, TimeUnit.SECONDS);
        verifyNoInteractions(userInfoMapper);
    }

    @Test
    void lockedAccountIsRejectedWithoutCheckingCredentialsOrExtendingLock() {
        when(redisTemplate.hasKey(RedisKeyUtil.loginLock(ACCOUNT))).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.login(passwordRequest(), IP, "test", "client-1"));

        assertEquals(ResultCode.LOGIN_LOCKED.getCode(), exception.getCode());
        Result<Void> response = new GlobalExceptionHandler().handleBusinessException(exception);
        assertEquals(30004, response.getCode());
        assertEquals("登录失败次数过多，账号已临时锁定", response.getMsg());
        verifyNoInteractions(userInfoMapper, emailCodeService);
        verify(valueOperations, never()).increment(RedisKeyUtil.loginFail(ACCOUNT));
        verify(valueOperations, never()).set(RedisKeyUtil.loginLock(ACCOUNT), "1", 1800L, TimeUnit.SECONDS);
    }

    @Test
    void successfulEmailCodeLoginClearsSharedAccountFailures() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        UserInfo user = new UserInfo();
        user.setUid(9L);
        user.setStatus(1);
        when(userInfoMapper.selectOne(any())).thenReturn(user);
        LoginRequest request = new LoginRequest();
        request.setAccountType("email");
        request.setEmail(ACCOUNT);
        request.setVerificationCode("123456");

        service.login(request, IP, "test", "client-1");

        verify(redisTemplate).delete(RedisKeyUtil.loginFail(ACCOUNT));
        verify(redisTemplate).delete(RedisKeyUtil.loginLock(ACCOUNT));
        verify(valueOperations, never()).increment(RedisKeyUtil.loginFail(ACCOUNT));
    }

    @Test
    void sixteenthRegistrationAttemptFromIpIsRejected() {
        when(valueOperations.increment(RedisKeyUtil.rate("register-ip", IP))).thenReturn(16L);
        RegisterRequest request = new RegisterRequest();
        request.setEmail(ACCOUNT);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(request, IP, "client-1"));

        assertEquals(ResultCode.IP_RATE_LIMITED.getCode(), exception.getCode());
        verifyNoInteractions(userInfoMapper, emailCodeService);
    }

    @Test
    void sixthRegistrationAttemptForEmailIsRejectedWithinHourlyWindow() {
        when(valueOperations.increment(RedisKeyUtil.rate("register-ip", IP))).thenReturn(1L);
        when(valueOperations.increment(RedisKeyUtil.rate("register-email", ACCOUNT))).thenReturn(6L);
        RegisterRequest request = new RegisterRequest();
        request.setEmail(ACCOUNT);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.register(request, IP, "client-1"));

        assertEquals(ResultCode.IP_RATE_LIMITED.getCode(), exception.getCode());
        verify(redisTemplate).expire(RedisKeyUtil.rate("register-ip", IP), 3600L, TimeUnit.SECONDS);
        verifyNoInteractions(userInfoMapper, emailCodeService);
    }

    private LoginRequest passwordRequest() {
        LoginRequest request = new LoginRequest();
        request.setAccountType("password");
        request.setEmail(ACCOUNT);
        request.setPassword("password");
        return request;
    }
}
