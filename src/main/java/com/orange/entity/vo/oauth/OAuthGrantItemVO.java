package com.orange.entity.vo.oauth;

import java.time.LocalDateTime;

/**
 * 授权条目视图对象（按 client 分组后，组内某一次授权的具体信息）
 *
 * @author UserCenter
 */
public class OAuthGrantItemVO {

    /** 授权范围（逗号分隔） */
    private String scope;

    /** refresh_token 签发（授权）时间；本次升级前签发的存量令牌无该记录，返回 null */
    private LocalDateTime createdAt;

    /** 剩余有效期（秒，>0） */
    private Long expiresInSeconds;

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
