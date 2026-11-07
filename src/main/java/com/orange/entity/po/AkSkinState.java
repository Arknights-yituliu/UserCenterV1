package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 游戏账号已拥有皮肤实体（ak_skin_state）：一行一个皮肤，按 ak_uid 归属
 *
 * <p>只记录「拥有关系」与获得时间，不承载皮肤名称、画师、立绘等图鉴元数据。
 * {@code skinId} 取自森空岛 player/info 的 {@code data.skins[].id}，形如
 * {@code char_4064_mlynar@epoque#28}，区分大小写。</p>
 *
 * @author UserCenter
 */
@TableName("ak_skin_state")
public class AkSkinState {

    /** 数据库自增行 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏账号 UID，数据归属键 */
    private String akUid;

    /** 皮肤编码，区分大小写，含 @ 与 # */
    private String skinId;

    /** 获得时间 Unix 秒，0 表示时间未知 */
    private Integer gainTs;

    /** 该皮肤最后一次实际变更时间，服务端生成 */
    private LocalDateTime updatedAt;

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

    public String getSkinId() {
        return skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = skinId;
    }

    public Integer getGainTs() {
        return gainTs;
    }

    public void setGainTs(Integer gainTs) {
        this.gainTs = gainTs;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
