package com.lhs.uc.entity.vo;

import java.time.LocalDateTime;

/**
 * 会话列表项视图对象
 *
 * @author UserCenter
 */
public class SessionVO {

    /** 会话 token（用户本人设备的会话，用于踢下线操作） */
    private String token;

    /** 来源应用 AppId */
    private String appId;

    /** 登录时间 */
    private LocalDateTime loginTime;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
}
