package com.orange.entity.vo.efpool;

/**
 * 寻访记录导入任务完成后返回的角色身份信息
 *
 * <p>数据来源已由旧表 endministrator_info 迁移到 ef_account_binding，
 * 字段名保持不变，避免影响既有调用方。</p>
 *
 * @author UserCenter
 */
public class EfPoolRecordIdentityVO {

    /** 终末地游戏内角色 UID */
    private String roleId;

    /** 鹰角通行证 UID */
    private String uid;

    /** 玩家昵称 */
    private String nickName;

    public EfPoolRecordIdentityVO() {
    }

    public EfPoolRecordIdentityVO(String uid, String roleId, String nickName) {
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
