package com.orange.common.context;

import com.orange.common.exception.BusinessException;
import com.orange.common.enums.ResultCode;

/**
 * 当前登录用户上下文（ThreadLocal）
 *
 * <p>由用户认证拦截器在请求进入时写入，业务层通过 {@link #getUid()} 获取当前用户</p>
 *
 * @author UserCenter
 */
public final class UserContext {

    private static final ThreadLocal<Long> UID_HOLDER = new ThreadLocal<>();

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
    }
}
