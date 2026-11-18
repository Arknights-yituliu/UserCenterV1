package com.orange.common.context;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.service.PatPrincipal;

/**
 * 当前 PAT 上下文（ThreadLocal）
 *
 * <p>由 {@link com.orange.interceptor.PatAuthInterceptor} 在请求进入时写入，只服务
 * {@code /pat/**}。与 {@link UserContext} 分开持有：后者放三个认证面共有的身份字段
 * （uid / clientId / scope），本类放 PAT 独有的令牌属性。</p>
 *
 * <p>存的是整个 {@link PatPrincipal} 而不是逐个字段的 ThreadLocal：令牌以后新增字段时
 * 只改 principal，不必动上下文类，也不会出现「新增字段忘记清理」导致的线程复用串号。</p>
 *
 * @author UserCenter
 */
public final class PatContext {

    private static final ThreadLocal<PatPrincipal> HOLDER = new ThreadLocal<>();

    private PatContext() {
    }

    /**
     * 写入当前 PAT 主体
     *
     * @param principal 令牌主体
     */
    public static void set(PatPrincipal principal) {
        HOLDER.set(principal);
    }

    /**
     * 获取当前 PAT 主体，缺失时按未登录处理
     *
     * @return 令牌主体
     */
    public static PatPrincipal require() {
        PatPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN, "缺少 PAT 上下文");
        }
        return principal;
    }

    /**
     * 清理上下文，防止线程复用导致数据串号
     */
    public static void clear() {
        HOLDER.remove();
    }
}
