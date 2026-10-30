package com.orange.entity.po;

import java.time.LocalDateTime;

/**
 * 用户手动版干员数据的时间汇总投影（无数据时 updateTime 为 null）
 *
 * @author UserCenter
 */
public class AkOperatorStateManualSummary {

    /** 最近一次手动版保存时间 */
    private LocalDateTime updateTime;

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
