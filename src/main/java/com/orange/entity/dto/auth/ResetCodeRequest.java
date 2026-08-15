package com.orange.entity.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * 发送重设密码验证码请求参数
 *
 * <p>account 为邮箱或用户名（兼容旧系统迁移用户）</p>
 *
 * @author UserCenter
 */
public class ResetCodeRequest {

    /** 账号（邮箱或用户名） */
    @NotBlank(message = "账号不能为空")
    private String account;

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }
}
