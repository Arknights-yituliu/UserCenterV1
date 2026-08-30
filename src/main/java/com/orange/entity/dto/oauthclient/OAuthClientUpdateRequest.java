package com.orange.entity.dto.oauthclient;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新 OAuth 客户端请求参数（client_id / authMethod / grantTypes 创建后不可改）
 *
 * @author UserCenter
 */
public class OAuthClientUpdateRequest {

    /** 客户端名称 */
    @NotBlank(message = "客户端名称不能为空")
    @Size(max = 128, message = "客户端名称长度不能超过 128")
    private String clientName;

    /** 回调地址白名单（1~10 个，须 https，本地联调可 http://localhost） */
    @NotEmpty(message = "回调地址不能为空")
    @Size(max = 10, message = "回调地址最多 10 个")
    private List<@NotBlank(message = "回调地址不能为空") String> redirectUris;

    /** 可授权范围（如 user.read、user.email） */
    @NotEmpty(message = "授权范围不能为空")
    private List<@NotBlank(message = "授权范围不能为空") String> scopes;

    /** 网站域名 origin（CORS 白名单来源） */
    @Size(max = 255, message = "网站域名长度不能超过 255")
    private String websiteOrigin;

    /** access_token 有效期（秒），空用全局默认 */
    @Min(value = 60, message = "access_token 有效期不能小于 60 秒")
    private Long accessTokenTtl;

    /** refresh_token 有效期（秒），空用全局默认 */
    @Min(value = 300, message = "refresh_token 有效期不能小于 300 秒")
    private Long refreshTokenTtl;

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

    public String getWebsiteOrigin() {
        return websiteOrigin;
    }

    public void setWebsiteOrigin(String websiteOrigin) {
        this.websiteOrigin = websiteOrigin;
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
}
