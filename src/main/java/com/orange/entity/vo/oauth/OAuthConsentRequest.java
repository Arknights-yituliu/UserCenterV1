package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * OAuth 授权确认请求体：确认页提交"同意/拒绝"时携带的 JSON 参数
 *
 * <p>对应 POST /oauth2/consent 的 JSON body，示例：
 * {"pending_id":"xxx","approve":true}。键名兼容下划线（pending_id）与驼峰（pendingId）两种写法。</p>
 *
 * @author UserCenter
 */
public class OAuthConsentRequest {

    /** 授权确认单 ID（authorize 302 跳转确认页时携带） */
    @JsonProperty("pending_id")
    @JsonAlias("pendingId")
    private String pendingId;

    /** 是否同意授权（true=同意，false=拒绝） */
    private Boolean approve;

    /**
     * 用户在确认页最终确定的权限集合（可空）
     *
     * <p>为空表示沿用确认单里的申请范围（兼容旧确认页）；非空时服务端要求其非空且必须是
     * 系统可授予的权限（不受该应用登记范围限制），同时覆盖写入用户自定义授权范围表，
     * 供下次授权自动延续。该字段既用于追加权限，也用于通过“不勾选”取消已授权的权限。</p>
     */
    private List<String> scopes;

    public String getPendingId() {
        return pendingId;
    }

    public void setPendingId(String pendingId) {
        this.pendingId = pendingId;
    }

    public Boolean getApprove() {
        return approve;
    }

    public void setApprove(Boolean approve) {
        this.approve = approve;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }
}
