package com.orange.entity.vo;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/** 当前用户可读取完整内容的排班表视图。 */
public class UserScheduleVO {

    private Long id;
    private JsonNode schedule;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
