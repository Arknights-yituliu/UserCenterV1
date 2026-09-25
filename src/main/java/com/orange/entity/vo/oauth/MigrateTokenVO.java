package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 迁移兑换端点响应视图对象（UC → BackEndV3 的服务端间响应）
 *
 * <p>为缩小公网暴露下的响应面，不复用 ServerLoginVO：只回令牌与 scope，不回带
 * 昵称 / 头像 / 邮箱。用户资料由 BackEndV3 持新 access_token 调 /oauth2/userinfo 获取。</p>
 *
 * @author UserCenter
 */
public class MigrateTokenVO {

    /** 用户 uid */
    @JsonProperty("uid")
    private Long uid;

    /** 访问令牌 */
    @JsonProperty("access_token")
    private String accessToken;

    /** 令牌类型，固定 Bearer */
    @JsonProperty("token_type")
    private String tokenType;

    /** access_token 有效期（秒） */
    @JsonProperty("expires_in")
    private Long expiresIn;

    /** 刷新令牌（仅服务端间传输，不下发浏览器） */
    @JsonProperty("refresh_token")
    private String refreshToken;

    /** 授权范围（逗号分隔；null 时省略） */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String scope;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

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
