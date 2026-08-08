package com.lhs.uc.service;

/**
 * 邮件发送服务接口
 *
 * @author UserCenter
 */
public interface MailService {

    /**
     * 发送纯文本邮件
     *
     * @param to      收件人邮箱
     * @param subject 主题
     * @param content 正文
     */
    void sendText(String to, String subject, String content);
}
