package com.lhs.uc.entity.dto.app;

import jakarta.validation.constraints.NotBlank;

/**
 * 校验用户 token 请求参数
 *
 * @author UserCenter
 */
public class VerifyTokenRequest {

    /** 用户会话 token */
    @NotBlank(message = "token 不能为空")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
