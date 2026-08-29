package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 跨站登录票据响应：登录页换取短时一次性票据，供跳转授权地址时携带
 *
 * @author UserCenter
 */
public class LoginTicketVO {

    /** 一次性登录票据 */
    private String ticket;

    /** 票据有效期（秒），对外输出为 expires_in（与 OAuth 令牌响应风格统一） */
    @JsonProperty("expires_in")
    private long expiresIn;

    public String getTicket() {
        return ticket;
    }

    public void setTicket(String ticket) {
        this.ticket = ticket;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    /**
     * 旧版兼容输出：与 expires_in 同值，供已按 expiresIn 解析的旧接入方过渡，
     * 待接入方全部迁移后移除
     *
     * @return 票据有效期（秒）
     */
    @JsonProperty("expiresIn")
    public long getExpiresInCompat() {
        return expiresIn;
    }
}
