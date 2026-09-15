package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 游戏角色信息实体（ak_player_info）：按 ak_uid 去重，可被多个用户中心 uid 共享
 *
 * @author UserCenter
 */
@TableName("ak_player_info")
public class AkPlayerInfo {

    /** 数据库行 ID，仅用于内部更新 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏角色昵称 */
    private String akNickName;

    /** 游戏账号 UID，数据归属键 */
    private String akUid;

    /** 渠道名称，可为空 */
    private String channelName;

    /** 渠道主 ID，可为空 */
    private Integer channelMasterId;

    /** 是否已逻辑删除：false=有效 */
    private Boolean deleteFlag;

    /** 角色信息最后一次实际变更的 Unix 毫秒时间戳，服务端生成 */
    private Long updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAkNickName() {
        return akNickName;
    }

    public void setAkNickName(String akNickName) {
        this.akNickName = akNickName;
    }

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Integer getChannelMasterId() {
        return channelMasterId;
    }

    public void setChannelMasterId(Integer channelMasterId) {
        this.channelMasterId = channelMasterId;
    }

    public Boolean getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(Boolean deleteFlag) {
        this.deleteFlag = deleteFlag;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }
}
