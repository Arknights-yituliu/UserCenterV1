package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户自定义有效期的 access_token 签发记录实体（oauth_custom_token）
 *
 * <p>与 {@link OAuthGrant} 的区别：授权台账记录 refresh_token 且只存摘要；
 * 本表记录过去由直签端点现场签发的短时 access_token，按需求 token 明文保存。</p>
 *
 * <p><strong>遗留表</strong>：写入方与查询方（{@code /user/oauth/custom-tokens}、
 * {@code /oauth2/custom-tokens}、{@code /oauth2/token/from-refresh}）已于 2026-11 下线，
 * 新长期凭证改由 {@code /user/pat}、{@code /oauth2/pat} 的 PAT 承接。本实体现在只被
 * {@code PatMigrationServiceImpl} 只读遍历（把未过期的旧令牌搬进 tlc_pat），
 * 不再有新增写入；旧表导出归档后可随迁移端点一并删除。</p>
 *
 * @author UserCenter
 */
@TableName("oauth_custom_token")
public class OAuthCustomToken {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 签发用户 uid */
    private Long uid;

    /** 目标 OAuth 客户端 ID */
    private String clientId;

    /** 用户为本次签发起的名称 */
    private String name;

    /** access_token 明文 */
    private String token;

    /** 本次令牌携带的权限范围（英文逗号分隔） */
    private String scope;

    /** 签发时指定的有效期（秒） */
    private Long expiresIn;

    /** 到期时间 */
    private LocalDateTime expireTime;

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
