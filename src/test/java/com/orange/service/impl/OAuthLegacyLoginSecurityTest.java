package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.config.OAuthDirectProperties;
import com.orange.common.config.UserAccentProperties;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.auth.ServerLoginVO;
import com.orange.entity.vo.oauth.DirectLoginSessionVO;
import com.orange.entity.vo.oauth.DirectLoginTicketVO;
import com.orange.entity.vo.oauth.OAuthTokenVO;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证旧系统直连认证的客户端权限、票据有效期、限流和渐进锁定策略。 */
@ExtendWith(MockitoExtension.class)
class OAuthLegacyLoginSecurityTest {

    @Mock
    private UserInfoMapper userInfoMapper;
    @Mock
    private LoginLogMapper loginLogMapper;
    @Mock
    private OAuthClientMapper oauthClientMapper;
    @Mock
    private EmailCodeService emailCodeService;
    @Mock
    private RevokeService revokeService;
    @Mock
    private OAuthTokenService oauthTokenService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private Validator validator;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(
                userInfoMapper, loginLogMapper, oauthClientMapper,
                emailCodeService, revokeService, oauthTokenService,
                redisTemplate, new ObjectMapper(), validator, new UserAccentProperties(), new OAuthDirectProperties());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void directSessionUsesFiveMinuteExpiryAndOneMinuteRateWindows() {
        when(oauthClientMapper.selectById("client-1")).thenReturn(encryptedClient(true));
        when(valueOperations.increment(anyString())).thenReturn(1L);

        DirectLoginSessionVO session = service.createDirectSession("client-1", "secret", "127.0.0.1");

        assertEquals(300L, session.getExpiresIn());
        verify(valueOperations).set(eq(RedisKeyUtil.directChannel(session.getChannel())), anyString(),
                eq(300L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).expire(RedisKeyUtil.rate("direct-session-ip", "127.0.0.1"), 60L, TimeUnit.SECONDS);
        verify(redisTemplate).expire(RedisKeyUtil.rate("direct-session-client-ip", "client-1:127.0.0.1"),
                60L, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @CsvSource({"61, 0", "1, 21"})
    void directSessionEnforcesIpAndClientIpLimits(long ipCount, long clientIpCount) {
        when(oauthClientMapper.selectById("client-1")).thenReturn(encryptedClient(true));
        when(valueOperations.increment(RedisKeyUtil.rate("direct-session-ip", "127.0.0.1"))).thenReturn(ipCount);
        if (ipCount <= 60) {
            when(valueOperations.increment(RedisKeyUtil.rate("direct-session-client-ip", "client-1:127.0.0.1")))
                    .thenReturn(clientIpCount);
        }

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDirectSession("client-1", "secret", "127.0.0.1"));

        assertEquals(ResultCode.IP_RATE_LIMITED.getCode(), exception.getCode());
    }

    @Test
    void thirtyFirstDirectLoginRequestIsRejectedBeforeReadingChannel() {
        when(valueOperations.increment(RedisKeyUtil.rate("direct-login-ip", "127.0.0.1"))).thenReturn(31L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.directLogin("channel", "password", "user", "password", null, "127.0.0.1"));

        assertEquals(ResultCode.IP_RATE_LIMITED.getCode(), exception.getCode());
        verify(valueOperations, never()).get(RedisKeyUtil.directChannel("channel"));
    }

    @ParameterizedTest
    @CsvSource({"1, 0", "4, 0", "5, 60", "9, 60", "10, 300", "14, 300", "15, 900"})
    void directLoginFailureAppliesProgressiveLocksToIpAndAccount(long failureCount, long lockSeconds) {
        String ip = "127.0.0.1";
        String account = "user@example.com";
        when(valueOperations.increment(RedisKeyUtil.rate("direct-login-ip", ip))).thenReturn(1L);
        when(valueOperations.get(RedisKeyUtil.directChannel("channel")))
                .thenReturn("{\"clientId\":\"client-1\"}");
        when(oauthClientMapper.selectById("client-1")).thenReturn(encryptedClient(true));
        when(valueOperations.increment(RedisKeyUtil.loginFail(account))).thenReturn(1L);
        when(valueOperations.increment(RedisKeyUtil.rate("direct-login-ip-fail", ip))).thenReturn(failureCount);
        when(valueOperations.increment(RedisKeyUtil.rate("direct-login-account-fail", account))).thenReturn(failureCount);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.directLogin("channel", "password", account, "password", null, ip));

        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), exception.getCode());
        verify(redisTemplate).expire(RedisKeyUtil.rate("direct-login-ip", ip), 60L, TimeUnit.SECONDS);
        if (failureCount == 1) {
            verify(redisTemplate).expire(RedisKeyUtil.rate("direct-login-ip-fail", ip), 900L, TimeUnit.SECONDS);
            verify(redisTemplate).expire(RedisKeyUtil.rate("direct-login-account-fail", account), 900L, TimeUnit.SECONDS);
        }
        if (lockSeconds > 0) {
            verify(valueOperations).set(RedisKeyUtil.rate("direct-login-ip-lock", ip), "1", lockSeconds, TimeUnit.SECONDS);
            verify(valueOperations).set(RedisKeyUtil.rate("direct-login-account-lock", account), "1", lockSeconds, TimeUnit.SECONDS);
        } else {
            verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        }
    }

    @Test
    void successfulDirectLoginIssuesOneMinuteTicket() {
        String account = "user@example.com";
        when(valueOperations.increment(RedisKeyUtil.rate("direct-login-ip", "127.0.0.1"))).thenReturn(1L);
        when(valueOperations.get(RedisKeyUtil.directChannel("channel")))
                .thenReturn("{\"clientId\":\"client-1\"}");
        when(oauthClientMapper.selectById("client-1")).thenReturn(encryptedClient(true));
        UserInfo user = new UserInfo();
        user.setUid(9L);
        user.setStatus(1);
        when(userInfoMapper.selectOne(any())).thenReturn(user);

        DirectLoginTicketVO ticket = service.directLogin("channel", "email", account, null, "123456", "127.0.0.1");

        assertEquals(60L, ticket.getExpiresIn());
        verify(valueOperations).set(eq(RedisKeyUtil.directTicket(ticket.getTicket())), anyString(),
                eq(60L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete(RedisKeyUtil.directChannel("channel"));
    }

    @Test
    void publicClientCannotCreateLegacyDirectLoginSession() {
        OAuthClient publicClient = new OAuthClient();
        publicClient.setId("spa-client");
        publicClient.setAuthMethods("none");
        publicClient.setClientSecret(null);
        publicClient.setOwnerEnabled(1);
        publicClient.setAdminApproved(1);
        publicClient.setDirectAuthEnabled(1);
        when(oauthClientMapper.selectById("spa-client")).thenReturn(publicClient);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDirectSession("spa-client", null, "127.0.0.1"));

        assertEquals(ResultCode.OAUTH_SECRET_INVALID.getCode(), exception.getCode());
    }

    @Test
    void clientWithoutDirectAuthCannotCreateSession() {
        OAuthClient client = encryptedClient(false);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createDirectSession("client-1", "secret", "127.0.0.1"));

        assertEquals(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED.getCode(), exception.getCode());
    }

    @Test
    void disablingDirectAuthRejectsExistingLoginChannel() {
        OAuthClient client = encryptedClient(false);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get(RedisKeyUtil.directChannel("channel")))
                .thenReturn("{\"clientId\":\"client-1\"}");
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.directLogin("channel", "password", "user", "password", null, "127.0.0.1"));

        assertEquals(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED.getCode(), exception.getCode());
    }

    @Test
    void disablingDirectAuthRejectsExistingRegistrationChannel() {
        OAuthClient client = encryptedClient(false);
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.get(RedisKeyUtil.directChannel("channel")))
                .thenReturn("{\"clientId\":\"client-1\"}");
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.directRegister("channel", new RegisterRequest(), "127.0.0.1"));

        assertEquals(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED.getCode(), exception.getCode());
    }

    @Test
    void disablingDirectAuthRejectsExistingTicket() {
        OAuthClient client = encryptedClient(false);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.directUser("client-1", "secret", "ticket"));

        assertEquals(ResultCode.OAUTH_DIRECT_AUTH_NOT_ALLOWED.getCode(), exception.getCode());
        // 权限校验先于票据消费：被拒时票据必须原封不动，用户可改用其他方式兑换
        verify(valueOperations, never()).setIfAbsent(
                anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void directUserIssuesAccessAndRefreshToken() {
        OAuthClient client = encryptedClient(true);
        when(oauthClientMapper.selectById("client-1")).thenReturn(client);
        when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
        when(valueOperations.get(RedisKeyUtil.directTicket("ticket")))
                .thenReturn("{\"clientId\":\"client-1\",\"uid\":9}");
        UserInfo user = new UserInfo();
        user.setUid(9L);
        user.setNickname("nick");
        user.setAvatar("avatar");
        user.setStatus(1);
        when(userInfoMapper.selectById(9L)).thenReturn(user);
        OAuthTokenVO token = new OAuthTokenVO();
        token.setAccessToken("access-1");
        token.setTokenType("Bearer");
        token.setExpiresIn(7200L);
        token.setRefreshToken("refresh-1");
        token.setScope("user.read");
        when(oauthTokenService.issueDirectToken("client-1", 9L)).thenReturn(token);

        ServerLoginVO vo = service.directUser("client-1", "secret", "ticket");

        assertEquals(Long.valueOf(9L), vo.getUid());
        assertEquals("nick", vo.getNickname());
        assertEquals("access-1", vo.getAccessToken());
        assertEquals("refresh-1", vo.getRefreshToken());
        assertEquals("Bearer", vo.getTokenType());
        assertEquals(Long.valueOf(7200L), vo.getExpiresIn());
        assertEquals("user.read", vo.getScope());
        // 票据一次性消费：兑换成功后必须删除票据数据
        verify(redisTemplate).delete(RedisKeyUtil.directTicket("ticket"));
    }

    private OAuthClient encryptedClient(boolean directAuthEnabled) {
        OAuthClient client = new OAuthClient();
        client.setId("client-1");
        client.setAuthMethods("client_secret_post");
        client.setClientSecret(new BCryptPasswordEncoder().encode("secret"));
        client.setOwnerEnabled(1);
        client.setAdminApproved(1);
        client.setDirectAuthEnabled(directAuthEnabled ? 1 : 0);
        return client;
    }
}
