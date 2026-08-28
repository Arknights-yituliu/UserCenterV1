package com.orange.common.context;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.util.RedisKeyUtil;
import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * OAuth access_token 解析器：从 Redis 读取令牌记录并解析出用户上下文
 *
 * <p>被用户认证拦截器与 OAuth 认证拦截器共用，避免两处维护 OAuth 令牌解析逻辑；
 * 与 {@link com.orange.service.OAuthTokenService#resolveAccessToken(String)} 不同的是，
 * 无效令牌返回 null 而非抛异常，由调用方决定处理方式。</p>
 *
 * @author UserCenter
 */
@Component
public class OAuthTokenResolver {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     */
    public OAuthTokenResolver(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 OAuth access_token
     *
     * @param accessToken 访问令牌
     * @return 令牌主体（uid/clientId/scope），令牌缺失或无效时返回 null
     */
    public OAuthTokenPrincipal resolve(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return null;
        }
        String json = stringRedisTemplate.opsForValue().get(RedisKeyUtil.oauthAccess(accessToken));
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> record = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {
                    });
            Object uidObj = record.get("uid");
            if (uidObj == null) {
                return null;
            }
            return new OAuthTokenPrincipal(((Number) uidObj).longValue(),
                    (String) record.get("clientId"), (String) record.get("scope"));
        } catch (IOException e) {
            return null;
        }
    }
}
