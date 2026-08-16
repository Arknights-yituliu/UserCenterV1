package com.orange.entity.vo.oauth;

import com.orange.entity.po.UserInfo;
import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;

/**
 * OAuth 用户信息响应（GET /oauth2/userinfo）
 *
 * <p>接入方多为无自有账户体系的网站，直接以 UC 用户作为登录账号，因此返回用户基础资料
 * （uid/邮箱/用户名/昵称/头像），供接入方展示与关联。</p>
 *
 * @author UserCenter
 */
public class UserInfoVO {

    /** 用户 uid */
    private Long uid;

    /** 邮箱（登录账号） */
    private String email;

    /** 用户名（兼容旧系统迁移用户，可空） */
    private String userName;

    /** 全局默认昵称 */
    private String nickname;

    /** 头像地址 */
    private String avatar;

    /**
     * 由令牌主体与用户实体构建
     *
     * @param principal 令牌主体（取 uid）
     * @param user      目标库用户实体（可能为 null，此时仅返回 uid）
     * @return 用户信息 VO
     */
    public static UserInfoVO of(OAuthTokenPrincipal principal, UserInfo user) {
        UserInfoVO vo = new UserInfoVO();
        vo.setUid(principal.getUid());
        if (user != null) {
            vo.setEmail(user.getEmail());
            vo.setUserName(user.getUserName());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
        }
        return vo;
    }

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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
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
}
