package com.orange.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.orange.common.exception.BusinessException;
import com.orange.common.enums.ResultCode;
import com.orange.entity.po.SmtpConfig;
import com.orange.mapper.SmtpConfigMapper;
import com.orange.service.MailService;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ses.v20201002.SesClient;
import com.tencentcloudapi.ses.v20201002.models.SendEmailRequest;
import com.tencentcloudapi.ses.v20201002.models.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 多渠道邮件发送服务实现
 *
 * <p>渠道降级策略（基于每日累计发送量，计数存 Redis，参考 BackEndV3）：</p>
 * <ol>
 *     <li>每日 500 封以内：腾讯云 SES 发送（未配置则跳过）</li>
 *     <li>超过 500 封：降级为第一个 163 邮箱（mail-163-1，配置存 smtp_config 表）</li>
 *     <li>超过 800 封：转为第二个 163 邮箱（mail-163-2）</li>
 * </ol>
 * <p>SES 发送失败时自动降级到 163 渠道，保证可用性。</p>
 *
 * @author UserCenter
 */
@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    /** 每日邮件降级阈值：500 封以内走腾讯云 SES */
    private static final int TENCENT_DAILY_LIMIT = 500;

    /** 每日邮件降级阈值：超过 500 封后降级为第一个 163 邮箱，超过 800 封转为第二个 163 邮箱 */
    private static final int FIRST_163_DAILY_LIMIT = 800;

    private final String secretId;
    private final String secretKey;
    private final String region;
    private final String fromAddress;
    private final Long templateId;
    private final StringRedisTemplate stringRedisTemplate;
    private final SmtpConfigMapper smtpConfigMapper;

    /** 渠道标识 -> 邮件发送器 缓存 */
    private final Map<String, JavaMailSenderImpl> senderCache = new ConcurrentHashMap<>();

    /**
     * 构造器注入依赖（腾讯云配置未配置时使用空默认值，运行时自动跳过 SES 渠道）
     *
     * @param secretId           腾讯云 SecretId
     * @param secretKey          腾讯云 SecretKey
     * @param region             腾讯云地域
     * @param fromAddress        SES 发件人地址
     * @param templateId         SES 模板 ID
     * @param stringRedisTemplate Redis 客户端（每日发送计数）
     * @param smtpConfigMapper   SMTP 渠道配置 Mapper
     */
    public MailServiceImpl(
            @Value("${tencent.secretId:}") String secretId,
            @Value("${tencent.secretKey:}") String secretKey,
            @Value("${tencent.email.region:}") String region,
            @Value("${tencent.email.from-address:}") String fromAddress,
            @Value("${tencent.email.template-id:53553}") Long templateId,
            StringRedisTemplate stringRedisTemplate,
            SmtpConfigMapper smtpConfigMapper) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
        this.fromAddress = fromAddress;
        this.templateId = templateId;
        this.stringRedisTemplate = stringRedisTemplate;
        this.smtpConfigMapper = smtpConfigMapper;
    }

    /**
     * 发送纯文本邮件（使用配置的默认模板）
     *
     * @param to      收件人邮箱
     * @param subject 主题
     * @param content 正文
     */
    @Override
    public void sendText(String to, String subject, String content) {
        sendText(to, subject, content, templateId);
    }

    /**
     * 发送纯文本邮件（指定 SES 模板 ID，多渠道降级路由）
     *
     * @param to         收件人邮箱
     * @param subject    主题
     * @param content    正文
     * @param templateId 腾讯云 SES 模板 ID（163 降级渠道不受影响）
     */
    @Override
    public void sendText(String to, String subject, String content, Long templateId) {
        int dailyCount = getDailyCount();

        try {
            if (dailyCount < TENCENT_DAILY_LIMIT) {
                // 每日 500 封以内：腾讯云 SES 发送
                if (sendTencentCloudEmail(to, subject, content, templateId)) {
                    // 发送成功，递增当日累计计数
                    incrementDailyCount();
                    return;
                }
                log.info("腾讯云 SES 未配置或发送失败，降级为 mail-163-1");
            } else if (dailyCount < FIRST_163_DAILY_LIMIT) {
                log.info("邮件渠道降级：今日已发送 {} 封，切换为 mail-163-1", dailyCount);
            } else {
                log.info("邮件渠道降级：今日已发送 {} 封，切换为 mail-163-2", dailyCount);
            }

            // 降级/超额走 163 SMTP 渠道（配置存数据库）
            String accountKey = dailyCount < FIRST_163_DAILY_LIMIT ? "mail-163-1" : "mail-163-2";
            send163Email(to, subject, content, accountKey);
            incrementDailyCount();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("邮件发送失败，收件人：{}", to, e);
            throw new BusinessException(ResultCode.MAIL_SEND_FAILED);
        }
    }

    /**
     * 获取当日累计发送量
     *
     * @return 当日发送计数（无记录为 0）
     */
    private int getDailyCount() {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String value = stringRedisTemplate.opsForValue().get(emailDailyKey(today));
        return value != null ? Integer.parseInt(value) : 0;
    }

    /**
     * 当日发送量 +1 并设置 1 天过期（自然日滚动）
     */
    private void incrementDailyCount() {
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = emailDailyKey(today);
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, 1, TimeUnit.DAYS);
    }

    /**
     * 每日计数 Redis key
     *
     * @param day yyyyMMdd
     * @return key
     */
    private String emailDailyKey(String day) {
        return "email:daily:" + day;
    }

    /**
     * 通过指定渠道标识的 163 邮箱发送
     *
     * @param to         收件人邮箱
     * @param subject    主题
     * @param content    正文
     * @param accountKey 渠道标识，如 mail-163-1 / mail-163-2
     */
    private void send163Email(String to, String subject, String content, String accountKey) {
        sendBySender(getSender(accountKey), to, subject, content);
    }

    /**
     * 根据渠道标识获取邮件发送器（首次获取后缓存复用）
     *
     * @param accountKey 渠道标识，如 mail-163-1 / mail-163-2
     * @return 邮件发送器
     */
    private JavaMailSender getSender(String accountKey) {
        return senderCache.computeIfAbsent(accountKey, this::createSender);
    }

    /**
     * 根据渠道标识从数据库读取配置并创建邮件发送器
     *
     * @param accountKey 渠道标识
     * @return 配置好的邮件发送器
     */
    private JavaMailSenderImpl createSender(String accountKey) {
        SmtpConfig config = smtpConfigMapper.selectOne(new LambdaQueryWrapper<SmtpConfig>()
                .eq(SmtpConfig::getAccountKey, accountKey)
                .eq(SmtpConfig::getEnabled, Boolean.TRUE));
        if (config == null) {
            throw new BusinessException(ResultCode.MAIL_SEND_FAILED, "邮件渠道未配置：" + accountKey);
        }

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(config.getPassword());
        sender.setProtocol(config.getProtocol() != null ? config.getProtocol() : "smtp");
        sender.setDefaultEncoding(config.getDefaultEncoding() != null ? config.getDefaultEncoding() : "UTF-8");

        // 配置 SSL 相关属性
        if (Boolean.TRUE.equals(config.getSslEnable())) {
            Properties props = sender.getJavaMailProperties();
            props.put("mail.smtp.ssl.enable", "true");
            if (config.getPort() != null) {
                props.put("mail.smtp.socketFactory.port", String.valueOf(config.getPort()));
            }
            props.put("mail.smtp.socketFactoryClass", "javax.net.ssl.SSLSocketFactory");
        }
        return sender;
    }

    /**
     * 通过指定 SMTP 发送器发送邮件
     *
     * @param sender  SMTP 发送器（对应某个邮箱渠道）
     * @param to      收件人邮箱
     * @param subject 主题
     * @param content 正文
     */
    private void sendBySender(JavaMailSender sender, String to, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        // 发件人使用当前 SMTP 账号（跟随渠道配置），不依赖业务层传入的 from
        message.setFrom(((JavaMailSenderImpl) sender).getUsername());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(content);
        sender.send(message);
    }

    /**
     * 通过腾讯云 SES 发送邮件
     *
     * @param to         收件人邮箱
     * @param subject    主题
     * @param content    正文（模板变量 {{code}}）
     * @param templateId 腾讯云 SES 模板 ID（按业务场景区分）
     * @return true=发送成功；false=未配置或发送失败（供调用方降级）
     */
    private boolean sendTencentCloudEmail(String to, String subject, String content, Long templateId) {
        // 未配置腾讯云凭据时跳过 SES 渠道
        if (secretId == null || secretId.isBlank() || secretKey == null || secretKey.isBlank()) {
            return false;
        }

        try {
            Credential cred = new Credential(secretId, secretKey);
            HttpProfile httpProfile = new HttpProfile();
            httpProfile.setEndpoint("ses.tencentcloudapi.com");
            ClientProfile clientProfile = new ClientProfile();
            clientProfile.setHttpProfile(httpProfile);
            SesClient client = new SesClient(cred, region, clientProfile);

            SendEmailRequest req = new SendEmailRequest();
            req.setFromEmailAddress(fromAddress);
            req.setDestination(new String[]{to});
            req.setSubject(subject);

            Template template = new Template();
            template.setTemplateID(templateId);
            template.setTemplateData(buildTemplateData(content));
            req.setTemplate(template);

            client.SendEmail(req);
            log.info("腾讯云邮件发送成功，收件人：{}", to);
            return true;
        } catch (TencentCloudSDKException e) {
            log.error("腾讯云邮件发送失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 构建模板数据的 JSON 字符串，模板变量为 {{code}}
     *
     * @param content 验证码内容
     * @return JSON 字符串
     */
    private String buildTemplateData(String content) {
        return "{\"code\":\"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }
}
