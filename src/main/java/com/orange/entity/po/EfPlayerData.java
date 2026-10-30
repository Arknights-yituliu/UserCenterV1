package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 终末地玩家数据实体（ef_player_data）：一行一个游戏角色，按 role_id + server_id 去重
 *
 * <p>与明日方舟按字段拆列的干员表不同，终末地角色卡片结构多变，整份 JSON 以文本形式
 * 存放在 {@code playerData}，服务端不解析内容，只负责保存与归属。</p>
 *
 * @author UserCenter
 */
@TableName("ef_player_data")
public class EfPlayerData {

    /** 数据库自增行 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 终末地游戏内角色 UID（card/detail 的 roleId） */
    private String roleId;

    /** 服务器 ID，与 roleId 共同定位角色 */
    private String serverId;

    /** 玩家数据 JSON 原始文本 */
    private String playerData;

    /** 记录创建时间 */
    private LocalDateTime createTime;

    /** 最近一次保存时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPlayerData() {
        return playerData;
    }

    public void setPlayerData(String playerData) {
        this.playerData = playerData;
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
