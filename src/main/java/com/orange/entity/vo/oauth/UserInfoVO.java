package com.orange.entity.vo.oauth;

import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;

/**
 * OAuth 用户信息响应（GET /oauth2/userinfo）
 *
 * @author UserCenter
 */
public class UserInfoVO {

    /** 用户 uid */
    private Long uid;

    /** 签发令牌的客户端 ID */
    private String clientId;

    /** 授权范围 */
    private String scope;

    /**
     * 由令牌主体构建
     *
     * @param principal 令牌主体
     * @return 用户信息 VO
     */
    public static UserInfoVO of(OAuthTokenPrincipal principal) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUid(principal.getUid());
        vo.setClientId(principal.getClientId());
        vo.setScope(principal.getScope());
        return vo;
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
}
