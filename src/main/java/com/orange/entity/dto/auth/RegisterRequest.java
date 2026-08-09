package com.orange.entity.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 注册请求参数
 *
 * <p>registerType=password 时校验邮箱/密码；registerType=email_code 时校验邮箱/验证码</p>
 *
 * @author UserCenter
 */
public class RegisterRequest {

    /** 注册方式：password=密码注册 email_code=邮箱验证码注册 */
    @NotBlank(message = "注册方式不能为空")
    private String registerType;

    /** 邮箱 */
    private String email;

    /** 密码（registerType=password 时必填，仅允许数字、字母、@、下划线） */
    @Pattern(regexp = "^[A-Za-z0-9@_]+$", message = "密码仅支持数字、字母、@、下划线")
    private String password;

    /** 邮箱验证码（registerType=email_code 时必填） */
    private String verificationCode;

    /** 昵称（可选） */
    private String nickname;

    public String getRegisterType() {
        return registerType;
    }

    public void setRegisterType(String registerType) {
        this.registerType = registerType;
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
