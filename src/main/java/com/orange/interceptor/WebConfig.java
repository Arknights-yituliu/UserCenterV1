package com.orange.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册拦截器
 *
 * <ul>
 *   <li>UserAuthInterceptor：仅校验用户会话（/user/** 用户侧接口）</li>
 *   <li>OAuthAuthInterceptor：仅校验 OAuth access_token（/oauth/** 中需要令牌的接口）</li>
 * </ul>
 *
 * <p>两条鉴权链路完全分离，互不纠缠：用户侧接口只认用户会话，
 * OAuth 资源接口只认 access_token，client_id 均由各自登录上下文提供。</p>
 *
 * @author UserCenter
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;
    private final OAuthAuthInterceptor oauthAuthInterceptor;

    /**
     * 构造器注入拦截器
     *
     * @param userAuthInterceptor 用户认证拦截器
     * @param oauthAuthInterceptor OAuth 认证拦截器
     */
    public WebConfig(UserAuthInterceptor userAuthInterceptor, OAuthAuthInterceptor oauthAuthInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
        this.oauthAuthInterceptor = oauthAuthInterceptor;
    }

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户侧接口：仅校验用户会话
        // （/oauth2/client/** 为开发者自助管理自己的 OAuth 客户端，同样需要用户会话）
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/user/**", "/auth/logout", "/oauth2/client/**");
        // OAuth 资源接口：需要 access_token 的接口统一走此拦截器
        // （/oauth2/userinfo 为 OAuthController 的用户信息端点，/oauth2/config/** 为 OAuth 令牌版用户配置）
        registry.addInterceptor(oauthAuthInterceptor)
                .addPathPatterns("/oauth2/userinfo", "/oauth2/config/**");
    }
}
