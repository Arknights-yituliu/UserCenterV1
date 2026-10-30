package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 终末地角色与用户中心账号的绑定关系实体（ef_account_binding）
 *
 * <p>多对多关系：一个用户可绑定多个角色，一个角色也可被多个用户绑定；
 * 玩家数据按角色只存一份，本表只记录授权归属。</p>
 *
 * @author UserCenter
 */
@TableName("ef_account_binding")
public class EfAccountBinding {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 终末地游戏内角色 UID */
    private String roleId;

    /** 服务器 ID */
    private String serverId;

    /** 绑定该角色的用户中心 UID */
    private Long ownerUid;

    /** 绑定创建时间 */
    private LocalDateTime createTime;

    /** 该角色数据最近一次导入时间 */
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

    public Long getOwnerUid() {
        return ownerUid;
    }

    public void setOwnerUid(Long ownerUid) {
        this.ownerUid = ownerUid;
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
