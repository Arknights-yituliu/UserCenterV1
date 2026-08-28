package com.orange.interceptor;

import com.orange.common.context.OAuthTokenResolver;
import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RequestUtil;
import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * OAuth 认证拦截器
 *
 * <p>统一校验需要 OAuth access_token 的接口（Authorization: Bearer {token}），
 * 从令牌解析出 uid / client_id / scope 写入 {@link UserContext}，
 * 供 OAuth 接口（如 /oauth/userinfo）直接使用，避免各接口重复解析令牌。</p>
 *
 * @author UserCenter
 */
@Component
public class OAuthAuthInterceptor implements HandlerInterceptor {

    private final OAuthTokenResolver oauthTokenResolver;

    /**
     * 构造器注入依赖
     *
     * @param oauthTokenResolver OAuth 令牌解析器
     */
    public OAuthAuthInterceptor(OAuthTokenResolver oauthTokenResolver) {
        this.oauthTokenResolver = oauthTokenResolver;
    }

    /**
     * 请求处理前校验 OAuth access_token 并注入用户上下文
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return 是否放行
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检请求（OPTIONS）直接放行
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }

        String token = RequestUtil.resolveToken(request);
        if (token == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN, "缺少登录凭证");
        }

        OAuthTokenPrincipal principal = oauthTokenResolver.resolve(token);
        if (principal == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }

        UserContext.setUid(principal.getUid());
        UserContext.setClientId(principal.getClientId());
        UserContext.setScope(principal.getScope());
        return true;
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
}
