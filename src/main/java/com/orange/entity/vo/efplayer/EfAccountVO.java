package com.orange.entity.vo.efplayer;

import java.time.LocalDateTime;

/**
 * 当前用户已绑定的终末地角色视图对象（不含玩家数据正文）
 *
 * <p>列表按 {@code updateTime} 倒序返回，前端取首条即可默认展示最新导入的数据。
 * 角色由 {@code roleId} + {@code serverId} 定位，读取玩家数据时原样回传这两个字段。</p>
 *
 * @author UserCenter
 */
public class EfAccountVO {

    /** 终末地游戏内角色 UID */
    private String roleId;

    /** 服务器 ID，可能为空串 */
    private String serverId;

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
