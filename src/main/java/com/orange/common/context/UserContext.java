package com.orange.common.context;

import com.orange.common.enums.OAuthScope;
import com.orange.common.exception.BusinessException;
import com.orange.common.enums.ResultCode;

/**
 * 当前登录用户上下文（ThreadLocal）
 *
 * <p>由用户认证拦截器 / OAuth 认证拦截器在请求进入时写入，业务层通过 {@link #getUid()} 获取当前用户，
 * 通过 {@link #getClientId()} 获取来源客户端标识，通过 {@link #getScope()} 获取 OAuth 授权范围</p>
 *
 * @author UserCenter
 */
public final class UserContext {

    private static final ThreadLocal<Long> UID_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> CLIENT_ID_HOLDER = new ThreadLocal<>();

    private static final ThreadLocal<String> SCOPE_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 写入当前用户 uid
     *
     * @param uid 用户 uid
     */
    public static void setUid(Long uid) {
        UID_HOLDER.set(uid);
    }

    /**
     * 获取当前用户 uid
     *
     * @return 用户 uid
     */
    public static Long getUid() {
        return UID_HOLDER.get();
    }

    /**
     * 写入来源客户端标识
     *
     * @param clientId 来源客户端 id（对应 oauth_client.client_id）
     */
    public static void setClientId(String clientId) {
        CLIENT_ID_HOLDER.set(clientId);
    }

    /**
     * 获取来源客户端标识
     *
     * @return 来源客户端 id，缺失时返回 null
     */
    public static String getClientId() {
        return CLIENT_ID_HOLDER.get();
    }

    /**
     * 写入 OAuth 授权范围
     *
     * @param scope 授权范围（逗号分隔），用户会话场景为空
     */
    public static void setScope(String scope) {
        SCOPE_HOLDER.set(scope);
    }

    /**
     * 获取 OAuth 授权范围
     *
     * @return 授权范围，非 OAuth 场景返回 null
     */
    public static String getScope() {
        return SCOPE_HOLDER.get();
    }

    /**
     * 校验当前 OAuth 令牌已获得指定 scope。
     *
     * <p>令牌 scope 含元范围 {@link OAuthScope#ALL}（由管理员手工写库授予的机密客户端）时，
     * 视为已获得全部范围，直接放行。</p>
     *
     * @param required 所需授权范围
     */
    public static void requireScope(OAuthScope required) {
        String currentScope = SCOPE_HOLDER.get();
        if (currentScope == null || currentScope.isBlank()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "缺少授权范围: " + required.getCode());
        }
        String allCode = OAuthScope.ALL.getCode();
        boolean granted = java.util.Arrays.stream(currentScope.split(","))
                .map(String::trim)
                .anyMatch(value -> required.getCode().equals(value) || allCode.equals(value));
        if (!granted) {
            throw new BusinessException(ResultCode.FORBIDDEN, "缺少授权范围: " + required.getCode());
        }
    }

    /**
     * 获取当前用户 uid，未登录时抛出异常
     *
     * @return 用户 uid
     */
    public static Long requireUid() {
        Long uid = UID_HOLDER.get();
        if (uid == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
        return uid;
    }

    /**
     * 清理上下文，防止线程复用导致数据串号
     */
    public static void clear() {
        UID_HOLDER.remove();
        CLIENT_ID_HOLDER.remove();
        SCOPE_HOLDER.remove();
    }
}
