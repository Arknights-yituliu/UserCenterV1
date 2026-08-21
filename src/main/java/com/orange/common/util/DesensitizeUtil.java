package com.orange.common.util;

/**
 * 数据脱敏工具类
 *
 * <p>对外返回用户信息时对敏感字段打码，避免完整邮箱等登录账号信息泄露</p>
 *
 * @author UserCenter
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /**
     * 邮箱脱敏：保留前缀前 4 位与 @ 后域名，中间打码
     *
     * <p>示例：user@example.com → user***@example.com；前缀不足 4 位时保留整个前缀；
     * 无 @ 或为 null/空白时原样返回。</p>
     *
     * @param email 原始邮箱
     * @return 脱敏后的邮箱
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return email;
        }
        int at = email.indexOf('@');
        // 非法邮箱格式原样返回，避免误伤
        if (at <= 0 || at == email.length() - 1) {
            return email;
        }
        int keep = Math.min(4, at);
        return email.substring(0, keep) + "***" + email.substring(at);
    }
}
