package com.orange.entity.vo.app;

/**
 * 接入方用户信息视图对象（全局资料 + 站点扩展资料）
 *
 * @author UserCenter
 */
public class AppUserVO {

    /** 用户 uid */
    private Long uid;

    /** 邮箱 */
    private String email;

    /** 全局昵称 */
    private String nickname;

    /** 全局头像 */
    private String avatar;

    /** 账号状态：1=正常 <0=封禁 */
    private Integer status;

    /** 站点扩展资料（可为空） */
    private AppProfileVO profile;

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public AppProfileVO getProfile() {
        return profile;
    }

    public void setProfile(AppProfileVO profile) {
        this.profile = profile;
    }
}
