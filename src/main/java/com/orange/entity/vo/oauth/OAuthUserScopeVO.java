package com.orange.entity.vo.oauth;

import java.util.List;

/**
 * 用户自定义授权范围查询响应
 *
 * <p>供用户在“我的第三方应用授权”管理页查看与编辑某应用的授权范围：
 * {@link #grantedScopes} 为当前已授予（自定义表中）的权限，未自定义过则为该应用登记范围；
 * {@link #selectableScopes} 为系统全部可选权限（不受该应用登记范围限制），
 * 两者配合前端渲染勾选状态。</p>
 *
 * @author UserCenter
 */
public class OAuthUserScopeVO {

    /** 客户端 ID */
    private String clientId;

    /** 客户端名称（第三方网站名） */
    private String clientName;

    /** 当前已授予该应用的权限（来自用户自定义表；未自定义过则为该应用登记范围） */
    private List<ScopeItemVO> grantedScopes;

    /** 系统全部可选权限（用户可在此范围内追加，不受该应用登记范围限制） */
    private List<ScopeItemVO> selectableScopes;

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
