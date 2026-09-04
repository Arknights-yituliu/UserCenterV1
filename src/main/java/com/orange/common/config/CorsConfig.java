package com.orange.common.config;

import com.orange.service.OAuthClientOriginCache;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Collections;
import java.util.List;

/**
 * CORS 跨域配置：对需要浏览器跨域调用的端点开放白名单来源
 *
 * <p>开放范围：/oauth2/**（OAuth 换 token、userinfo）、/auth/**（登录、注册、验证码、重设密码）、
 * /user/**（用户自助接口）。白名单来源取 oauth_client_origin 的运行时内存快照。
 * /api/app/** 为服务端签名接口，刻意不开放跨域（AppSecret 不能暴露给浏览器）。</p>
 *
 * @author UserCenter
 */
@Configuration
public class CorsConfig {

    /** 允许跨域访问的端点路径（/api/app/** 刻意排除，仅服务端签名调用） */
    private static final String[] OPEN_PATHS = {"/oauth2/**", "/auth/**", "/user/**"};

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 创建动态 CORS 配置源。允许时只把当前请求 Origin 放入响应配置，避免在请求路径上
     * 对完整白名单进行线性扫描；未审批的 Origin 会由 Spring CORS 处理器直接拒绝。
     *
     * @param originCache 已审批 Origin 缓存
     * @return 动态 CORS 配置源
     */
    @Bean
    public CorsConfigurationSource oauthCorsConfigurationSource(OAuthClientOriginCache originCache) {
        return request -> {
            if (!isOpenPath(request)) {
                return null;
            }
            CorsConfiguration configuration = new CorsConfiguration();
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            configuration.setAllowedOrigins(originCache.isAllowed(origin)
                    ? List.of(origin) : Collections.emptyList());
            configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
            configuration.setAllowedHeaders(List.of("*"));
            configuration.setAllowCredentials(true);
            return configuration;
        };
    }

    /**
     * 在 MVC 拦截器之前处理预检和实际跨域请求。
     *
     * @param configurationSource 动态 CORS 配置源
     * @return CORS 过滤器
     */
    @Bean
    public CorsFilter corsFilter(
            @Qualifier("oauthCorsConfigurationSource") CorsConfigurationSource configurationSource) {
        return new CorsFilter(configurationSource);
    }

    private boolean isOpenPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        for (String pattern : OPEN_PATHS) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
