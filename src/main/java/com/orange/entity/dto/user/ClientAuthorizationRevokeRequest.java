package com.orange.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 撤销我对某第三方应用授权的请求参数
 *
 * @author UserCenter
 */
public class ClientAuthorizationRevokeRequest {

    /** 被撤销授权的应用客户端 ID */
    @NotBlank(message = "应用ID不能为空")
    @Size(max = 128, message = "应用ID长度不能超过 128")
    private String clientId;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
}
