package com.orange.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求工具类：IP 获取、token 解析
 *
 * @author UserCenter
 */
public final class RequestUtil {

    /** Authorization 请求头前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

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
     * 从请求解析用户 token：优先 Authorization: Bearer xxx，其次 UC-Token
     *
     * @param request 请求
     * @return token，未携带时返回 null
     */
    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String ucToken = request.getHeader("UC-Token");
        if (ucToken != null && !ucToken.isBlank()) {
            return ucToken.trim();
        }
        return null;
    }
}
