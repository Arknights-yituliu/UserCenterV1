package com.orange.entity.vo.efplayer;

import java.time.LocalDateTime;

/**
 * 当前用户已绑定的终末地角色视图对象（不含玩家数据正文）
 *
 * <p>列表按 {@code updateTime} 倒序返回，前端取首条即可默认展示最新导入的数据。
 * 角色由 {@code roleId} + {@code serverId} 定位，读取玩家数据时原样回传这两个字段。
 * 服务器展示信息与玩家昵称只用于列表展示，未知时统一为空串。</p>
 *
 * @author UserCenter
 */
public class EfAccountVO {

    /** 终末地游戏内角色 UID */
    private String roleId;

    /** 服务器 ID，可能为空串 */
    private String serverId;

    /** 服务器类型（客户端保存玩家数据时上报），未上报时为空串 */
    private String serverType = "";

    /** 服务器名称（客户端保存玩家数据时上报），未上报时为空串 */
    private String serverName = "";

    /** 玩家昵称（寻访记录导入时写入），未导入时为空串 */
    private String nickName = "";

    /** 绑定创建时间：首次导入该角色数据时写入 */
    private LocalDateTime createTime;

    /** 最近一次导入该角色数据的时间 */
    private LocalDateTime updateTime;

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

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
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
