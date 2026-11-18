package com.orange.interceptor;

import com.orange.common.context.PatContext;
import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.PatUtil;
import com.orange.common.util.RequestUtil;
import com.orange.service.PatPrincipal;
import com.orange.service.PatService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * PAT 认证拦截器
 *
 * <p>只挂在 {@code /pat/**} 上：从请求解析 PAT 明文，按明文单次查库，
 * 命中且未过期才放行。身份三元组写入 {@link UserContext}（使控制器可直接复用
 * {@code requireScope}），令牌属性整体放进 {@link PatContext}。</p>
 *
 * <p>与 {@link OAuthAuthInterceptor} 完全独立：PAT 不进入 {@code /oauth2/**} 的
 * access_token 解析链路，两套凭证互不识别。</p>
 *
 * @author UserCenter
 */
@Component
public class PatAuthInterceptor implements HandlerInterceptor {

    private final PatService patService;

    /**
     * 构造器注入依赖
     *
     * @param patService PAT 服务
     */
    public PatAuthInterceptor(PatService patService) {
        this.patService = patService;
    }

    /**
     * 请求处理前校验 PAT 并注入用户上下文
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
        if (!PatUtil.isPat(token) && !PatUtil.isLegacyToken(token)) {
            throw new BusinessException(ResultCode.NOT_LOGIN, "缺少 PAT");
        }

        PatPrincipal principal = patService.resolve(token);
        if (principal == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }

        UserContext.setUid(principal.getUid());
        UserContext.setClientId(principal.getClientId());
        UserContext.setScope(principal.getScope());
        PatContext.set(principal);
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
        PatContext.clear();
    }
}
