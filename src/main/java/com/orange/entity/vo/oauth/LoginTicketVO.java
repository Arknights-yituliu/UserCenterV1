package com.orange.entity.vo.oauth;

/**
 * 跨站登录票据响应：登录页换取短时一次性票据，供跳转授权地址时携带
 *
 * @author UserCenter
 */
public class LoginTicketVO {

    /** 一次性登录票据 */
    private String ticket;

    /** 票据有效期（秒） */
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
}
