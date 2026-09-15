package com.orange.entity.vo.akoperator;

import java.util.List;

/**
 * 干员数据全量读取结果：始终返回该游戏账号的全部干员，星级筛选由调用方完成
 *
 * @author UserCenter
 */
public class OperatorListVO {

    /** 游戏账号 UID */
    private String akUid;

    /** 干员记录列表，无数据时为空数组 */
    private List<OperatorVO> items;

    public OperatorListVO() {
    }

    /**
     * 构造全量读取结果
     *
     * @param akUid 游戏账号 UID
     * @param items 干员记录列表
     */
    public OperatorListVO(String akUid, List<OperatorVO> items) {
        this.akUid = akUid;
        this.items = items;
    }

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    public List<OperatorVO> getItems() {
        return items;
    }

    public void setItems(List<OperatorVO> items) {
        this.items = items;
    }
}
