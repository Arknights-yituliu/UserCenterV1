package com.orange.entity.vo.pat;

import java.time.LocalDateTime;

/**
 * 个人访问令牌列表项视图对象
 *
 * <p><strong>全链路明文（业务取舍）</strong>：除元数据与展示前缀 {@code tokenPrefix} 外，
 * 还回传完整的 {@code token} 明文，便于用户回看与复制；签发响应不再是唯一获取途径。
 * 因此本接口响应必须走 TLS，且不得写入访问日志、监控与前端埋点。</p>
 *
 * @author UserCenter
 */
public class PatVO {

    /** 记录主键（撤销按它定位） */
    private Long id;

    /** 个人令牌固定 this_land_cloud */
    private String clientId;

    /** 用户命名的用途 */
    private String name;

    /** 令牌明文（新令牌 72 字符 / 迁移旧令牌 64 位 hex） */
    private String token;

    /** 令牌前 12 位，仅用于辨认 */
    private String tokenPrefix;

    /** 签发时的权限快照（英文逗号分隔） */
    private String scope;

    /** 签发时指定的有效期（秒） */
    private Long expiresIn;

    /** 到期时间 */
    private LocalDateTime expireTime;

    /** 来源：session / legacy */
    private String source;

    /** 调用方自定义 KV 对象（原样返回） */
    private Object payload;

    /** 最近一次使用时间（阶段一恒为 null） */
    private LocalDateTime lastUsedAt;

    /** 签发时间 */
    private LocalDateTime createdAt;

    /** 剩余有效期（秒），≤0 即已过期 */
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

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
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
