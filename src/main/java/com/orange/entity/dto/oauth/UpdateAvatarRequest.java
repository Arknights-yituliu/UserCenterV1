package com.orange.entity.dto.oauth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 修改头像请求参数（OAuth 侧）
 *
 * <p>仅用于 {@code POST /oauth2/profile/avatar}。avatar 必须显式传值，传空串表示清除头像；
 * 校验口径与用户侧修改资料保持一致（≤512 字符），具体写库规则由 {@code UserService.updateProfile} 统一处理。</p>
 *
 * @author UserCenter
 */
public class UpdateAvatarRequest {

    /** 新头像地址（空串表示清除） */
    @NotNull(message = "头像不能为空")
    @Size(max = 512, message = "头像地址长度不能超过 512")
    private String avatar;

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
