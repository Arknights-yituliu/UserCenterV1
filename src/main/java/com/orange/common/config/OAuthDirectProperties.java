package com.orange.common.config;

import org.springframework.stereotype.Component;

/**
 * OAuth 旧系统直连登录、注册策略，直接在此类维护，不从外部配置绑定。
 *
 * @author UserCenter
 */
@Component
public class OAuthDirectProperties {

    /** 发起会话凭证有效期（秒）：5 分钟。 */
    private final long channelTtlSeconds = 300L;

    /** 一次性登录票据有效期（秒）：1 分钟。 */
    private final long ticketTtlSeconds = 60L;

    /** 发起会话：窗口内单 IP 请求次数上限。 */
    private final long sessionIpLimit = 60L;

    /** 发起会话：窗口内同一客户端与 IP 组合的请求次数上限。 */
    private final long sessionClientIpLimit = 20L;

    /** 发起会话限流窗口（秒）。 */
    private final long sessionWindowSeconds = 60L;

    /** 提交登录：窗口内单 IP 请求次数上限。 */
    private final long loginIpRequestLimit = 30L;

    /** 提交登录 IP 限流窗口（秒）。 */
    private final long loginIpRequestWindowSeconds = 60L;

    /** 同一 IP 或账号的登录失败统计窗口（秒）：15 分钟。 */
    private final long loginFailureWindowSeconds = 900L;

    /** 一级锁定的失败次数阈值。 */
    private final long loginFirstLockThreshold = 5L;

    /** 一级锁定时长（秒）。 */
    private final long loginFirstLockSeconds = 60L;

    /** 二级锁定的失败次数阈值。 */
    private final long loginSecondLockThreshold = 10L;

    /** 二级锁定时长（秒）。 */
    private final long loginSecondLockSeconds = 300L;

    /** 三级锁定的失败次数阈值。 */
    private final long loginThirdLockThreshold = 15L;

    /** 三级锁定时长（秒）。 */
    private final long loginThirdLockSeconds = 900L;

    /** 直连注册：窗口内单 IP 注册次数上限，与主站注册的计数桶独立。 */
    private final long registerIpLimit = 10L;

    /** 直连注册：窗口内同邮箱注册次数上限。 */
    private final long registerEmailLimit = 3L;

    /** 直连注册限流窗口（秒）：1 小时。 */
    private final long registerWindowSeconds = 3600L;

    public long getChannelTtlSeconds() {
        return channelTtlSeconds;
    }

    public long getTicketTtlSeconds() {
        return ticketTtlSeconds;
    }

    public long getSessionIpLimit() {
        return sessionIpLimit;
    }

    public long getSessionClientIpLimit() {
        return sessionClientIpLimit;
    }

    public long getSessionWindowSeconds() {
        return sessionWindowSeconds;
    }

    public long getLoginIpRequestLimit() {
        return loginIpRequestLimit;
    }

    public long getLoginIpRequestWindowSeconds() {
        return loginIpRequestWindowSeconds;
    }

    public long getLoginFailureWindowSeconds() {
        return loginFailureWindowSeconds;
    }

    public long getLoginFirstLockThreshold() {
        return loginFirstLockThreshold;
    }

    public long getLoginFirstLockSeconds() {
        return loginFirstLockSeconds;
    }

    public long getLoginSecondLockThreshold() {
        return loginSecondLockThreshold;
    }

    public long getLoginSecondLockSeconds() {
        return loginSecondLockSeconds;
    }

    public long getLoginThirdLockThreshold() {
        return loginThirdLockThreshold;
    }

    public long getLoginThirdLockSeconds() {
        return loginThirdLockSeconds;
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
