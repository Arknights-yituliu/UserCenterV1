package com.orange.entity.dto.userconfig;

import jakarta.validation.constraints.NotNull;

/**
 * 删除用户配置请求参数
 *
 * @author UserCenter
 */
public class UserConfigDeleteRequest {

    /** 配置 id */
    @NotNull(message = "配置 id 不能为空")
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
