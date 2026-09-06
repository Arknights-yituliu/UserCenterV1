package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OAuth2 令牌响应视图对象（标准字段命名，兼容 RFC 6749）
 *
 * @author UserCenter
 */
public class OAuthTokenVO {

    /** 访问令牌 */
    @JsonProperty("access_token")
    private String accessToken;

    /** 令牌类型，固定 Bearer */
    @JsonProperty("token_type")
    private String tokenType;

    /** access_token 有效期（秒） */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /** 刷新令牌（null 时省略；仅授权码换码且客户端登记 refresh_token grant 时返回） */
    @JsonProperty("refresh_token")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String refreshToken;

    /** 授权范围（逗号分隔；null 时省略） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String scope;

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
