package com.lhs.uc.entity.vo.app;

/**
 * 站点扩展资料视图对象
 *
 * @author UserCenter
 */
public class AppProfileVO {

    /** 站点内昵称 */
    private String nickname;

    /** 站点内头像 */
    private String avatar;

    /** 扩展字段（JSON） */
    private String ext;

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

    public String getExt() {
        return ext;
    }

    public void setExt(String ext) {
        this.ext = ext;
    }
}
