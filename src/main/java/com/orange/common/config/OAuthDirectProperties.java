package com.orange.common.config;

import org.springframework.stereotype.Component;

/**
 * OAuth 旧系统直连认证策略，直接在此类维护，不从外部配置绑定。
 * 登录失败锁定与注册限流策略统一由 {@link UserAccentProperties} 维护。
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
}
