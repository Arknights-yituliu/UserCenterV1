package com.orange.entity.vo.efplayer;

import java.time.LocalDateTime;

/**
 * 终末地玩家数据保存结果
 *
 * <p>返回本次写入的角色标识与保存时间，便于调用方确认落库结果。</p>
 *
 * @author UserCenter
 */
public class EfPlayerDataSaveResultVO {

    /** 终末地游戏内角色 UID */
    private String roleId;

    /** 服务器 ID（已归一化，可能为空串） */
    private String serverId;

    /** 本次保存时间 */
    private LocalDateTime updateTime;

    public EfPlayerDataSaveResultVO() {
    }

    /**
     * 构造保存结果
     *
     * @param roleId     角色 UID
     * @param serverId   服务器 ID
     * @param updateTime 本次保存时间
     */
    public EfPlayerDataSaveResultVO(String roleId, String serverId, LocalDateTime updateTime) {
        this.roleId = roleId;
        this.serverId = serverId;
        this.updateTime = updateTime;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
