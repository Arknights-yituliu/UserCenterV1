package com.orange.entity.dto.efpool;

/**
 * 终末地玩家角色信息 DTO
 *
 * <p>由 token 换取服务返回，包含后续请求寻访记录所需的 u8Token 与角色标识。</p>
 *
 * @author UserCenter
 */
public class EfEndfieldUserInfoDTO {

    /** 用于请求终末地寻访记录接口的 u8Token */
    private String u8Token;

    /** 玩家昵称 */
    private String nickName;

    /** 鹰角账号 uid */
    private String uid;

    /** 终末地游戏内角色 UID */
    private String roleId;

    public String getU8Token() {
        return u8Token;
    }

    public void setU8Token(String u8Token) {
        this.u8Token = u8Token;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }
}
