package com.orange.entity.vo.oauth;

import java.time.LocalDateTime;

/**
 * 当前用户已授权的 refresh_token 列表项视图对象
 *
 * @author UserCenter
 */
public class RefreshGrantVO {

    /** 授权应用（OAuth 客户端）ID */
    private String clientId;

    /** 授权应用名称；应用已被删除/停用时为空 */
    private String clientName;

    /** 授权范围（逗号分隔） */
    private String scope;

    /** refresh_token 签发（授权）时间；本次升级前签发的存量令牌无该记录，返回 null */
    private LocalDateTime createdAt;

    /** 剩余有效期（秒，>0） */
    private Long expiresInSeconds;

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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
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
