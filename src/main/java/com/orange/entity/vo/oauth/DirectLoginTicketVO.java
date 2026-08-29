package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    /** 有效秒数，对外输出为 expires_in（与 OAuth 令牌响应风格统一） */
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
     * @return 有效秒数
     */
    @JsonProperty("expiresIn")
    public long getExpiresInCompat() {
        return expiresIn;
    }
}
