package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.service.RevokeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
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

    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param stringRedisTemplate Redis 客户端（删除会话/令牌及索引）
     * @param objectMapper        JSON 序列化器（读取 refresh_token 记录以联动吊销派生令牌）
     */
    public RevokeServiceImpl(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
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
     * refresh_token 连带吊销其派生出的 access_token，最后清理索引
     * （令牌已过期时删除幂等，成员一并移除，惰性清理过期残留）
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
                // 连带吊销 refresh_token 派生出的 access_token（令牌已过期时跳过）
                Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthRefresh(token));
                if (record != null) {
                    String derivedAccess = (String) record.get("accessToken");
                    if (StringUtils.hasText(derivedAccess)) {
                        stringRedisTemplate.delete(RedisKeyUtil.oauthAccess(derivedAccess));
                    }
                }
                stringRedisTemplate.delete(RedisKeyUtil.oauthRefresh(token));
            }
        }
        stringRedisTemplate.delete(indexKey);
    }

    /**
     * 从 Redis 读取 JSON 并反序列化为 Map（读取 refresh_token 记录用）
     *
     * @param key Redis key
     * @return 反序列化结果，key 不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String key) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "OAuth 数据反序列化失败");
        }
    }
}
