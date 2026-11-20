package com.orange.service.impl;

import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.entity.po.UserInfo;
import com.orange.mapper.AccountDeletionMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.OAuthTokenService;
import com.orange.service.RevokeService;
import com.orange.service.EmailCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceImplTest {
    @Mock private UserInfoMapper userMapper;
    @Mock private AccountDeletionMapper mapper;
    @Mock private OAuthTokenService oauth;
    @Mock private RevokeService revoke;
    @Mock private EmailCodeService emailCodes;
    private AccountDeletionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccountDeletionServiceImpl(userMapper, mapper, oauth, revoke, emailCodes);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private void existingUser() {
        UserInfo user = new UserInfo();
        user.setEmail("bound@example.com");
        when(userMapper.selectByUidForUpdate(9L)).thenReturn(user);
    }

    @Test
    void archivesBeforeDeletingAndOnlyKicksSessions() {
        existingUser();
        UserContext.setClientId("web-app");
        when(mapper.archiveUser(9L, "session", "web-app")).thenReturn(1);
        when(userMapper.deleteById(9L)).thenReturn(1);
        service.deleteAccount(9L, "123456");
        InOrder order = inOrder(userMapper, mapper, revoke, emailCodes);
        order.verify(userMapper).selectByUidForUpdate(9L);
        order.verify(emailCodes).verifyAccountDeletionCode(9L, "bound@example.com", "web-app", "123456");
        order.verify(mapper).archiveUser(9L, "session", "web-app");
        order.verify(mapper).deleteAkBindings(9L);
        order.verify(mapper).deleteEfBindings(9L);
        order.verify(userMapper).deleteById(9L);
        order.verify(revoke).kickAllSessions(9L);
        verifyNoMoreInteractions(mapper, revoke);
        verifyNoInteractions(oauth);
    }

    @Test
    void refreshTokenDeterminesUserAndAuditClient() {
        existingUser();
        UserContext.setClientId("cannot-override-refresh-client");
        when(oauth.resolveRefreshToken("refresh"))
                .thenReturn(new OAuthTokenService.RefreshTokenPrincipal(9L, "oauth-app", "user.read", "family"));
        when(mapper.archiveUser(9L, "oauth_refresh", "oauth-app")).thenReturn(1);
        when(userMapper.deleteById(9L)).thenReturn(1);
        service.deleteByRefreshToken("refresh", "123456");
        verify(emailCodes).verifyAccountDeletionCode(9L, "bound@example.com", "oauth-app", "123456");
        verify(mapper).archiveUser(9L, "oauth_refresh", "oauth-app");
        verify(userMapper).deleteById(9L);
        verify(revoke).kickAllSessions(9L);
        verify(revoke, never()).revokeUser(any());
        verify(revoke, never()).revokeUserTokens(any());
    }

    @Test
    void legacySessionMayHaveNoClientId() {
        existingUser();
        when(mapper.archiveUser(9L, "session", null)).thenReturn(1);
        when(userMapper.deleteById(9L)).thenReturn(1);
        service.deleteAccount(9L, "123456");
        verify(mapper).archiveUser(9L, "session", null);
    }

    @Test
    void invalidRefreshCannotDeleteAnything() {
        when(oauth.resolveRefreshToken("access-or-expired"))
                .thenThrow(new BusinessException(ResultCode.OAUTH_TOKEN_INVALID));
        assertThrows(BusinessException.class, () -> service.deleteByRefreshToken("access-or-expired", "123456"));
        verifyNoInteractions(userMapper, mapper, revoke);
    }

    @Test
    void missingUserCannotCreateAuditOrDeleteBindings() {
        assertThrows(BusinessException.class, () -> service.deleteAccount(9L, "123456"));
        verifyNoInteractions(mapper, revoke);
    }

    @Test
    void failedAuditPreventsAnyDeletion() {
        existingUser();
        BusinessException error = assertThrows(BusinessException.class, () -> service.deleteAccount(9L, "123456"));
        assertEquals(ResultCode.SYSTEM_ERROR.getCode(), error.getCode());
        verify(mapper, never()).deleteAkBindings(any());
        verify(mapper, never()).deleteEfBindings(any());
        verify(userMapper, never()).deleteById(any(Long.class));
        verifyNoInteractions(revoke);
    }

    @Test
    void wrongCodePreventsAuditAndDeletion() {
        existingUser();
        doThrow(new BusinessException(ResultCode.CODE_ERROR)).when(emailCodes)
                .verifyAccountDeletionCode(9L, "bound@example.com", null, "123456");
        assertThrows(BusinessException.class, () -> service.deleteAccount(9L, "123456"));
        verifyNoInteractions(mapper, revoke);
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void missingEmailCannotSendOrDelete() {
        UserInfo user = new UserInfo();
        when(userMapper.selectById(9L)).thenReturn(user);
        when(userMapper.selectByUidForUpdate(9L)).thenReturn(user);
        assertEquals(ResultCode.EMAIL_NOT_BOUND.getCode(),
                assertThrows(BusinessException.class, () -> service.sendDeletionCode(9L, "127.0.0.1")).getCode());
        assertEquals(ResultCode.EMAIL_NOT_BOUND.getCode(),
                assertThrows(BusinessException.class, () -> service.deleteAccount(9L, "123456")).getCode());
        verifyNoInteractions(emailCodes, mapper, revoke);
    }

    @Test
    void sendsOnlyToBoundEmailUsingAuthenticatedClient() {
        UserInfo user = new UserInfo();
        user.setEmail("bound@example.com");
        when(userMapper.selectById(9L)).thenReturn(user);
        UserContext.setClientId("web-app");
        service.sendDeletionCode(9L, "127.0.0.1");
        verify(emailCodes).sendAccountDeletionCode(9L, "bound@example.com", "web-app", "127.0.0.1");
        when(oauth.resolveRefreshToken("refresh"))
                .thenReturn(new OAuthTokenService.RefreshTokenPrincipal(9L, "oauth-app", "user.read", "family"));
        service.sendDeletionCodeByRefreshToken("refresh", "127.0.0.2");
        verify(emailCodes).sendAccountDeletionCode(9L, "bound@example.com", "oauth-app", "127.0.0.2");
        verifyNoInteractions(mapper, revoke);
    }
}
