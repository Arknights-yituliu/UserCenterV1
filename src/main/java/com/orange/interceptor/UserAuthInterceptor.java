package com.orange.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.dto.SessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 用户认证拦截器
 *
 * <p>校验请求携带的用户 token（Authorization: Bearer {token} 或 X-Token），
 * 实时查询 Redis 会话，保证"踢下线"立即生效；通过后将 uid 写入 {@link UserContext}</p>
 *
 * @author UserCenter
 */
@Component
public class UserAuthInterceptor implements HandlerInterceptor {

    /** Authorization 请求头前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param stringRedisTemplate Redis 客户端
     * @param objectMapper        JSON 序列化器
     */
    public UserAuthInterceptor(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 请求处理前校验登录态
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return 是否放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request);
        if (token == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN, "缺少登录凭证");
        }

        String sessionJson = stringRedisTemplate.opsForValue().get(RedisKeyUtil.token(token));
        if (sessionJson == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }

        try {
            SessionInfo session = objectMapper.readValue(sessionJson, SessionInfo.class);
            if (session == null || session.getUid() == null) {
                throw new BusinessException(ResultCode.NOT_LOGIN);
            }
            UserContext.setUid(session.getUid());
            return true;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
    }

    /**
     * 请求结束后清理线程上下文
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @param ex       异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 从请求头解析 token：优先 Authorization: Bearer xxx，其次 X-Token
     *
     * @param request 请求
     * @return token，未携带时返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String xToken = request.getHeader("X-Token");
        return (xToken == null || xToken.isBlank()) ? null : xToken.trim();
    }
}
