package com.orange.entity.vo.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 直连登录兑换用户信息响应（POST /oauth2/direct-user，供旧系统服务端调用）
 *
 * <p>旧系统凭一次性登录票据兑换用户信息做本地缓存，缓存所需的公开资料只包含
 * uid/昵称/头像/状态，不含邮箱等敏感字段。</p>
 *
 * <p>同时返回本系统签发的 OAuth 令牌（access_token + refresh_token），供旧系统调用
 * /oauth2/userinfo 等 OAuth 资源接口。注意这里签发的是 <b>OAuth 令牌</b>，
 * 不是 <b>UC 会话 token</b>：会话 token 仅由 /auth/login 签发且只作用于 /user/**。</p>
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

    /** 用户状态：1=正常 -1=封禁 */
    private Integer status;

    /** 访问令牌，用于调用 OAuth 资源接口（/oauth2/userinfo 等） */
    @JsonProperty("access_token")
    private String accessToken;

    /** 令牌类型，固定 Bearer */
    @JsonProperty("token_type")
    private String tokenType;

    /** access_token 有效期（秒） */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /** 刷新令牌，有效期内可反复调用 /oauth2/token 刷新 */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /** 授权范围（逗号分隔；null 时省略） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String scope;

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

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
