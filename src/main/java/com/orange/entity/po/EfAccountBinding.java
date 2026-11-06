package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 终末地角色与用户中心账号的绑定关系及角色档案实体（ef_account_binding）
 *
 * <p>多对多关系：一个用户可绑定多个角色，一个角色也可被多个用户绑定；
 * 玩家数据与寻访记录按角色只存一份，本表记录授权归属，并冗余角色维度档案
 * （鹰角通行证 UID、玩家昵称、是否已导入寻访记录）。</p>
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

    /** 鹰角通行证 UID（原 endministrator_info.uid） */
    private String hgUid;

    /** 玩家昵称（原 endministrator_info.nick_name） */
    private String nickName;

    /** 该角色是否已导入过玩家数据（角色详情）：0 否，1 是 */
    private Integer playerDataImported;

    /** 该角色是否已导入过寻访记录：0 否，1 是 */
    private Integer poolRecordImported;

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

    public String getHgUid() {
        return hgUid;
    }

    public void setHgUid(String hgUid) {
        this.hgUid = hgUid;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public Integer getPlayerDataImported() {
        return playerDataImported;
    }

    public void setPlayerDataImported(Integer playerDataImported) {
        this.playerDataImported = playerDataImported;
    }

    public Integer getPoolRecordImported() {
        return poolRecordImported;
    }

    public void setPoolRecordImported(Integer poolRecordImported) {
        this.poolRecordImported = poolRecordImported;
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
