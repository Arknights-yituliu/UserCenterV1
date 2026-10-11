package com.orange.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户自助吊销自定义有效期 access_token 的请求参数
 *
 * <p>用户在「我的自定义令牌」列表中主动作废自己名下的一条令牌。token 为签发时返回的
 * access_token 明文，放在请求体中提交；只按当前登录用户 uid 限定归属，可跨应用吊销。</p>
 *
 * @author UserCenter
 */
public class UserCustomTokenRevokeRequest {

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
