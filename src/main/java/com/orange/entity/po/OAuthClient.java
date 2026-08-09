package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * OAuth2 客户端注册实体（oauth_client）
 *
 * <p>第三方 Web 网站接入本系统时登记的一行记录，对应 OAuth 2.0 协议中的 Client。</p>
 *
 * @author UserCenter
 */
@TableName("oauth_client")
public class OAuthClient {

    /** 客户端 ID（client_id） */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 客户端密钥（BCrypt 哈希，公共客户端为空） */
    private String clientSecret;

    /** 客户端名称（第三方网站名） */
    private String clientName;

    /** 认证方式：client_secret_basic/client_secret_post */
    private String authMethods;

    /** 授权类型：authorization_code,refresh_token */
    private String grantTypes;

    /** 回调地址白名单（逗号分隔，精确匹配） */
    private String redirectUris;

    /** 可授权范围（逗号分隔）：user.read */
    private String scopes;

    /** 是否强制 PKCE：1=强制 0=不强制 */
    private Integer requirePkce;

    /** 授权时是否展示确认页 */
    private Integer requireAuthConsent;

    /** access_token 有效期（秒），NULL 用全局默认 */
    private Long accessTokenTtl;

    /** refresh_token 有效期（秒） */
    private Long refreshTokenTtl;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getAuthMethods() {
        return authMethods;
    }

    public void setAuthMethods(String authMethods) {
        this.authMethods = authMethods;
    }

    public String getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(String grantTypes) {
        this.grantTypes = grantTypes;
    }

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public Integer getRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(Integer requirePkce) {
        this.requirePkce = requirePkce;
    }

    public Integer getRequireAuthConsent() {
        return requireAuthConsent;
    }

    public void setRequireAuthConsent(Integer requireAuthConsent) {
        this.requireAuthConsent = requireAuthConsent;
    }

    public Long getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Long accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Long getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Long refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
