package com.orange.entity.vo.akoperator;

/**
 * 单条物品存量读取结果
 *
 * @author UserCenter
 */
public class ItemVO {

    /** 稳定物品编码 */
    private String itemId;

    /** 物品数量 */
    private Long count;

    public ItemVO() {
    }

    /**
     * 构造单条物品读取结果
     *
     * @param itemId 稳定物品编码
     * @param count  物品数量
     */
    public ItemVO(String itemId, Long count) {
        this.itemId = itemId;
        this.count = count;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }
}
