package com.orange.entity.dto.app;

import jakarta.validation.constraints.NotNull;

/**
 * 踢用户下线请求参数
 *
 * @author UserCenter
 */
public class UserKickRequest {

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
