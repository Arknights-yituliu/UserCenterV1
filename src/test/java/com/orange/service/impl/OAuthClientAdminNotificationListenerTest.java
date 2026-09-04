package com.orange.service.impl;

import com.orange.event.OAuthClientReviewNotificationEvent;
import com.orange.service.MailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** OAuth 客户端管理员邮件通知测试。 */
class OAuthClientAdminNotificationListenerTest {

    @Test
    void sendsClientDetailsToConfiguredAdmin() {
        MailService mailService = mock(MailService.class);
        OAuthClientAdminNotificationListener listener =
                new OAuthClientAdminNotificationListener(mailService, "admin@example.com");

        listener.sendReviewNotification(new OAuthClientReviewNotificationEvent(
                "注册", "client-1", "Example SPA", 7L, "https://spa.example.com"));

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendText(org.mockito.ArgumentMatchers.eq("admin@example.com"),
                subject.capture(), content.capture());
        assertTrue(subject.getValue().contains("注册"));
        assertTrue(content.getValue().contains("client-1"));
        assertTrue(content.getValue().contains("https://spa.example.com"));
    }

    @Test
    void skipsNotificationWhenAdminEmailIsBlank() {
        MailService mailService = mock(MailService.class);
        OAuthClientAdminNotificationListener listener =
                new OAuthClientAdminNotificationListener(mailService, " ");

        listener.sendReviewNotification(new OAuthClientReviewNotificationEvent(
                "更新", "client-1", "Example SPA", 7L, null));

        verify(mailService, never()).sendText(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }
}
