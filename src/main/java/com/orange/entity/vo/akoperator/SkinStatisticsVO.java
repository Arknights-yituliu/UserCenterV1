package com.orange.entity.vo.akoperator;

/**
 * 皮肤持有统计结果
 *
 * <p>{@code own} 为拥有该皮肤的账号数，{@code totalAccounts} 为本轮参与统计的账号总数，
 * 二者来自同一轮扫描，持有率 = own / totalAccounts。</p>
 *
 * @author UserCenter
 */
public class SkinStatisticsVO {

    /** 皮肤编码，对应 ak_skin_state.skin_id */
    private String skinId;

    /** 拥有该皮肤的账号数 */
    private long own;

    /** 本轮参与统计的账号总数（持有率分母） */
    private long totalAccounts;

    public String getSkinId() {
        return skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = skinId;
    }

    public long getOwn() {
        return own;
    }

    public void setOwn(long own) {
        this.own = own;
    }

    public long getTotalAccounts() {
        return totalAccounts;
    }

    public void setTotalAccounts(long totalAccounts) {
        this.totalAccounts = totalAccounts;
    }
}
