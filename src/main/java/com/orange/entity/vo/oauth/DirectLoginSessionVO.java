package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

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

    /** 有效秒数，对外输出为 expires_in（与 OAuth 令牌响应风格统一） */
    @JsonProperty("expires_in")
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
