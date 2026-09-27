package com.orange.entity.vo.oauth;

import java.util.List;

/**
 * OAuth 授权确认页信息响应：确认页展示第三方网站与申请权限
 *
 * <p>由 GET /oauth2/consent/info 返回，供确认页渲染"谁在申请、申请什么权限、授权后跳转哪里"。
 * scope 由服务端映射为中文描述，未知 scope 返回原始标识。</p>
 *
 * <p>为支持用户对已授权权限做追加与取消，额外返回 {@link #grantedScopes}（用户自定义授权范围，
 * 从未自定义过则为空）与 {@link #selectableScopes}（系统全部可选权限，不受该应用登记范围限制）：
 * 前端据此渲染勾选状态，用户在页面上勾选的结果通过 POST /oauth2/consent 的 scopes 字段回传。</p>
 *
 * @author UserCenter
 */
public class ConsentInfoVO {

    /** 发起授权的用户 uid */
    private Long uid;

    /** 客户端 ID */
    private String clientId;

    /** 客户端名称（第三方网站名） */
    private String clientName;

    /** 授权回调地址 */
    private String redirectUri;

    /** 本次将授予的权限列表（已按用户自定义授权范围收敛，见 resolveGrantScope） */
    private List<ScopeItemVO> scopes;

    /** 当前已授予该应用的权限（来自用户自定义授权范围表；从未自定义过则为空列表） */
    private List<ScopeItemVO> grantedScopes;

    /** 系统全部可选权限（用户可在此范围内追加，不受该应用登记范围限制） */
    private List<ScopeItemVO> selectableScopes;

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

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public List<ScopeItemVO> getScopes() {
        return scopes;
    }

    public void setScopes(List<ScopeItemVO> scopes) {
        this.scopes = scopes;
    }

    public List<ScopeItemVO> getGrantedScopes() {
        return grantedScopes;
    }

    public void setGrantedScopes(List<ScopeItemVO> grantedScopes) {
        this.grantedScopes = grantedScopes;
    }

    public List<ScopeItemVO> getSelectableScopes() {
        return selectableScopes;
    }

    public void setSelectableScopes(List<ScopeItemVO> selectableScopes) {
        this.selectableScopes = selectableScopes;
    }
}
