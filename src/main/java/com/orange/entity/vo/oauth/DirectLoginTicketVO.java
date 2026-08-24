package com.orange.entity.vo.oauth;

/**
 * 直连登录票据响应（POST /oauth2/direct-login，前端调用）
 *
 * <p>前端直接向 UC 提交账号密码，校验通过后返回一次性登录票据（ticket），
 * 前端将 ticket 交给旧系统后端，由后端凭 ticket 兑换用户信息。密码全程不经过旧系统后端。</p>
 *
 * @author UserCenter
 */
public class DirectLoginTicketVO {

    /** 一次性登录票据（短时有效，旧系统后端兑换用户信息用） */
    private String ticket;

    /** 有效秒数 */
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
