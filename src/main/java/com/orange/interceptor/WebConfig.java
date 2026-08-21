package com.orange.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册拦截器
 *
 * <ul>
 *   <li>UserAuthInterceptor：校验用户登录态</li>
 * </ul>
 *
 * @author UserCenter
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;

    /**
     * 构造器注入拦截器
     *
     * @param userAuthInterceptor 用户认证拦截器
     */
    public WebConfig(UserAuthInterceptor userAuthInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
    }

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户登录态校验：用户侧接口
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/user/**", "/auth/logout");
    }
}
