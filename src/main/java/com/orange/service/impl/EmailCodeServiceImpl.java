package com.orange.service.impl;

import com.orange.common.exception.BusinessException;
import com.orange.common.enums.ResultCode;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.RedisRateLimiter;
import com.orange.service.EmailCodeService;
import com.orange.service.MailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务实现（Redis 存储，5 分钟过期，一次性使用）
 *
 * @author UserCenter
 */
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    /** 验证码位数 */
    private static final int CODE_LENGTH = 6;

    private final StringRedisTemplate stringRedisTemplate;
    private final MailService mailService;

    /** 验证码有效期（秒） */
    @Value("${user-center.code-ttl-seconds:300}")
    private long codeTtlSeconds;

    /** 同 IP 发送最小间隔（秒） */
    @Value("${user-center.code-send-interval-seconds:60}")
    private long ipIntervalSeconds;

    /** 同邮箱发送最小间隔（秒）：默认 5 分钟 */
    @Value("${user-center.email-send-interval-seconds:300}")
    private long emailIntervalSeconds;

    /** 注册验证码邮件模板 ID（腾讯云 SES） */
    @Value("${tencent.email.register-template-id:57132}")
    private Long registerTemplateId;

    /** 登录验证码邮件模板 ID（腾讯云 SES） */
    @Value("${tencent.email.login-template-id:57133}")
    private Long loginTemplateId;

    /** 其他场景（重置密码/换绑邮箱等）邮件模板 ID（沿用原 53553） */
    @Value("${tencent.email.template-id:53553}")
    private Long defaultTemplateId;

    /**
     * 构造器注入依赖
     *
     * @param stringRedisTemplate Redis 客户端
     * @param mailService         邮件服务
     */
    public EmailCodeServiceImpl(StringRedisTemplate stringRedisTemplate, MailService mailService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.mailService = mailService;
    }

    /**
     * 发送邮箱验证码（带发送限流）
     *
     * @param email 目标邮箱
     * @param usage 验证码用途
     * @param ip    请求 IP（限流维度）
     */
    @Override
    public void sendCode(String email, String usage, String ip) {
        // 同 IP 限流：间隔内只能发送一次
        if (!RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("send-code", ip), 1, ipIntervalSeconds)) {
            throw new BusinessException(ResultCode.CODE_SEND_TOO_FREQUENT,
                    "同 IP 发送过于频繁，请 " + ipIntervalSeconds + " 秒后再试");
        }
        // 同邮箱限流：间隔内只能发送一次
        if (!RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("send-code-email", email), 1, emailIntervalSeconds)) {
            throw new BusinessException(ResultCode.CODE_SEND_TOO_FREQUENT,
                    "发送过于频繁，请 " + (emailIntervalSeconds / 60) + " 分钟后再试");
        }

        String code = generateCode();
        // 验证码存 Redis，5 分钟过期
        stringRedisTemplate.opsForValue().set(RedisKeyUtil.code(email), code, codeTtlSeconds, TimeUnit.SECONDS);

        // 按业务场景选择邮件模板：注册 57132、登录 57133、重置/换绑等其他沿用默认
        Long templateId = switch (usage) {
            case "register" -> registerTemplateId;
            case "login" -> loginTemplateId;
            default -> defaultTemplateId;
        };

        String subject = "【User Center】邮箱验证码";
        String content = "您的验证码为 " + code + "，"
                + (codeTtlSeconds / 60) + " 分钟内有效，请勿泄露给他人。";
        mailService.sendText(email, subject, content, templateId);
    }

    /**
     * 校验验证码（校验通过后立即删除，保证一次性使用）
     *
     * @param email 邮箱
     * @param code  验证码
     */
    @Override
    public void verifyCode(String email, String code) {
        String key = RedisKeyUtil.code(email);
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new BusinessException(ResultCode.CODE_EXPIRED);
        }
        if (!stored.equals(code)) {
            throw new BusinessException(ResultCode.CODE_ERROR);
        }
        stringRedisTemplate.delete(key);
    }

    /**
     * 生成 6 位数字验证码
     *
     * @return 验证码字符串
     */
    private String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
