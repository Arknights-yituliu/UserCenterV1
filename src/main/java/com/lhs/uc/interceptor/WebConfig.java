package com.lhs.uc.interceptor;

import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册过滤器与拦截器
 *
 * <ul>
 *   <li>ContentCachingFilter：缓存 /api/app/** 请求体供签名校验使用</li>
 *   <li>AppSignInterceptor：校验接入方签名</li>
 *   <li>UserAuthInterceptor：校验用户登录态</li>
 * </ul>
 *
 * @author UserCenter
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppSignInterceptor appSignInterceptor;
    private final UserAuthInterceptor userAuthInterceptor;

    /**
     * 构造器注入拦截器
     *
     * @param appSignInterceptor  接入方签名拦截器
     * @param userAuthInterceptor 用户认证拦截器
     */
    public WebConfig(AppSignInterceptor appSignInterceptor, UserAuthInterceptor userAuthInterceptor) {
        this.appSignInterceptor = appSignInterceptor;
        this.userAuthInterceptor = userAuthInterceptor;
    }

    /**
     * 注册请求体缓存过滤器（仅 /api/app/**，签名校验需要读取请求体）
     *
     * @return 过滤器注册器
     */
    @Bean
    public FilterRegistrationBean<Filter> contentCachingFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ContentCachingFilter());
        registration.addUrlPatterns("/api/app/*");
        registration.setName("contentCachingFilter");
        registration.setOrder(1);
        return registration;
    }

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 接入方签名校验：/api/app/**
        registry.addInterceptor(appSignInterceptor)
                .addPathPatterns("/api/app/**");

        // 用户登录态校验：用户侧接口
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/user/**", "/auth/logout");
    }
}
