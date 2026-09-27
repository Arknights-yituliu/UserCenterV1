package com.orange.entity.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册请求参数
 *
 * <p>统一注册形态：邮箱、用户名、密码、邮箱验证码均必填，主站注册与直连注册共用同一套规则。
 * 密码以 BCrypt 加密落库；邮箱必须通过验证码验证，确保邮箱真实可用且属于注册者本人。
 * userName 仅允许字母、数字、下划线，3-20 位；nickname 可选，缺省时取用户名。</p>
 *
 * @author UserCenter
 */
public class RegisterRequest {

    /** 邮箱（必填，登录账号；需通过邮箱验证码验证） */
    @NotBlank(message = "邮箱不能为空")
    private String email;

    /** 用户名（必填，仅允许字母、数字、下划线，3-20 位） */
    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$", message = "用户名仅支持字母、数字、下划线，长度 3-20 位")
    private String userName;

    /** 密码（必填，6-32 位，仅允许数字、字母、@、下划线） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    @Pattern(regexp = "^[A-Za-z0-9@_]+$", message = "密码仅支持数字、字母、@、下划线")
    private String password;

    /** 邮箱验证码（必填） */
    @NotBlank(message = "邮箱验证码不能为空")
    private String verificationCode;

    /** 昵称（可选，业务上限 24 字符；仅允许中文、英文、数字；表字段 VARCHAR(64) 富余，无需同步改表） */
    @Size(max = 24, message = "昵称长度不能超过 24 位")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z0-9]+$", message = "昵称仅支持中文、英文、数字")
    private String nickname;

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
