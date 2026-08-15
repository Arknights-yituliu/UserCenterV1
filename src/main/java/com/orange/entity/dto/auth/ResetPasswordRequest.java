package com.orange.entity.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 重设密码请求参数
 *
 * <p>通过邮箱验证码重置密码，无需登录态与旧密码</p>
 *
 * @author UserCenter
 */
public class ResetPasswordRequest {

    /** 账号（邮箱或用户名） */
    @NotBlank(message = "账号不能为空")
    private String account;

    /** 邮箱验证码 */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /** 新密码（仅允许数字、字母、@、下划线） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度需在 6-32 位之间")
    @Pattern(regexp = "^[A-Za-z0-9@_]+$", message = "密码仅支持数字、字母、@、下划线")
    private String newPassword;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
