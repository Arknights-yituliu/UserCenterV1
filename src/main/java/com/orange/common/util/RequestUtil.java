package com.orange.common.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求工具类：IP 获取、token 解析
 *
 * @author UserCenter
 */
public final class RequestUtil {

    /** Authorization 请求头前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 会话 Cookie 名（纯前端 OAuth 场景，浏览器跳转自动携带） */
    private static final String SESSION_COOKIE = "uc_token";

    private RequestUtil() {
    }

    /**
     * 获取客户端真实 IP（优先取 X-Forwarded-For 首个值）
     *
     * @param request 请求
     * @return IP 地址
     */
    public static String getIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 从请求解析用户 token：优先 Authorization: Bearer xxx，其次 X-Token，最后 Cookie: uc_token
     *
     * @param request 请求
     * @return token，未携带时返回 null
     */
    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String xToken = request.getHeader("X-Token");
        if (xToken != null && !xToken.isBlank()) {
            return xToken.trim();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    String value = cookie.getValue();
                    return (value == null || value.isBlank()) ? null : value.trim();
                }
            }
        }
        return null;
    }
}
