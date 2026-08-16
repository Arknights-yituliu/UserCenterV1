package com.orange.entity.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 注册请求参数
 *
 * <p>password / email_code 两种注册方式均需设置密码；邮箱与用户名至少填一个。
 * email_code 方式必须填邮箱。只要填了邮箱，都必须提供邮箱验证码通过验证。
 * userName 仅允许字母、数字、下划线，3-20 位。</p>
 *
 * @author UserCenter
 */
public class RegisterRequest {

    /** 注册方式：password=密码注册 email_code=邮箱验证码注册（两者均需设置密码） */
    @NotBlank(message = "注册方式不能为空")
    private String registerType;

    /** 邮箱（与用户名至少填一个；邮箱验证码注册时必填） */
    private String email;

    /** 用户名（可选；与邮箱至少填一个，仅允许字母、数字、下划线，3-20 位） */
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名仅支持字母、数字、下划线，长度 3-20 位")
    private String userName;

    /** 密码（registerType=password 时必填，仅允许数字、字母、@、下划线） */
    @Pattern(regexp = "^[A-Za-z0-9@_]+$", message = "密码仅支持数字、字母、@、下划线")
    private String password;

    /** 邮箱验证码（填了邮箱时必须提供：密码注册带邮箱、邮箱验证码注册均需校验） */
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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
