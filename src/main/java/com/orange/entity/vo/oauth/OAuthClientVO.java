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

    /** 客户端认证方式：none=公共客户端，client_secret_post=加密客户端。 */
    private String authMethod;

    /** 客户端获准使用的授权类型，响应中以列表形式返回。 */
    private List<String> grantTypes;

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

    /** 网站 Origin 是否已由管理员审批通过。 */
    private Boolean originApproved;

    /** 所有者是否启用客户端。 */
    private Boolean ownerEnabled;

    /** 管理员是否已审批通过；false 表示待审批或已被管理员封禁。 */
    private Boolean adminApproved;

    /** 是否已由管理员开通直连认证能力（统一控制直连登录和直连注册）。 */
    private Boolean directAuthEnabled;

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

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public List<String> getGrantTypes() {
        return grantTypes;
    }

    public void setGrantTypes(List<String> grantTypes) {
        this.grantTypes = grantTypes;
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

    public Boolean getOriginApproved() {
        return originApproved;
    }

    public void setOriginApproved(Boolean originApproved) {
        this.originApproved = originApproved;
    }

    public Boolean getOwnerEnabled() {
        return ownerEnabled;
    }

    public void setOwnerEnabled(Boolean ownerEnabled) {
        this.ownerEnabled = ownerEnabled;
    }

    public Boolean getAdminApproved() {
        return adminApproved;
    }

    public void setAdminApproved(Boolean adminApproved) {
        this.adminApproved = adminApproved;
    }

    public Boolean getDirectAuthEnabled() {
        return directAuthEnabled;
    }

    public void setDirectAuthEnabled(Boolean directAuthEnabled) {
        this.directAuthEnabled = directAuthEnabled;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
