package com.lhs.uc.entity.dto.app;

import jakarta.validation.constraints.NotNull;

/**
 * 按 uid 拉取用户资料请求参数
 *
 * @author UserCenter
 */
public class UserInfoRequest {

    /** 用户 uid */
    @NotNull(message = "uid 不能为空")
    private Long uid;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }
}
