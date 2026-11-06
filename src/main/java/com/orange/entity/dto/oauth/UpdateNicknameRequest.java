package com.orange.entity.dto.oauth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改昵称请求参数（OAuth 侧）
 *
 * <p>仅用于 {@code POST /oauth2/profile/nickname}。昵称支持中文，不做字符集限制
 * （中文、英文、数字及其他字符均可提交），仅校验非空与长度上限 64 字符；
 * 校验口径与用户侧修改资料保持一致，具体写库规则由 {@code UserService.updateProfile} 统一处理。</p>
 *
 * @author UserCenter
 */
public class UpdateNicknameRequest {

    /** 新昵称（支持中文，最长 64 字符） */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
}
