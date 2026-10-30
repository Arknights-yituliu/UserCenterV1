package com.orange.entity.dto.skland;

import jakarta.validation.constraints.NotBlank;

/**
 * 鹰角官网 token 换森空岛凭证请求参数
 *
 * @author UserCenter
 */
public class HgTokenRequest {

    /** 鹰角通行证 token（官网 / 森空岛网页端获取） */
    @NotBlank(message = "token不能为空")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
