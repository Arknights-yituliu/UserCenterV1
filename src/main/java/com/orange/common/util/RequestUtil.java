package com.orange.common.util;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
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
     * 从请求解析用户 token：优先 Authorization: Bearer xxx，其次 TCL-Token（兼容 UC-Token）
     *
     * @param request 请求
     * @return token，未携带时返回 null
     */
    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return compatibleValue(request.getHeader("TCL-Token"),
                request.getHeader("UC-Token"), "TCL-Token / UC-Token");
    }

    /** 解析来源客户端标识，兼容旧 UC-Client-Id 请求头。 */
    public static String resolveClientId(HttpServletRequest request) {
        return compatibleValue(request.getHeader("TCL-Client-Id"),
                request.getHeader("UC-Client-Id"), "TCL-Client-Id / UC-Client-Id");
    }

    /** 解析跨站登录票据，兼容旧 uc_ticket 查询/表单参数。 */
    public static String resolveLoginTicket(HttpServletRequest request) {
        return compatibleValue(request.getParameter("tcl_ticket"),
                request.getParameter("uc_ticket"), "tcl_ticket / uc_ticket");
    }

    private static String compatibleValue(String current, String legacy, String field) {
        String currentValue = normalizeCompatibleValue(current);
        String legacyValue = normalizeCompatibleValue(legacy);
        if (currentValue != null && legacyValue != null && !currentValue.equals(legacyValue)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新旧参数值不一致（" + field + "）");
        }
        return currentValue != null ? currentValue : legacyValue;
    }

    private static String normalizeCompatibleValue(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
