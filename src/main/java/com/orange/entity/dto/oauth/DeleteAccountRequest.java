package com.orange.entity.dto.oauth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** OAuth 注销需要 refresh_token 及绑定邮箱的注销专用验证码。 */
public class DeleteAccountRequest {

    @NotBlank(message = "refresh_token 不能为空")
    @JsonProperty("refresh_token")
    @JsonAlias("refreshToken")
    private String refreshToken;

    @NotBlank(message = "注销验证码不能为空")
    @Pattern(regexp = "[0-9]{6}", message = "注销验证码必须为6位数字")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
