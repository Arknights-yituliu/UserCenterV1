package com.orange.entity.vo.oauth;

/**
 * 直连登录发起会话响应（POST /oauth2/direct-session，旧系统后端调用）
 *
 * <p>旧系统后端以 client_id + client_secret 换取短期发起会话凭证（channel），
 * 前端持 channel 才能调直连登录接口，避免把 client_secret 暴露给浏览器。</p>
 *
 * @author UserCenter
 */
public class DirectLoginSessionVO {

    /** 发起会话凭证（短时有效，前端登录时携带） */
    private String channel;

    /** 有效秒数 */
    private long expiresIn;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
