package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 游戏账号物品存量实体（ak_item_data）：一行一个账号，整份 JSON 文本存储
 *
 * <p>items 数组整体序列化为一个 JSON 字符串，随干员数据一同上报；
 * 每次携带 items 时覆盖写入该账号已有物品数据，不做行级拆分与比对。</p>
 *
 * @author UserCenter
 */
@TableName("ak_item_data")
public class AkItemData {

    /** 数据库自增行 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏账号 UID，数据归属键 */
    private String akUid;

    /** 物品存量 JSON 文本（items 数组整体序列化） */
    private String itemData;

    /** 首次保存时间 */
    private LocalDateTime createTime;

    /** 最近一次携带 items 的上传时间 */
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

    public String getItemData() {
        return itemData;
    }

    public void setItemData(String itemData) {
        this.itemData = itemData;
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
