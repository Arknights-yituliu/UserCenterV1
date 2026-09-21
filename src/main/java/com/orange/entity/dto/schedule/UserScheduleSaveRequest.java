package com.orange.entity.dto.schedule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 保存用户排班表请求参数。
 *
 * @author UserCenter
 */
public class UserScheduleSaveRequest {

    /** 排班表 ID；为空时创建，非空时覆盖更新。 */
    private Long id;

    /** 排班表 JSON 字符串。 */
    @NotBlank(message = "排班表不能为空")
    @Size(max = 30720, message = "排班表不能超过 30KB")
    private String schedule;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSchedule() {
        return schedule;
    }

    public void setSchedule(String schedule) {
        this.schedule = schedule;
    }
}
