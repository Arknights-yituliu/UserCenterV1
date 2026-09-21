package com.orange.entity.vo.oauth;

import com.orange.common.enums.OAuthScope;

/**
 * OAuth scope 元数据，供客户端管理与授权确认前端展示。
 *
 * @author UserCenter
 */
public class OAuthScopeVO {

    private String code;
    private String name;
    private String description;
    private boolean sensitive;

    public static OAuthScopeVO of(OAuthScope scope) {
        OAuthScopeVO vo = new OAuthScopeVO();
        vo.setCode(scope.getCode());
        vo.setName(scope.getName());
        vo.setDescription(scope.getDescription());
        vo.setSensitive(scope.isSensitive());
        return vo;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }
}
