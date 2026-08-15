package com.orange.entity.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 换绑邮箱请求参数
 *
 * <p>需同时验证旧邮箱与新邮箱的所有权（验证码均一次性）</p>
 *
 * @author UserCenter
 */
public class ChangeEmailRequest {

    /** 当前已绑定的旧邮箱 */
    @NotBlank(message = "旧邮箱不能为空")
    @Email(message = "旧邮箱格式不正确")
    private String oldEmail;

    /** 旧邮箱验证码 */
    @NotBlank(message = "旧邮箱验证码不能为空")
    private String oldCode;

    /** 要换绑的新邮箱 */
    @NotBlank(message = "新邮箱不能为空")
    @Email(message = "新邮箱格式不正确")
    private String newEmail;

    /** 新邮箱验证码 */
    @NotBlank(message = "新邮箱验证码不能为空")
    private String newCode;

    public String getOldEmail() {
        return oldEmail;
    }

    public void setOldEmail(String oldEmail) {
        this.oldEmail = oldEmail;
    }

    public String getOldCode() {
        return oldCode;
    }

    public void setOldCode(String oldCode) {
        this.oldCode = oldCode;
    }

    public String getNewEmail() {
        return newEmail;
    }

    public void setNewEmail(String newEmail) {
        this.newEmail = newEmail;
    }

    public String getNewCode() {
        return newCode;
    }

    public void setNewCode(String newCode) {
        this.newCode = newCode;
    }
}
