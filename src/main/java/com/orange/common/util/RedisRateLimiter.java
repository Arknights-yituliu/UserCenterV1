package com.orange.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Redis 限流工具：基于 INCR + 过期时间的固定窗口计数器
 *
 * @author UserCenter
 */
public final class RedisRateLimiter {

    private RedisRateLimiter() {
    }

    /**
     * 尝试获取一次额度
     *
     * <p>首次调用时写入过期时间，窗口内计数超过 limit 则拒绝。</p>
     *
     * @param redis       Redis 客户端
     * @param key         限流 key
     * @param limit       窗口内允许的最大次数
     * @param ttlSeconds  窗口时长（秒）
     * @return true=允许，false=超限
     */
    public static boolean tryAcquire(StringRedisTemplate redis, String key, long limit, long ttlSeconds) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, ttlSeconds, TimeUnit.SECONDS);
        }
        return count == null || count <= limit;
    }
}
