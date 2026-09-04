package com.orange.service.impl;

import com.orange.event.OAuthClientReviewNotificationEvent;
import com.orange.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * OAuth 客户端注册和更新的管理员邮件通知。
 *
 * <p>事务成功提交后异步发送。通知失败只记录日志，不影响已经完成的客户端操作。</p>
 *
 * @author UserCenter
 */
@Component
public class OAuthClientAdminNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OAuthClientAdminNotificationListener.class);

    private final MailService mailService;
    private final String adminEmail;

    public OAuthClientAdminNotificationListener(
            MailService mailService,
            @Value("${user-center.oauth.admin-email:}") String adminEmail) {
        this.mailService = mailService;
        this.adminEmail = adminEmail;
    }

    /**
     * 发送管理员审核通知。未配置收件邮箱时跳过，避免影响客户端管理功能。
     *
     * @param event 客户端注册或更新事件
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void sendReviewNotification(OAuthClientReviewNotificationEvent event) {
        if (!StringUtils.hasText(adminEmail)) {
            log.warn("[OAuthClient] 未配置 user-center.oauth.admin-email，跳过管理员审核邮件");
            return;
        }

        String subject = "[UserCenter] OAuth 客户端" + event.action() + "通知 - " + event.clientName();
        String content = "OAuth 客户端发生" + event.action() + "，请管理员核查。\n\n"
                + "客户端 ID：" + event.clientId() + "\n"
                + "客户端名称：" + event.clientName() + "\n"
                + "所有者 UID：" + event.ownerUid() + "\n"
                + "Origin：" + (StringUtils.hasText(event.origin()) ? event.origin() : "未填写") + "\n";
        try {
            mailService.sendText(adminEmail, subject, content);
            log.info("[OAuthClient] 管理员审核邮件发送成功: action={}, clientId={}",
                    event.action(), event.clientId());
        } catch (RuntimeException e) {
            log.error("[OAuthClient] 管理员审核邮件发送失败: action={}, clientId={}",
                    event.action(), event.clientId(), e);
        }
    }
}
