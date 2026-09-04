package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * OAuth 客户端凭证视图对象（client_secret 明文仅在注册/轮换时返回一次）
 *
 * @author UserCenter
 */
public class OAuthClientCredentialVO {

    /** 客户端 ID */
    private String clientId;

    /**
     * 客户端密钥。加密客户端仅在注册或轮换时返回一次明文；公共客户端明确返回 null，
     * 使调用方可以区分“该客户端没有密钥”和“用户遗失了曾经下发的密钥”。
     */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String clientSecret;

    /** 客户端认证方式：none=公共客户端，client_secret_post=加密客户端。 */
    private String authMethod;

    /** 客户端名称 */
    private String clientName;

    /** 所有者是否启用客户端。 */
    private Boolean ownerEnabled;

    /** 管理员是否已审批通过；false 表示待审批或已被管理员封禁。 */
    private Boolean adminApproved;

    /** 是否已由管理员开通直连认证能力（统一控制直连登录和直连注册）。 */
    private Boolean directAuthEnabled;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
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
}
