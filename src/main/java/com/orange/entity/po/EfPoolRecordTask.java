package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.util.Date;

/**
 * 寻访记录导入任务实体（player_pool_record_task）
 *
 * <p>沿用终末地一图流原有表结构与表名：token 字段存放前端提交的 hgToken，
 * 调度器每秒取一条未开始的任务执行导入，完成后置 complete_flag。</p>
 *
 * @author UserCenter
 */
@TableName("player_pool_record_task")
public class EfPoolRecordTask {

    /** 任务 ID，主键 */
    @TableId(type = IdType.INPUT)
    private String taskId;

    /** 前端提交的 hgToken（完成后会被替换为占位值，避免长期留存凭证） */
    private String token;

    /** 是否已完成导入 */
    private Boolean completeFlag;

    /** 终末地游戏内角色 UID（完成后写入） */
    private String roleId;

    /** 是否已开始处理 */
    private Boolean startFlag;

    /** 任务创建时间 */
    private Date createTime;

    /** 任务最近更新时间 */
    private Date updateTime;

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Boolean getCompleteFlag() {
        return completeFlag;
    }

    public void setCompleteFlag(Boolean completeFlag) {
        this.completeFlag = completeFlag;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public Boolean getStartFlag() {
        return startFlag;
    }

    public void setStartFlag(Boolean startFlag) {
        this.startFlag = startFlag;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
