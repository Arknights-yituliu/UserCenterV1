package com.orange.entity.vo.efplayer;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * 终末地玩家数据读取结果：原样返回保存时提交的 JSON 对象，服务端不做字段转换
 *
 * @author UserCenter
 */
public class EfPlayerDataVO {

    /** 终末地游戏内角色 UID */
    private String roleId;

    /** 服务器 ID（已归一化，可能为空串） */
    private String serverId;

    /** 玩家数据 JSON 对象，与保存时提交的内容一致 */
    private JsonNode playerData;

    /** 最近一次保存时间 */
    private LocalDateTime updateTime;

    public EfPlayerDataVO() {
    }

    /**
     * 构造读取结果
     *
     * @param roleId     角色 UID
     * @param serverId   服务器 ID
     * @param playerData 玩家数据 JSON 对象
     * @param updateTime 最近一次保存时间
     */
    public EfPlayerDataVO(String roleId, String serverId, JsonNode playerData, LocalDateTime updateTime) {
        this.roleId = roleId;
        this.serverId = serverId;
        this.playerData = playerData;
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

    public JsonNode getPlayerData() {
        return playerData;
    }

    public void setPlayerData(JsonNode playerData) {
        this.playerData = playerData;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
