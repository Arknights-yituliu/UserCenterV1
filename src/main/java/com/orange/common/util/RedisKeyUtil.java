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

    /** 邮箱验证码 key 前缀：tcl:code:{email} */
    private static final String PREFIX_CODE = "tcl:code:";

    /** 登录失败计数 key 前缀：tcl:login:fail:{account} */
    private static final String PREFIX_LOGIN_FAIL = "tcl:login:fail:";

    /** 登录锁定 key 前缀：tcl:lock:{account} */
    private static final String PREFIX_LOGIN_LOCK = "tcl:lock:";

    /** 通用限流 key 前缀：tcl:rate:{biz}:{target} */
    private static final String PREFIX_RATE = "tcl:rate:";

    /** OAuth 授权码 key 前缀：tcl:oauth:code:{code} */
    private static final String PREFIX_OAUTH_CODE = "tcl:oauth:code:";

    /** OAuth 授权码已使用标记 key 前缀：tcl:oauth:code:used:{code} */
    private static final String PREFIX_OAUTH_CODE_USED = "tcl:oauth:code:used:";

    /** OAuth access_token key 前缀：uc:oauth:access:{token} */
    private static final String PREFIX_OAUTH_ACCESS = "uc:oauth:access:";

    /** OAuth refresh_token key 前缀：uc:oauth:refresh:{token} */
    private static final String PREFIX_OAUTH_REFRESH = "uc:oauth:refresh:";

    /** OAuth refresh_token 轮转墓碑 key 前缀：uc:oauth:refresh:used:{token}（值为家族信息，用于识别凭证重放） */
    private static final String PREFIX_OAUTH_REFRESH_USED = "uc:oauth:refresh:used:";

    /** OAuth refresh_token 宽限期幂等副本 key 前缀：uc:oauth:refresh:next:{token}（值为换发出的新 refresh_token） */
    private static final String PREFIX_OAUTH_REFRESH_NEXT = "uc:oauth:refresh:next:";

    /** OAuth 令牌家族索引 key 前缀：uc:oauth:family:{familyId}（Set，成员为同族 access/refresh） */
    private static final String PREFIX_OAUTH_FAMILY = "uc:oauth:family:";

    /** OAuth 跨站登录票据 key 前缀：tcl:oauth:ticket:{ticket} */
    private static final String PREFIX_OAUTH_TICKET = "tcl:oauth:ticket:";

    /** OAuth 跨站登录票据已使用标记 key 前缀：tcl:oauth:ticket:used:{ticket} */
    private static final String PREFIX_OAUTH_TICKET_USED = "tcl:oauth:ticket:used:";

    /** OAuth 授权确认单 key 前缀：tcl:oauth:consent:{pendingId} */
    private static final String PREFIX_OAUTH_CONSENT = "tcl:oauth:consent:";

    /** OAuth 授权确认单已使用标记 key 前缀：tcl:oauth:consent:used:{pendingId} */
    private static final String PREFIX_OAUTH_CONSENT_USED = "tcl:oauth:consent:used:";

    /** 用户会话反向索引 key 前缀：uc:uid:session:{uid}（Set，成员为会话 token） */
    private static final String PREFIX_UID_SESSION = "uc:uid:session:";

    /** 用户 OAuth 令牌反向索引 key 前缀：uc:uid:oauth:{uid}（Set，成员为带类型前缀的令牌） */
    private static final String PREFIX_UID_OAUTH = "uc:uid:oauth:";

    /** 直连登录发起会话 key 前缀：tcl:oauth:direct:channel:{channel}（前端登录的前置凭证） */
    private static final String PREFIX_DIRECT_CHANNEL = "tcl:oauth:direct:channel:";

    /** 直连登录一次性票据 key 前缀：tcl:oauth:direct:ticket:{ticket}（前端登录成功后的凭证） */
    private static final String PREFIX_DIRECT_TICKET = "tcl:oauth:direct:ticket:";

    /** 直连登录票据已使用标记 key 前缀：tcl:oauth:direct:used:{ticket} */
    private static final String PREFIX_DIRECT_USED = "tcl:oauth:direct:used:";

    /** 迁移兑换请求防重放 key 前缀：tcl:oauth:migrate:nonce:{nonce} */
    private static final String PREFIX_OAUTH_MIGRATE_NONCE = "tcl:oauth:migrate:nonce:";

    /** 迁移凭证当前映射 key 前缀：uc:oauth:migrate:current:{clientId}:{uid}（值为上一轮迁移签发的 refresh_token） */
    private static final String PREFIX_OAUTH_MIGRATE_CURRENT = "uc:oauth:migrate:current:";

    /** 个人访问令牌校验缓存 key 前缀：tlc:pat:{token}（阶段二按需启用） */
    private static final String PREFIX_PAT = "tlc:pat:";

    /** 用户 PAT 索引 key 前缀：tlc:uid:pat:{uid}（Set，成员为令牌明文，阶段二按需启用） */
    private static final String PREFIX_UID_PAT = "tlc:uid:pat:";

    /** PAT 校验未命中负缓存 key 前缀：tlc:pat:miss:{token}（60s，阶段二按需启用） */
    private static final String PREFIX_PAT_MISS = "tlc:pat:miss:";

    /** PAT 校验单飞锁 key 前缀：tlc:pat:lock:{token}（5s，阶段二按需启用） */
    private static final String PREFIX_PAT_LOCK = "tlc:pat:lock:";

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

    /**
     * OAuth refresh_token 轮转墓碑 key（旧 refresh 被换发后写入，长期保留至旧凭证原到期时间）
     *
     * @param token 已被轮转的旧 refresh_token 明文
     * @return Redis key
     */
    public static String oauthRefreshUsed(String token) {
        return PREFIX_OAUTH_REFRESH_USED + token;
    }

    /**
     * OAuth refresh_token 宽限期幂等副本 key（仅在宽限期内存在，用于响应丢失后的重试）
     *
     * @param token 已被轮转的旧 refresh_token 明文
     * @return Redis key
     */
    public static String oauthRefreshNext(String token) {
        return PREFIX_OAUTH_REFRESH_NEXT + token;
    }

    /**
     * OAuth 令牌家族索引 key（Set，成员为带类型前缀的令牌，用于整族吊销）
     *
     * @param familyId 令牌家族标识
     * @return Redis key
     */
    public static String oauthFamily(String familyId) {
        return PREFIX_OAUTH_FAMILY + familyId;
    }

    /**
     * OAuth access_token key 前缀（按客户端批量扫描清理时使用）
     *
     * @return 前缀（含结尾冒号）
     */
    public static String oauthAccessPrefix() {
        return PREFIX_OAUTH_ACCESS;
    }

    /**
     * OAuth refresh_token key 前缀（按客户端批量扫描清理时使用）
     *
     * @return 前缀（含结尾冒号）
     */
    public static String oauthRefreshPrefix() {
        return PREFIX_OAUTH_REFRESH;
    }

    /**
     * OAuth 跨站登录票据 key
     *
     * @param ticket 一次性登录票据
     * @return Redis key
     */
    public static String oauthTicket(String ticket) {
        return PREFIX_OAUTH_TICKET + ticket;
    }

    /**
     * OAuth 跨站登录票据已使用标记 key（并发/重放下仅一次成功）
     *
     * @param ticket 一次性登录票据
     * @return Redis key
     */
    public static String oauthTicketUsed(String ticket) {
        return PREFIX_OAUTH_TICKET_USED + ticket;
    }

    /**
     * OAuth 授权确认单 key（存放待确认的授权参数）
     *
     * @param pendingId 确认单 ID（随机不可猜）
     * @return Redis key
     */
    public static String oauthConsent(String pendingId) {
        return PREFIX_OAUTH_CONSENT + pendingId;
    }

    /**
     * OAuth 授权确认单已使用标记 key（并发/重复提交下仅一次生效）
     *
     * @param pendingId 确认单 ID
     * @return Redis key
     */
    public static String oauthConsentUsed(String pendingId) {
        return PREFIX_OAUTH_CONSENT_USED + pendingId;
    }

    /**
     * 用户会话反向索引 key（Set，成员为该 uid 名下的会话 token）
     *
     * @param uid 用户 uid
     * @return Redis key
     */
    public static String uidSession(Long uid) {
        return PREFIX_UID_SESSION + uid;
    }

    /**
     * 用户 OAuth 令牌反向索引 key（Set，成员为带类型前缀的令牌）
     *
     * @param uid 用户 uid
     * @return Redis key
     */
    public static String uidOauth(Long uid) {
        return PREFIX_UID_OAUTH + uid;
    }

    /**
     * 直连登录发起会话 key（旧系统后端换取的前置凭证，前端需携带才能调直连登录）
     *
     * @param channel 发起会话凭证
     * @return Redis key
     */
    public static String directChannel(String channel) {
        return PREFIX_DIRECT_CHANNEL + channel;
    }

    /**
     * 直连登录一次性票据 key（前端登录成功后换取，旧系统后端凭它兑换用户信息）
     *
     * @param ticket 一次性登录票据
     * @return Redis key
     */
    public static String directTicket(String ticket) {
        return PREFIX_DIRECT_TICKET + ticket;
    }

    /**
     * 直连登录票据已使用标记 key（并发/重放下仅一次成功）
     *
     * @param ticket 一次性登录票据
     * @return Redis key
     */
    public static String directUsed(String ticket) {
        return PREFIX_DIRECT_USED + ticket;
    }

    /**
     * 迁移兑换请求防重放 key（一次性 nonce）
     *
     * @param nonce 请求随机串
     * @return Redis key
     */
    public static String oauthMigrateNonce(String nonce) {
        return PREFIX_OAUTH_MIGRATE_NONCE + nonce;
    }

    /**
     * 迁移凭证当前映射 key（值为上一轮迁移签发的 refresh_token，供下一轮替换时撤销）
     *
     * @param clientId 客户端 ID
     * @param uid      用户 uid
     * @return Redis key
     */
    public static String oauthMigrateCurrent(String clientId, Long uid) {
        return PREFIX_OAUTH_MIGRATE_CURRENT + clientId + ":" + uid;
    }

    /**
     * 个人访问令牌校验缓存 key（值为 uid/clientId/scope/payload 的 JSON）
     *
     * <p>阶段一不写该键：PAT 调用量远低于普通接口，按明文查一次库即可。留出方法是
     * 为了让阶段二启用缓存时不必再改工具类。key 直接带令牌明文是「全链路明文」取舍的
     * 一部分，一旦启用，Redis 也需按敏感数据源管理（禁止键扫描结果外泄、禁止落日志）。</p>
     *
     * @param token 令牌明文
     * @return Redis key
     */
    public static String pat(String token) {
        return PREFIX_PAT + token;
    }

    /**
     * 用户 PAT 索引 key（Set，成员为令牌明文，供按 uid 批量清理）
     *
     * @param uid 用户 uid
     * @return Redis key
     */
    public static String uidPat(Long uid) {
        return PREFIX_UID_PAT + uid;
    }

    /**
     * PAT 校验未命中负缓存 key（60s，避免无效凭证反复打库）
     *
     * @param token 令牌明文
     * @return Redis key
     */
    public static String patMiss(String token) {
        return PREFIX_PAT_MISS + token;
    }

    /**
     * PAT 校验单飞锁 key（5s，同一明文并发回源只放一个请求过）
     *
     * @param token 令牌明文
     * @return Redis key
     */
    public static String patLock(String token) {
        return PREFIX_PAT_LOCK + token;
    }
}
