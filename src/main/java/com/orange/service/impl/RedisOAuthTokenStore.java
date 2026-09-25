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
    private final DefaultRedisScript<Long> issueAccessFromRefreshScript;
    private final DefaultRedisScript<List> issueMigratedTokenScript;

    /**
     * 加载 classpath 中的 Lua 脚本。脚本文本由 Spring 计算 SHA 并优先使用 EVALSHA，
     * Redis 尚未缓存脚本时框架会自动回退到 EVAL。
     */
    public RedisOAuthTokenStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.consumeAuthorizationCodeScript = loadScript("scripts/oauth-code-consume.lua");
        this.issueAccessFromRefreshScript = loadScript("scripts/oauth-refresh-issue.lua");
        this.issueMigratedTokenScript = loadMultiValueScript("scripts/oauth-migrate-issue.lua");
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
    public boolean issueAccessFromRefresh(RefreshAccessRequest request) {
        List<String> keys = Arrays.asList(
                RedisKeyUtil.oauthRefresh(request.oldRefreshToken()),
                RedisKeyUtil.oauthAccess(request.newAccessToken()),
                RedisKeyUtil.uidOauth(request.uid()));

        Long result = redisTemplate.execute(issueAccessFromRefreshScript, keys,
                request.expectedOldRefreshJson(),
                request.newAccessJson(),
                Long.toString(request.accessTtlSeconds()),
                ACCESS_MEMBER_PREFIX + request.newAccessToken());
        return Long.valueOf(1L).equals(result);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MigrateIssueResult issueMigratedToken(MigrateIssueRequest request) {
        List<String> keys = Arrays.asList(
                RedisKeyUtil.oauthMigrateCurrent(request.clientId(), request.uid()),
                RedisKeyUtil.oauthAccess(request.newAccessToken()),
                RedisKeyUtil.oauthRefresh(request.newRefreshToken()),
                RedisKeyUtil.uidOauth(request.uid()));

        List<Object> result = (List<Object>) redisTemplate.execute(issueMigratedTokenScript, keys,
                request.newAccessJson(),
                request.newRefreshJson(),
                Long.toString(request.accessTtlSeconds()),
                Long.toString(request.refreshTtlSeconds()),
                ACCESS_MEMBER_PREFIX + request.newAccessToken(),
                REFRESH_MEMBER_PREFIX + request.newRefreshToken(),
                RedisKeyUtil.oauthRefreshPrefix(),
                REFRESH_MEMBER_PREFIX,
                request.newRefreshToken(),
                Long.toString(request.refreshTtlSeconds()));
        if (result == null || result.size() < 2) {
            return new MigrateIssueResult(false, null);
        }
        Object resultCode = result.get(0);
        if (!(resultCode instanceof Number) || ((Number) resultCode).longValue() != 1L) {
            return new MigrateIssueResult(false, null);
        }
        String revoked = (String) result.get(1);
        return new MigrateIssueResult(true, revoked == null || revoked.isEmpty() ? null : revoked);
    }

    /** 创建返回 Long 的 Redis 脚本定义。 */
    private DefaultRedisScript<Long> loadScript(String classpathLocation) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpathLocation));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 创建返回多值数组的 Redis 脚本定义。
     *
     * <p>迁移签发脚本需要同时回传“结果码”和“被本次替换撤销的旧 refresh_token”，
     * 因此结果类型为 List：元素 0 为结果码（Long），元素 1 为旧 refresh_token（String）。</p>
     */
    @SuppressWarnings("rawtypes")
    private DefaultRedisScript<List> loadMultiValueScript(String classpathLocation) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(classpathLocation));
        script.setResultType(List.class);
        return script;
    }
}
