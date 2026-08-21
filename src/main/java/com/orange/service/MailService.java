package com.orange.service;

/**
 * 邮件发送服务接口
 *
 * @author UserCenter
 */
public interface MailService {

    /**
     * 发送纯文本邮件（使用默认模板）
     *
     * @param to      收件人邮箱
     * @param subject 主题
     * @param content 正文
     */
    void sendText(String to, String subject, String content);

    /**
     * 发送纯文本邮件（指定腾讯云 SES 模板 ID）
     *
     * @param to         收件人邮箱
     * @param subject    主题
     * @param content    正文
     * @param templateId 腾讯云 SES 模板 ID（163 降级渠道不受影响）
     */
    void sendText(String to, String subject, String content, Long templateId);
}
