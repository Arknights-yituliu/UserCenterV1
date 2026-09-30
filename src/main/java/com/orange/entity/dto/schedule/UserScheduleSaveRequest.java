package com.orange.entity.dto.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * 保存用户排班表请求参数。
 *
 * @author UserCenter
 */
public class UserScheduleSaveRequest {

    /** 排班表 ID；为空时创建，非空时覆盖更新。 */
    private Long id;

    /** 排班表 JSON 数组。 */
    @NotNull(message = "排班表不能为空")
    private JsonNode schedule;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public JsonNode getSchedule() {
        return schedule;
    }

    public void setSchedule(JsonNode schedule) {
        this.schedule = schedule;
    }
}
