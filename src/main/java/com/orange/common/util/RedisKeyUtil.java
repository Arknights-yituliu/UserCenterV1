package com.orange.common.util;

/**
 * Redis Key 统一构建工具
 *
 * <p>统一管理 key 前缀，避免散落各处导致冲突</p>
 *
 * @author UserCenter
 */
public final class RedisKeyUtil {

    /** 会话 key 前缀：uc:token:{token} */
    private static final String PREFIX_TOKEN = "uc:token:";

    /** 邮箱验证码 key 前缀：uc:code:{email} */
    private static final String PREFIX_CODE = "uc:code:";

    /** 防重放 nonce key 前缀：uc:nonce:{nonce} */
    private static final String PREFIX_NONCE = "uc:nonce:";

    /** 登录失败计数 key 前缀：uc:login:fail:{account} */
    private static final String PREFIX_LOGIN_FAIL = "uc:login:fail:";

    /** 登录锁定 key 前缀：uc:lock:{account} */
    private static final String PREFIX_LOGIN_LOCK = "uc:lock:";

    /** 通用限流 key 前缀：uc:rate:{biz}:{target} */
    private static final String PREFIX_RATE = "uc:rate:";

    /** OAuth 授权码 key 前缀：uc:oauth:code:{code} */
    private static final String PREFIX_OAUTH_CODE = "uc:oauth:code:";

    /** OAuth 授权码已使用标记 key 前缀：uc:oauth:code:used:{code} */
    private static final String PREFIX_OAUTH_CODE_USED = "uc:oauth:code:used:";

    /** OAuth access_token key 前缀：uc:oauth:access:{token} */
    private static final String PREFIX_OAUTH_ACCESS = "uc:oauth:access:";

    /** OAuth refresh_token key 前缀：uc:oauth:refresh:{token} */
    private static final String PREFIX_OAUTH_REFRESH = "uc:oauth:refresh:";

    private RedisKeyUtil() {
    }

    /**
     * 用户会话 key
     *
     * @param token 会话 token
     * @return Redis key
     */
    public static String token(String token) {
        return PREFIX_TOKEN + token;
    }

    /**
     * 邮箱验证码 key
     *
     * @param email 邮箱
     * @return Redis key
     */
    public static String code(String email) {
        return PREFIX_CODE + email;
    }

    /**
     * 防重放 nonce key
     *
     * @param nonce 一次性随机数
     * @return Redis key
     */
    public static String nonce(String nonce) {
        return PREFIX_NONCE + nonce;
    }

    /**
     * 登录失败计数 key
     *
     * @param account 登录账号（邮箱/用户名）
     * @return Redis key
     */
    public static String loginFail(String account) {
        return PREFIX_LOGIN_FAIL + account;
    }

    /**
     * 登录锁定 key
     *
     * @param account 登录账号（邮箱/用户名）
     * @return Redis key
     */
    public static String loginLock(String account) {
        return PREFIX_LOGIN_LOCK + account;
    }

    /**
     * 通用限流 key
     *
     * @param biz    业务标识（如 send-code / login）
     * @param target 限流目标（IP 或账号）
     * @return Redis key
     */
    public static String rate(String biz, String target) {
        return PREFIX_RATE + biz + ":" + target;
    }

    /**
     * OAuth 一次性授权码 key
     *
     * @param code 授权码
     * @return Redis key
     */
    public static String oauthCode(String code) {
        return PREFIX_OAUTH_CODE + code;
    }

    /**
     * OAuth 授权码已使用标记 key（用于并发下防重复兑换）
     *
     * @param code 授权码
     * @return Redis key
     */
    public static String oauthCodeUsed(String code) {
        return PREFIX_OAUTH_CODE_USED + code;
    }

    /**
     * OAuth access_token key
     *
     * @param token 访问令牌
     * @return Redis key
     */
    public static String oauthAccess(String token) {
        return PREFIX_OAUTH_ACCESS + token;
    }

    /**
     * OAuth refresh_token key
     *
     * @param token 刷新令牌
     * @return Redis key
     */
    public static String oauthRefresh(String token) {
        return PREFIX_OAUTH_REFRESH + token;
    }
}
