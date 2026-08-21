package com.orange.entity.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 换绑邮箱请求参数
 *
 * <p>旧邮箱取自服务端当前登录用户，前端无需（也不应）回传，避免脱敏值被回传导致校验失败</p>
 *
 * @author UserCenter
 */
public class ChangeEmailRequest {

    /** 旧邮箱验证码（发到当前绑定邮箱） */
    @NotBlank(message = "旧邮箱验证码不能为空")
    private String oldCode;

    /** 要换绑的新邮箱 */
    @NotBlank(message = "新邮箱不能为空")
    @Email(message = "新邮箱格式不正确")
    private String newEmail;

    /** 新邮箱验证码 */
    @NotBlank(message = "新邮箱验证码不能为空")
    private String newCode;

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
