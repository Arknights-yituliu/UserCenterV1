package com.orange.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户编辑某第三方应用授权范围的请求参数
 *
 * <p>用于权限追加与取消两个接口：{@link #scopes} 为本次要操作的权限标识集合，
 * 追加时与服务端现有范围取并集，取消时取差集（取消后不可为空）。</p>
 *
 * @author UserCenter
 */
public class UserScopeRequest {

    /** 目标应用客户端 ID */
    @NotBlank(message = "应用ID不能为空")
    @Size(max = 128, message = "应用ID长度不能超过 128")
    private String clientId;

    /** 本次要追加或取消的权限标识集合 */
    @NotEmpty(message = "权限列表不能为空")
    private List<String> scopes;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }
}
