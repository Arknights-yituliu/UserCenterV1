package com.orange.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置：仅对 OAuth 端点开放白名单来源
 *
 * <p>纯前端（SPA）接入方需要跨域调用 /oauth2/token、/oauth2/userinfo，
 * 白名单来源取配置 uc.oauth.allowed-origins（已登记客户端的网站 origin，逗号分隔）。</p>
 *
 * @author UserCenter
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许跨域访问的站点 origin 白名单（逗号分隔，来自 oauth_client 登记的网站域名） */
    @Value("${user-center.oauth.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 注册 CORS 规则：仅 /oauth2/** 开放，白名单化，不开放全部来源
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 未配置白名单时不开放任何跨域
        if (!StringUtils.hasText(allowedOrigins)) {
            return;
        }
        registry.addMapping("/oauth2/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
