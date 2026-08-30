package com.orange.entity.vo.oauth;

/**
 * OAuth 客户端凭证视图对象（client_secret 明文仅在注册/轮换时返回一次）
 *
 * @author UserCenter
 */
public class OAuthClientCredentialVO {

    /** 客户端 ID */
    private String clientId;

    /** 客户端密钥（明文，仅此一次返回，请立即保存） */
    private String clientSecret;

    /** 客户端名称 */
    private String clientName;

    /** 状态：1=启用 0=停用 */
    private Integer status;

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

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
