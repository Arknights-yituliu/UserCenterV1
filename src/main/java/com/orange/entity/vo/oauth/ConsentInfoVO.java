package com.orange.entity.vo.oauth;

import java.util.List;

/**
 * OAuth 授权确认页信息响应：确认页展示第三方网站与申请权限
 *
 * <p>由 GET /oauth2/consent/info 返回，供确认页渲染"谁在申请、申请什么权限、
 * 授权后跳转哪里"。scope 由服务端映射为中文描述，未知 scope 返回原始标识。</p>
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

    /** 申请权限列表 */
    private List<ScopeItem> scopes;

    /**
     * 权限条目：标识 + 中文描述
     */
    public static class ScopeItem {

        /** 权限标识（如 user.read） */
        private String code;

        /** 权限中文描述 */
        private String desc;

        public ScopeItem() {
        }

        public ScopeItem(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDesc() {
            return desc;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }
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

    public List<ScopeItem> getScopes() {
        return scopes;
    }

    public void setScopes(List<ScopeItem> scopes) {
        this.scopes = scopes;
    }
}
