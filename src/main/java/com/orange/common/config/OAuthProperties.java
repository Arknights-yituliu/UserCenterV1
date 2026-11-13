package com.orange.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OAuth2 授权服务器参数。
 * 邮箱和页面地址读取顶层 oauth 配置，有效期参数直接在此类维护。
 *
 * @author UserCenter
 */
@Component
public class OAuthProperties {

    /** OAuth 客户端注册或更新后的管理员通知邮箱；留空则不发送。 */
    private final String adminEmail;

    /** access_token 默认有效期（秒）：2 小时，客户端可单独指定。 */
    private final long accessTokenTtlSeconds = 7200L;

    /** refresh_token 默认有效期（秒）：90 天，客户端可单独指定。 */
    private final long refreshTokenTtlSeconds = 7776000L;

    /**
     * refresh_token 轮转宽限期（秒），建议不小于客户端网络重试窗口。
     * 宽限期内重放旧凭证返回同一份新凭证，超出后作废整条令牌家族。
     */
    private final long refreshRotationGraceSeconds = 60L;

    /** 授权码有效期（秒）：5 分钟。 */
    private final long authorizationCodeTtlSeconds = 300L;

    /** authorize 未登录时跳转的登录页地址；留空则返回未登录错误（80001）。 */
    private final String loginPageUrl;

    /** 跨站登录票据有效期（秒）：5 分钟，一次性使用。 */
    private final long loginTicketTtlSeconds = 300L;

    /** requireAuthConsent=1 的客户端授权时跳转的确认页地址。 */
    private final String consentPageUrl;

    /** 授权确认单有效期（秒）：5 分钟。 */
    private final long consentTtlSeconds = 300L;

    public OAuthProperties(
            @Value("${oauth.admin-email:}") String adminEmail,
            @Value("${oauth.login-page-url:}") String loginPageUrl,
            @Value("${oauth.consent-page-url:}") String consentPageUrl) {
        this.adminEmail = adminEmail;
        this.loginPageUrl = loginPageUrl;
        this.consentPageUrl = consentPageUrl;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public long getRefreshRotationGraceSeconds() {
        return refreshRotationGraceSeconds;
    }

    public long getAuthorizationCodeTtlSeconds() {
        return authorizationCodeTtlSeconds;
    }

    public String getLoginPageUrl() {
        return loginPageUrl;
    }

    public long getLoginTicketTtlSeconds() {
        return loginTicketTtlSeconds;
    }

    public String getConsentPageUrl() {
        return consentPageUrl;
    }

    public long getConsentTtlSeconds() {
        return consentTtlSeconds;
    }
}
