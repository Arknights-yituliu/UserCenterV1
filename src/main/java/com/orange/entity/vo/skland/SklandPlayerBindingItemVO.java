package com.orange.entity.vo.skland;

/**
 * 森空岛账号下的明日方舟角色绑定条目
 *
 * @author UserCenter
 */
public class SklandPlayerBindingItemVO {

    /** 游戏内角色 UID */
    private String uid;

    /** 角色昵称 */
    private String nickName;

    /** 渠道主账号标识 */
    private String channelMasterId;

    /** 渠道名称（如官服、B服） */
    private String channelName;

    /** 是否为该账号在当前 App 下的默认角色 */
    private boolean isDefault;

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

    public String getChannelMasterId() {
        return channelMasterId;
    }

    public void setChannelMasterId(String channelMasterId) {
        this.channelMasterId = channelMasterId;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
}
