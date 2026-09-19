package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 游戏角色信息实体（ak_player_info）：只记录 ak_uid 与创建时间，按 ak_uid 去重，可被多个用户中心 uid 共享
 *
 * @author UserCenter
 */
@TableName("ak_player_info")
public class AkPlayerInfo {

    /** 数据库行 ID，仅用于内部更新 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏账号 UID，数据归属键 */
    private String akUid;

    /** 记录创建时间的 Unix 毫秒时间戳，服务端生成 */
    private Long createTime;

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

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }
}
