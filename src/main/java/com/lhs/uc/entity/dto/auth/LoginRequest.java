package com.lhs.uc.entity.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求参数
 *
 * <p>accountType=password 时用 email+password；accountType=email 时用 email+verificationCode</p>
 *
 * @author UserCenter
 */
public class LoginRequest {

    /** 登录方式：password=密码 email=邮箱验证码 wechat=微信 qq=QQ */
    @NotBlank(message = "登录方式不能为空")
    private String accountType;

    /** 邮箱 */
    private String email;

    /** 密码（accountType=password 时必填） */
    private String password;

    /** 邮箱验证码（accountType=email 时必填） */
    private String verificationCode;

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }
}
