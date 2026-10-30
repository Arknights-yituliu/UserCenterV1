package com.orange.entity.vo.akoperator;

/**
 * 手动修改版干员数据视图对象
 *
 * <p>沿用自动导入干员数据的全部字段，额外返回「是否持有」。全部数值字段始终返回数字。</p>
 *
 * @author UserCenter
 */
public class ManualOperatorVO extends OperatorVO {

    /** 是否持有：0 否，1 是 */
    private Integer owned;

    public Integer getOwned() {
        return owned;
    }

    public void setOwned(Integer owned) {
        this.owned = owned;
    }
}
