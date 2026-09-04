package com.orange.service.impl;

import com.orange.common.util.RedisKeyUtil;
import com.orange.service.OAuthTokenStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 基于 Redis Lua 的 OAuth 一次性凭证存储实现。
 *
 * <p>Spring 发起的每次脚本执行都在 Redis 事件循环中原子完成。这里不使用 Java 锁，
 * 因为应用可能部署多个实例，进程内锁无法覆盖跨实例并发。</p>
 */
@Component
public class RedisOAuthTokenStore implements OAuthTokenStore {

    private static final String ACCESS_MEMBER_PREFIX = "access:";
    private static final String REFRESH_MEMBER_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> consumeAuthorizationCodeScript;
    private final DefaultRedisScript<Long> rotateRefreshTokenScript;

    /**
     * 加载 classpath 中的 Lua 脚本。脚本文本由 Spring 计算 SHA 并优先使用 EVALSHA，
     * Redis 尚未缓存脚本时框架会自动回退到 EVAL。
     */
    public RedisOAuthTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeAuthorizationCodeScript = loadScript("scripts/oauth-code-consume.lua");
        this.rotateRefreshTokenScript = loadScript("scripts/oauth-refresh-rotate.lua");
    }

    @Override
    public String readAuthorizationCode(String code) {
        return redisTemplate.opsForValue().get(RedisKeyUtil.oauthCode(code));
    }

    @Override
    public boolean isAuthorizationCodeUsed(String code) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeyUtil.oauthCodeUsed(code)));
    }

    @Override
    public boolean consumeAuthorizationCode(String code, String expectedJson, long usedTtlSeconds) {
        List<String> keys = Arrays.asList(
                RedisKeyUtil.oauthCode(code),
                RedisKeyUtil.oauthCodeUsed(code));
        Long result = redisTemplate.execute(consumeAuthorizationCodeScript, keys,
                expectedJson, Long.toString(usedTtlSeconds));
        return Long.valueOf(1L).equals(result);
    }

    @Override
    public String readRefreshToken(String refreshToken) {
        return redisTemplate.opsForValue().get(RedisKeyUtil.oauthRefresh(refreshToken));
    }

    @Override
    public boolean rotateRefreshToken(RefreshTokenRotation rotation) {
        List<String> keys = Arrays.asList(
                RedisKeyUtil.oauthRefresh(rotation.oldRefreshToken()),
                RedisKeyUtil.oauthAccess(rotation.newAccessToken()),
                RedisKeyUtil.oauthRefresh(rotation.newRefreshToken()),
                RedisKeyUtil.uidOauth(rotation.uid()));

        Long result = redisTemplate.execute(rotateRefreshTokenScript, keys,
                rotation.expectedOldRefreshJson(),
                rotation.newAccessJson(),
                Long.toString(rotation.accessTtlSeconds()),
                rotation.newRefreshJson(),
                Long.toString(rotation.refreshTtlSeconds()),
                REFRESH_MEMBER_PREFIX + rotation.oldRefreshToken(),
                ACCESS_MEMBER_PREFIX + rotation.newAccessToken(),
                REFRESH_MEMBER_PREFIX + rotation.newRefreshToken());
        return Long.valueOf(1L).equals(result);
    }

    /** 创建返回 Long 的 Redis 脚本定义。 */
    private DefaultRedisScript<Long> loadScript(String classpathLocation) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpathLocation));
        script.setResultType(Long.class);
        return script;
    }
}
