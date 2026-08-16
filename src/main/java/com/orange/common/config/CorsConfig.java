package com.orange.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 跨域配置：对需要浏览器跨域调用的端点开放白名单来源
 *
 * <p>开放范围：/oauth2/**（OAuth 换 token、userinfo）、/auth/**（登录、注册、验证码、重设密码）、
 * /user/**（用户自助接口）。白名单来源取配置 user-center.oauth.allowed-origins（逗号分隔）。
 * /api/app/** 为服务端签名接口，刻意不开放跨域（AppSecret 不能暴露给浏览器）。</p>
 *
 * @author UserCenter
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许跨域访问的端点路径（/api/app/** 刻意排除，仅服务端签名调用） */
    private static final String[] OPEN_PATHS = {"/oauth2/**", "/auth/**", "/user/**"};

    /** 允许跨域访问的站点 origin 白名单（逗号分隔，来自 oauth_client 登记的网站域名） */
    @Value("${user-center.oauth.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 注册 CORS 规则：仅对开放路径生效，白名单化，不开放全部来源
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 未配置白名单时不开放任何跨域
        if (!StringUtils.hasText(allowedOrigins)) {
            return;
        }
        String[] origins = allowedOrigins.split(",");
        for (String path : OPEN_PATHS) {
            registry.addMapping(path)
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true);
        }
    }
}
