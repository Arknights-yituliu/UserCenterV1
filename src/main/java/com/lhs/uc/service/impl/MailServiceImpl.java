package com.lhs.uc.service.impl;

import com.lhs.uc.common.exception.BusinessException;
import com.lhs.uc.common.enums.ResultCode;
import com.lhs.uc.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务实现
 *
 * @author UserCenter
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final JavaMailSender mailSender;

    /** 发件人地址 */
    @Value("${spring.mail.username:noreply@example.com}")
    private String from;

    /**
     * 构造器注入邮件发送器
     *
     * @param mailSender 邮件发送器
     */
    public MailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * 发送纯文本邮件
     *
     * @param to      收件人邮箱
     * @param subject 主题
     * @param content 正文
     */
    @Override
    public void sendText(String to, String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("邮件已发送至 {}，主题：{}", to, subject);
        } catch (Exception e) {
            log.error("邮件发送失败，收件人：{}", to, e);
            throw new BusinessException(ResultCode.MAIL_SEND_FAILED);
        }
    }
}
