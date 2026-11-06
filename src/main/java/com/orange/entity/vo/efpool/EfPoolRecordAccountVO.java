package com.orange.entity.vo.efpool;

import java.time.LocalDateTime;

/**
 * 用户导入过寻访记录的终末地账户视图对象（不含寻访记录正文）
 *
 * <p>寻访记录只按 roleId 归属，列表按最近导入时间倒序，前端取首条即可默认展示最新账户。</p>
 *
 * @author UserCenter
 */
public class EfPoolRecordAccountVO {

    /** 终末地游戏内角色 UID，读取寻访记录时原样回传 */
    private String roleId;

    /** 玩家昵称 */
    private String nickName;

    /** 最近一次导入该角色寻访记录的时间 */
    private LocalDateTime updateTime;

    /** 该角色是否已导入过玩家数据（角色详情） */
    private Boolean playerDataImported;

    public EfPoolRecordAccountVO() {
    }

    public EfPoolRecordAccountVO(String roleId, String nickName, LocalDateTime updateTime,
                                 Boolean playerDataImported) {
        this.roleId = roleId;
        this.nickName = nickName;
        this.updateTime = updateTime;
        this.playerDataImported = playerDataImported;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public Boolean getPlayerDataImported() {
        return playerDataImported;
    }

    public void setPlayerDataImported(Boolean playerDataImported) {
        this.playerDataImported = playerDataImported;
    }
}
