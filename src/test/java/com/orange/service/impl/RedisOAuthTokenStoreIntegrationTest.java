package com.orange.service.impl;

import com.orange.common.util.RedisKeyUtil;
import com.orange.service.OAuthTokenStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redis Lua 并发集成测试。
 *
 * <p>该测试必须连接真实的单实例 Redis，不能由 Mockito 替代。默认跳过，CI 或本地通过
 * {@code RUN_REDIS_INTEGRATION_TESTS=true} 显式启用；连接地址由 {@code OAUTH_REDIS_HOST}
 * 和 {@code OAUTH_REDIS_PORT} 指定，默认 localhost:6379。</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_REDIS_INTEGRATION_TESTS", matches = "(?i)true")
class RedisOAuthTokenStoreIntegrationTest {

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisOAuthTokenStore tokenStore;
    private final List<String> keysToDelete = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String host = System.getenv().getOrDefault("OAUTH_REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("OAUTH_REDIS_PORT", "6379"));
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(host, port));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        tokenStore = new RedisOAuthTokenStore(redisTemplate);
    }

    @AfterEach
    void tearDown() {
        if (redisTemplate != null && !keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void twentyConcurrentAuthorizationCodeConsumersHaveOneWinner() throws Exception {
        String code = "it-code-" + UUID.randomUUID();
        String codeKey = RedisKeyUtil.oauthCode(code);
        String usedKey = RedisKeyUtil.oauthCodeUsed(code);
        String json = "{\"clientId\":\"client-1\",\"uid\":9}";
        keysToDelete.add(codeKey);
        keysToDelete.add(usedKey);
        redisTemplate.opsForValue().set(codeKey, json);

        int successes = runConcurrently(20,
                () -> tokenStore.consumeAuthorizationCode(code, json, 60L));

        assertEquals(1, successes);
        assertNull(redisTemplate.opsForValue().get(codeKey));
        assertEquals("1", redisTemplate.opsForValue().get(usedKey));
    }

    @Test
    void twentyConcurrentRefreshesAllIssueAccessAndKeepRefreshToken() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String oldRefresh = "it-old-refresh-" + suffix;
        String oldRefreshKey = RedisKeyUtil.oauthRefresh(oldRefresh);
        String indexKey = RedisKeyUtil.uidOauth(9L);
        String oldJson = "{\"uid\":9,\"clientId\":\"client-1\",\"scope\":\"user.read\"}";
        keysToDelete.add(oldRefreshKey);
        keysToDelete.add(indexKey);
        redisTemplate.opsForValue().set(oldRefreshKey, oldJson);
        redisTemplate.opsForSet().add(indexKey, "refresh:" + oldRefresh);

        List<OAuthTokenStore.RefreshAccessRequest> requests = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String access = "it-access-" + suffix + "-" + i;
            keysToDelete.add(RedisKeyUtil.oauthAccess(access));
            requests.add(new OAuthTokenStore.RefreshAccessRequest(
                    oldRefresh, oldJson,
                    access, "{\"kind\":\"access\",\"n\":" + i + "}", 60L,
                    9L));
        }

        List<Callable<Boolean>> actions = new ArrayList<>();
        for (OAuthTokenStore.RefreshAccessRequest request : requests) {
            actions.add(() -> tokenStore.issueAccessFromRefresh(request));
        }
        int successes = runConcurrently(actions);

        // 固定凭证模型：并发刷新全部成功签发 access，refresh 保留不动
        assertEquals(20, successes);
        assertEquals(oldJson, redisTemplate.opsForValue().get(oldRefreshKey));
        Set<String> members = redisTemplate.opsForSet().members(indexKey);
        assertNotNull(members);
        // 1 个 refresh 成员 + 20 个 access 成员
        assertEquals(21, members.size());
        assertTrue(members.contains("refresh:" + oldRefresh));

        long accessRecords = requests.stream()
                .filter(request -> redisTemplate.hasKey(RedisKeyUtil.oauthAccess(request.newAccessToken())))
                .count();
        assertEquals(20L, accessRecords);
    }

    @Test
    void scriptPreflightFailureLeavesRefreshTokenUntouched() {
        String suffix = UUID.randomUUID().toString();
        String oldRefresh = "it-old-refresh-" + suffix;
        String newAccess = "it-new-access-" + suffix;
        String oldKey = RedisKeyUtil.oauthRefresh(oldRefresh);
        String newAccessKey = RedisKeyUtil.oauthAccess(newAccess);
        String indexKey = RedisKeyUtil.uidOauth(9L);
        String oldJson = "{\"uid\":9,\"clientId\":\"client-1\"}";
        keysToDelete.add(oldKey);
        keysToDelete.add(newAccessKey);
        keysToDelete.add(indexKey);
        redisTemplate.opsForValue().set(oldKey, oldJson);
        // 制造错误类型：脚本期望反向索引为 Set。预检应在写 access 前返回失败。
        redisTemplate.opsForValue().set(indexKey, "wrong-type");

        boolean issued = tokenStore.issueAccessFromRefresh(new OAuthTokenStore.RefreshAccessRequest(
                oldRefresh, oldJson,
                newAccess, "{\"kind\":\"access\"}", 60L,
                9L));

        assertFalse(issued);
        assertEquals(oldJson, redisTemplate.opsForValue().get(oldKey));
        assertNull(redisTemplate.opsForValue().get(newAccessKey));
    }

    private int runConcurrently(int threads, Callable<Boolean> action) throws Exception {
        List<Callable<Boolean>> actions = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            actions.add(action);
        }
        return runConcurrently(actions);
    }

    /**
     * 使用起跑闩让所有工作线程尽量同时进入 Lua 调用。最终统计返回 true 的数量，
     * 精确验证 compare-and-delete 在真实 Redis 上只有一个赢家。
     */
    private int runConcurrently(List<? extends Callable<Boolean>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (Callable<Boolean> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return action.call();
                }));
            }
            ready.await();
            start.countDown();
            int successes = 0;
            for (Future<Boolean> future : futures) {
                if (Boolean.TRUE.equals(future.get())) {
                    successes++;
                }
            }
            return successes;
        } finally {
            executor.shutdownNow();
        }
    }
}
