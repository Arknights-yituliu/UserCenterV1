package com.orange.entity.vo.pat;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 签发个人访问令牌的响应视图对象
 *
 * <p>{@code token} 是明文。全链路明文取舍下，服务端同样保存明文，
 * 后续列表接口也可回看，本响应不再是唯一获取途径。</p>
 *
 * @author UserCenter
 */
public class PatIssueVO {

    /** 记录主键 */
    private Long id;

    /** 令牌明文 */
    private String token;

    /** 令牌前 12 位 */
    private String tokenPrefix;

    /** 用户命名的用途 */
    private String name;

    /** 个人令牌固定 this_land_cloud */
    private String clientId;

    /** 本次令牌携带的权限范围 */
    private String scope;

    /** 有效期（秒） */
    private Long expiresIn;

    /** 到期时间 */
    private LocalDateTime expireTime;

    /** 调用方自定义 KV 对象（原样返回；无则为 null 并省略） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object payload;

    /** 签发时间 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
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

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
