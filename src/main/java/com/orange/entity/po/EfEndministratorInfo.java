package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 终末地玩家角色信息实体（endministrator_info）
 *
 * <p>沿用终末地一图流原有表结构与表名，按 role_id 唯一，记录 uid 与昵称。</p>
 *
 * @author UserCenter
 */
@TableName("endministrator_info")
public class EfEndministratorInfo {

    /** 终末地游戏内角色 UID，主键 */
    @TableId(type = IdType.INPUT)
    private String roleId;

    /** 鹰角账号 uid */
    private String uid;

    /** 玩家昵称 */
    private String nickName;

    public EfEndministratorInfo() {
    }

    public EfEndministratorInfo(String uid, String roleId, String nickName) {
        this.uid = uid;
        this.roleId = roleId;
        this.nickName = nickName;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }
}
