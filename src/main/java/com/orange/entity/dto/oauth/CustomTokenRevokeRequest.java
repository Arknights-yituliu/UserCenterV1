package com.orange.entity.dto.oauth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 吊销（删除并失效）一条自定义有效期 access_token 的请求参数
 *
 * <p>由第三方应用以自身 access_token 认证后调用，token 为签发时返回的 access_token 明文，
 * 放在请求体中提交。只能吊销本应用为当前用户签发、且归属于调用方 client_id 的令牌。</p>
 *
 * @author UserCenter
 */
public class CustomTokenRevokeRequest {

    /** 待吊销的自定义 access_token 明文 */
    @NotBlank(message = "token 不能为空")
    @Size(max = 128, message = "token 长度不能超过 128")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
