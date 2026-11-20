package com.orange.entity.dto.oauth;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/** 凭 refresh_token 给其所属账户的绑定邮箱发送注销验证码。 */
public class SendAccountDeletionCodeRequest {
    @NotBlank(message = "refresh_token 不能为空")
    @JsonProperty("refresh_token")
    @JsonAlias("refreshToken")
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
