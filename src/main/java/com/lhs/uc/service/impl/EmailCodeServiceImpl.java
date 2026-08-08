package com.lhs.uc.service.impl;

import com.lhs.uc.common.exception.BusinessException;
import com.lhs.uc.common.enums.ResultCode;
import com.lhs.uc.common.util.RedisKeyUtil;
import com.lhs.uc.common.util.RedisRateLimiter;
import com.lhs.uc.service.EmailCodeService;
import com.lhs.uc.service.MailService;
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

    /** 同 IP 发送最小间隔（秒） */
    private static final long IP_INTERVAL_SECONDS = 60;

    /** 同邮箱发送最小间隔（秒）：5 分钟 */
    private static final long EMAIL_INTERVAL_SECONDS = 300;

    private final StringRedisTemplate stringRedisTemplate;
    private final MailService mailService;

    /** 验证码有效期（秒） */
    @Value("${uc.code-ttl-seconds:300}")
    private long codeTtlSeconds;

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
        // 同 IP 限流：60 秒内只能发送一次
        if (!RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("send-code", ip), 1, IP_INTERVAL_SECONDS)) {
            throw new BusinessException(ResultCode.CODE_SEND_TOO_FREQUENT, "同 IP 发送过于频繁，请 60 秒后再试");
        }
        // 同邮箱限流：5 分钟内只能发送一次
        if (!RedisRateLimiter.tryAcquire(stringRedisTemplate,
                RedisKeyUtil.rate("send-code-email", email), 1, EMAIL_INTERVAL_SECONDS)) {
            throw new BusinessException(ResultCode.CODE_SEND_TOO_FREQUENT, "发送过于频繁，请 5 分钟后再试");
        }

        String code = generateCode();
        // 验证码存 Redis，5 分钟过期
        stringRedisTemplate.opsForValue().set(RedisKeyUtil.code(email), code, codeTtlSeconds, TimeUnit.SECONDS);

        String subject = "【User Center】邮箱验证码";
        String content = "您的验证码为 " + code + "，"
                + (codeTtlSeconds / 60) + " 分钟内有效，请勿泄露给他人。";
        mailService.sendText(email, subject, content);
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
