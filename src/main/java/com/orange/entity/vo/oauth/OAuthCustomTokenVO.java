package com.orange.entity.vo.oauth;

import java.time.LocalDateTime;

/**
 * 用户自定义有效期 access_token 列表项视图对象
 *
 * @author UserCenter
 */
public class OAuthCustomTokenVO {

    /** 记录主键 */
    private Long id;

    /** 目标应用（OAuth 客户端）ID */
    private String clientId;

    /** 应用名称；应用已被删除/停用时为空 */
    private String clientName;

    /** 用户为本次签发起的名称 */
    private String name;

    /** access_token 明文（按需求保存，供用户复制使用） */
    private String token;

    /** 本次令牌携带的权限范围（英文逗号分隔） */
    private String scope;

    /** 签发时指定的有效期（秒） */
    private Long expiresIn;

    /** 到期时间 */
    private LocalDateTime expireTime;

    /** 签发（创建）时间 */
    private LocalDateTime createdAt;

    /** 剩余有效期（秒）；小于等于 0 即视为已过期 */
    private Long expiresInSeconds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public void setExpiresInSeconds(Long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }
}
