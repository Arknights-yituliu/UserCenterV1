package com.orange.service.impl;

import com.orange.common.util.RedisKeyUtil;
import com.orange.service.RevokeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 吊销编排服务实现：收拢「踢登录会话」与「吊销 OAuth 令牌」的 Redis 操作，
 * 作为按 uid 吊销的唯一实现点，供 revoke-user 等入口复用
 *
 * @author UserCenter
 */
@Service
public class RevokeServiceImpl implements RevokeService {

    /** uid 反向索引中 access_token 成员前缀 */
    private static final String OAUTH_ACCESS_MEMBER_PREFIX = "access:";

    /** uid 反向索引中 refresh_token 成员前缀 */
    private static final String OAUTH_REFRESH_MEMBER_PREFIX = "refresh:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造器注入依赖
     *
     * @param stringRedisTemplate Redis 客户端（删除会话/令牌及索引）
     */
    public RevokeServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 吊销指定用户在本系统内的全部凭证：先踢登录会话，再吊销 OAuth 令牌
     *
     * @param uid 用户 uid
     */
    @Override
    public void revokeUser(Long uid) {
        kickAllSessions(uid);
        revokeUserTokens(uid);
    }

    /**
     * 踢出用户全部登录会话：读 uid 反向索引直达各会话并删除，最后清理索引
     *
     * @param uid 用户 uid
     */
    @Override
    public void kickAllSessions(Long uid) {
        Set<String> tokens = stringRedisTemplate.opsForSet().members(RedisKeyUtil.uidSession(uid));
        if (tokens != null) {
            for (String token : tokens) {
                stringRedisTemplate.delete(RedisKeyUtil.token(token));
            }
        }
        stringRedisTemplate.delete(RedisKeyUtil.uidSession(uid));
    }

    /**
     * 吊销用户名下全部 OAuth 令牌：读 uid 反向索引，按成员类型删除令牌 key，
     * 最后清理索引（令牌已过期时删除幂等，成员一并移除，惰性清理过期残留）
     *
     * @param uid 用户 uid
     */
    @Override
    public void revokeUserTokens(Long uid) {
        String indexKey = RedisKeyUtil.uidOauth(uid);
        Set<String> members = stringRedisTemplate.opsForSet().members(indexKey);
        if (members == null || members.isEmpty()) {
            return;
        }
        for (String member : members) {
            if (member.startsWith(OAUTH_ACCESS_MEMBER_PREFIX)) {
                String token = member.substring(OAUTH_ACCESS_MEMBER_PREFIX.length());
                stringRedisTemplate.delete(RedisKeyUtil.oauthAccess(token));
            } else if (member.startsWith(OAUTH_REFRESH_MEMBER_PREFIX)) {
                String token = member.substring(OAUTH_REFRESH_MEMBER_PREFIX.length());
                stringRedisTemplate.delete(RedisKeyUtil.oauthRefresh(token));
            }
        }
        stringRedisTemplate.delete(indexKey);
    }
}
