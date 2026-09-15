package com.orange.entity.vo.akoperator;

/**
 * 干员数据批量保存结果：三项数量之和等于本次传入的记录数
 *
 * @author UserCenter
 */
public class OperatorSaveResultVO {

    /** 新增记录数 */
    private int createdCount;

    /** 属性实际变化并更新的记录数 */
    private int updatedCount;

    /** 与已存数据完全相同、未写库的记录数 */
    private int unchangedCount;

    public OperatorSaveResultVO() {
    }

    /**
     * 构造保存结果
     *
     * @param createdCount   新增记录数
     * @param updatedCount   更新记录数
     * @param unchangedCount 未变更记录数
     */
    public OperatorSaveResultVO(int createdCount, int updatedCount, int unchangedCount) {
        this.createdCount = createdCount;
        this.updatedCount = updatedCount;
        this.unchangedCount = unchangedCount;
    }

    public int getCreatedCount() {
        return createdCount;
    }

    public void setCreatedCount(int createdCount) {
        this.createdCount = createdCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getUnchangedCount() {
        return unchangedCount;
    }

    public void setUnchangedCount(int unchangedCount) {
        this.unchangedCount = unchangedCount;
    }
}
