package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * OAuth refresh_token 授权台账实体（oauth_grant）
 *
 * <p>令牌仍是 Redis 热数据；本表作为“我的授权”查询/审计的持久化投影。
 * token 只存 SHA-256 摘要，不落明文。</p>
 *
 * @author UserCenter
 */
@TableName("oauth_grant")
public class OAuthGrant {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 授权用户 uid */
    private Long uid;

    /** 被授权的 OAuth 客户端 ID */
    private String clientId;

    /** 授权范围（逗号分隔） */
    private String scope;

    /** refresh_token 的 SHA-256（64 位十六进制） */
    private String tokenHash;

    /** 授权（签发）时间 */
    private LocalDateTime issueTime;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否已吊销：1=已吊销 0=有效 */
    private Integer revoked;

    /** 记录创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 记录更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
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

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getIssueTime() {
        return issueTime;
    }

    public void setIssueTime(LocalDateTime issueTime) {
        this.issueTime = issueTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Integer getRevoked() {
        return revoked;
    }

    public void setRevoked(Integer revoked) {
        this.revoked = revoked;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
