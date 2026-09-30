package com.orange.common.config;

import com.orange.service.OAuthClientOriginCache;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS 跨域配置：所有请求路径均按已审批 Origin 白名单开放。
 *
 * <p>白名单来源取 oauth_client_origin 的运行时内存快照；不在白名单中的 Origin
 * 仍会由 Spring CORS 处理器直接拒绝。</p>
 *
 * @author UserCenter
 */
@Configuration
public class CorsConfig {

    /**
     * 创建动态 CORS 配置源。只把当前请求 Origin 放入响应配置，避免在请求路径上
     * 对完整白名单进行线性扫描；未审批的 Origin 会由 Spring CORS 处理器直接拒绝。
     *
     * @param originCache 已审批 Origin 缓存
     * @return 动态 CORS 配置源
     */
    @Bean
    public CorsConfigurationSource oauthCorsConfigurationSource(OAuthClientOriginCache originCache) {
        return request -> {
            CorsConfiguration configuration = new CorsConfiguration();
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            configuration.setAllowedOrigins(originCache.isAllowed(origin)
                    ? List.of(origin) : List.of());
            configuration.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
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
}
