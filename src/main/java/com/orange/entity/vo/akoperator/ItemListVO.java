package com.orange.entity.vo.akoperator;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏账号物品存量全量读取结果：返回该账号最近一次上报的完整物品快照
 *
 * @author UserCenter
 */
public class ItemListVO {

    /** 游戏账号 UID */
    private String akUid;

    /** 物品记录列表（最近一次携带 items 上传的完整快照），无数据时为空数组 */
    private List<ItemVO> items;

    /** 最近一次携带 items 的上传时间，该账号从未上报过物品时为 null */
    private LocalDateTime updateTime;

    public ItemListVO() {
    }

    /**
     * 构造全量读取结果
     *
     * @param akUid      游戏账号 UID
     * @param items      物品记录列表
     * @param updateTime 最近一次携带 items 的上传时间，可为 null
     */
    public ItemListVO(String akUid, List<ItemVO> items, LocalDateTime updateTime) {
        this.akUid = akUid;
        this.items = items;
        this.updateTime = updateTime;
    }

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    public List<ItemVO> getItems() {
        return items;
    }

    public void setItems(List<ItemVO> items) {
        this.items = items;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
