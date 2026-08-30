package com.orange.entity.vo.oauth;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OAuth 客户端视图对象（不包含密钥）
 *
 * @author UserCenter
 */
public class OAuthClientVO {

    /** 客户端 ID */
    private String clientId;

    /** 客户端名称 */
    private String clientName;

    /** 回调地址白名单 */
    private List<String> redirectUris;

    /** 可授权范围 */
    private List<String> scopes;

    /** 是否强制 PKCE */
    private Boolean requirePkce;

    /** 授权时是否展示确认页 */
    private Boolean requireAuthConsent;

    /** 网站域名 origin */
    private String websiteOrigin;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 管理员封禁：0=正常 1=封禁 */
    private Integer adminBanned;

    /** 创建时间 */
    private LocalDateTime createTime;

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

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public Boolean getRequirePkce() {
        return requirePkce;
    }

    public void setRequirePkce(Boolean requirePkce) {
        this.requirePkce = requirePkce;
    }

    public Boolean getRequireAuthConsent() {
        return requireAuthConsent;
    }

    public void setRequireAuthConsent(Boolean requireAuthConsent) {
        this.requireAuthConsent = requireAuthConsent;
    }

    public String getWebsiteOrigin() {
        return websiteOrigin;
    }

    public void setWebsiteOrigin(String websiteOrigin) {
        this.websiteOrigin = websiteOrigin;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getAdminBanned() {
        return adminBanned;
    }

    public void setAdminBanned(Integer adminBanned) {
        this.adminBanned = adminBanned;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
