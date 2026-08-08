package com.orange.interceptor;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;

/**
 * 请求体缓存过滤器
 *
 * <p>将请求包装为 ContentCachingRequestWrapper，使签名校验读取请求体后，
 * Controller 仍可正常读取 body</p>
 *
 * @author UserCenter
 */
public class ContentCachingFilter implements Filter {

    /**
     * 包装请求并放行
     *
     * @param request  请求
     * @param response 响应
     * @param chain    过滤器链
     * @throws IOException      IO 异常
     * @throws ServletException Servlet 异常
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (httpRequest instanceof ContentCachingRequestWrapper) {
            chain.doFilter(httpRequest, response);
            return;
        }
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(httpRequest);
        chain.doFilter(wrapper, response);
    }
}
