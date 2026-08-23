package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

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
}
