package com.orange.entity.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 注销当前账户需要绑定邮箱收到的注销专用验证码。 */
public class DeleteAccountRequest {
    @NotBlank(message = "注销验证码不能为空")
    @Pattern(regexp = "[0-9]{6}", message = "注销验证码必须为6位数字")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
