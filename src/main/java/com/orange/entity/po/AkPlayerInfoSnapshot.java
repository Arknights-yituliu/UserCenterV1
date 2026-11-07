package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 森空岛 player/info 数据镜像实体（ak_player_info_snapshot）：一行一个账号，整份文本存储
 *
 * <p>前端把 player/info 的数据以文本形式上报，服务端不解析、不校验结构、不抽取节点，
 * 原样写入 {@code player_data}。每个账号只保留最新一份，重复上报即整段覆盖。</p>
 *
 * @author UserCenter
 */
@TableName("ak_player_info_snapshot")
public class AkPlayerInfoSnapshot {

    /** 数据库自增行 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏账号 UID，数据归属键 */
    private String akUid;

    /** 前端上报的 player/info 文本，原样落库 */
    private String playerData;

    /** 首次写入时间 */
    private LocalDateTime createTime;

    /** 最近一次覆盖写入时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    public String getPlayerData() {
        return playerData;
    }

    public void setPlayerData(String playerData) {
        this.playerData = playerData;
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
