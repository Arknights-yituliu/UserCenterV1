package com.orange.entity.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 绑定邮箱请求参数（面向无邮箱的迁移用户）
 *
 * @author UserCenter
 */
public class BindEmailRequest {

    /** 要绑定的新邮箱 */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /** 新邮箱验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
