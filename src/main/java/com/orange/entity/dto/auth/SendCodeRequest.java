package com.orange.entity.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送邮箱验证码请求参数
 *
 * @author UserCenter
 */
public class SendCodeRequest {

    /** 目标邮箱 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 验证码用途：register=注册 login=登录 reset=重置密码（白名单校验，其余值一律拒绝） */
    @NotBlank(message = "验证码用途不能为空")
    @Pattern(regexp = "^(register|login|reset)$", message = "不支持的验证码用途")
    private String usage;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsage() {
        return usage;
    }

    public void setUsage(String usage) {
        this.usage = usage;
    }
}
