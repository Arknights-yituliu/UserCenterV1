package com.orange.entity.vo.akoperator;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏账号已拥有皮肤全量读取结果
 *
 * <p>返回该账号当前完整皮肤集合，按皮肤编码升序；从未上报过皮肤的账号返回空数组与 null 时间。</p>
 *
 * @author UserCenter
 */
public class SkinListVO {

    /** 游戏账号 UID */
    private String akUid;

    /** 皮肤记录列表，无数据时为空数组 */
    private List<SkinVO> items;

    /** 皮肤数据最近一次实际变更时间，该账号从未上报过皮肤时为 null */
    private LocalDateTime updateTime;

    public SkinListVO() {
    }

    /**
     * 构造全量读取结果
     *
     * @param akUid      游戏账号 UID
     * @param items      皮肤记录列表
     * @param updateTime 最近一次实际变更时间，可为 null
     */
    public SkinListVO(String akUid, List<SkinVO> items, LocalDateTime updateTime) {
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

    public List<SkinVO> getItems() {
        return items;
    }

    public void setItems(List<SkinVO> items) {
        this.items = items;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
