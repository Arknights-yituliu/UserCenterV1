package com.orange.entity.vo.auth;

/**
 * 服务端登录响应（POST /oauth2/server-login，供旧系统服务端调用）
 *
 * <p>旧系统以 client_id + client_secret 认证后，用账号密码换取用户信息做本地缓存。
 * 仅返回公开资料（不签发 UC 会话 token），邮箱为脱敏值，供展示不做账号关联。</p>
 *
 * @author UserCenter
 */
public class ServerLoginVO {

    /** 用户 uid（旧系统本地账号打通的稳定唯一标识） */
    private Long uid;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;

    /** 邮箱（脱敏展示） */
    private String email;

    /** 用户状态：1=正常 -1=封禁 */
    private Integer status;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
