package com.orange.entity.dto.user;

import jakarta.validation.constraints.Size;

/**
 * 修改个人资料请求参数
 *
 * @author UserCenter
 */
public class UpdateProfileRequest {

    /** 全局昵称 */
    @Size(max = 64, message = "昵称长度不能超过 64")
    private String nickname;

    /** 头像地址 */
    @Size(max = 512, message = "头像地址长度不能超过 512")
    private String avatar;

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}
