package com.orange.common.config;

import org.springframework.stereotype.Component;

/**
 * 用户认证与验证码策略，直接在此类维护，不从外部配置绑定。
 * 登录失败锁定与注册限流策略由主站与 OAuth 直连认证统一使用。
 *
 * @author UserCenter
 */
@Component
public class UserAccentProperties {

    /** 会话有效期（秒）：90 天。 */
    private final long sessionTtlSeconds = 7776000L;

    /** 验证码有效期（秒）：5 分钟。 */
    private final long codeTtlSeconds = 300L;

    /** 同 IP 发送验证码的最小间隔（秒）：1 分钟。 */
    private final long codeSendIntervalSeconds = 60L;

    /** 同邮箱发送验证码的最小间隔（秒）：1 分钟。 */
    private final long emailSendIntervalSeconds = 60L;

    /** 主站与直连认证共用：登录失败 5 次后锁定，仅保留一级锁定。 */
    private final long loginFailLimit = 5L;

    /** 共用登录失败计数窗口（秒）：15 分钟，与锁定时长独立。 */
    private final long loginFailWindowSeconds = 900L;

    /** 达到失败阈值后的统一登录锁定时长（秒）：30 分钟。 */
    private final long loginLockSeconds = 1800L;

    /** 主站登录：窗口内单 IP 允许的登录尝试次数，防止换账号的密码喷洒。 */
    private final long loginIpLimit = 20L;

    /** 主站登录 IP 限流窗口（秒）。 */
    private final long loginIpWindowSeconds = 60L;

    /** 主站与直连注册共用：窗口内单 IP 允许的注册尝试次数。 */
    private final long registerIpLimit = 15L;

    /** 主站与直连注册共用：窗口内同邮箱允许的注册尝试次数。 */
    private final long registerEmailLimit = 5L;

    /** 共用注册限流窗口（秒）：1 小时，主站与直连注册的计数桶各自独立。 */
    private final long registerWindowSeconds = 3600L;

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public long getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public long getCodeSendIntervalSeconds() {
        return codeSendIntervalSeconds;
    }

    public long getEmailSendIntervalSeconds() {
        return emailSendIntervalSeconds;
    }

    public long getLoginFailLimit() {
        return loginFailLimit;
    }

    public long getLoginFailWindowSeconds() {
        return loginFailWindowSeconds;
    }

    public long getLoginLockSeconds() {
        return loginLockSeconds;
    }

    public long getLoginIpLimit() {
        return loginIpLimit;
    }

    public long getLoginIpWindowSeconds() {
        return loginIpWindowSeconds;
    }

    public long getRegisterIpLimit() {
        return registerIpLimit;
    }

    public long getRegisterEmailLimit() {
        return registerEmailLimit;
    }

    public long getRegisterWindowSeconds() {
        return registerWindowSeconds;
    }
}
