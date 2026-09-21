package com.orange.entity.dto.schedule;

import jakarta.validation.constraints.NotNull;

/**
 * 删除用户排班表请求参数。
 *
 * @author UserCenter
 */
public class UserScheduleDeleteRequest {

    @NotNull(message = "排班表 id 不能为空")
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
