package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户配置容量配额。
 *
 * @author UserCenter
 */
@TableName("user_config_quota")
public class UserConfigQuota {

    /**
     * 直接复用 user_info.uid，由业务传入；IdType.INPUT 不会生成或递增主键。
     */
    @TableId(type = IdType.INPUT)
    private Long uid;

    /** 已使用字节数 */
    private Long usedBytes;

    /** 当前配额字节数 */
    private Long limitBytes;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public Long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(Long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public Long getLimitBytes() {
        return limitBytes;
    }

    public void setLimitBytes(Long limitBytes) {
        this.limitBytes = limitBytes;
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
