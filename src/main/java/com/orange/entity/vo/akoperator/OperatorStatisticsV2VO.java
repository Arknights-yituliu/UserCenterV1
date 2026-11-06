package com.orange.entity.vo.akoperator;

import java.util.Map;

/**
 * 干员养成数据统计结果（V2）
 *
 * <p>字段含义与 {@link OperatorStatisticsVO} 一致，仅把模组分布字段由 {@code mod*} 改名为
 * {@code equip*}，与源表 {@code ak_operator_state} 的 {@code equip_a} 等列名保持一致。</p>
 *
 * <p>{@code own} 为拥有该干员的记录总数；{@code sampleSize} 为最近一次数据变更时间不早于
 * 该干员实装时间的游戏账号数，未能匹配到实装时间的干员记为 0；其余字段为该干员在全部记录上的
 * 取值分布，key 为属性取值、value 为该取值的记录数，各分布之和等于 {@code own}。</p>
 *
 * @author UserCenter
 */
public class OperatorStatisticsV2VO {

    /** 干员编码，对应 ak_operator_state.operator_id */
    private String charId;

    /** 拥有该干员的记录总数 */
    private long own;

    /** 有效样本数：最近一次数据变更时间不早于该干员实装时间的游戏账号数 */
    private long sampleSize;

    /** 精英化阶段分布（对应 evolve_phase） */
    private Map<String, Long> elite;

    /** 技能 1 等级或状态分布（对应 skill1） */
    private Map<String, Long> skill1;

    /** 技能 2 等级或状态分布（对应 skill2） */
    private Map<String, Long> skill2;

    /** 技能 3 等级或状态分布（对应 skill3） */
    private Map<String, Long> skill3;

    /** A 模组数值分布（对应 equip_a） */
    private Map<String, Long> equipA;

    /** X 模组数值分布（对应 equip_x） */
    private Map<String, Long> equipX;

    /** Y 模组数值分布（对应 equip_y） */
    private Map<String, Long> equipY;

    /** D 模组数值分布（对应 equip_d） */
    private Map<String, Long> equipD;

    /** B 模组数值分布（对应 equip_b） */
    private Map<String, Long> equipB;

    /**
     * 获取干员编码
     *
     * @return 干员编码
     */
    public String getCharId() {
        return charId;
    }

    /**
     * 设置干员编码
     *
     * @param charId 干员编码
     */
    public void setCharId(String charId) {
        this.charId = charId;
    }

    /**
     * 获取拥有该干员的记录总数
     *
     * @return 拥有该干员的记录总数
     */
    public long getOwn() {
        return own;
    }

    /**
     * 设置拥有该干员的记录总数
     *
     * @param own 拥有该干员的记录总数
     */
    public void setOwn(long own) {
        this.own = own;
    }

    /**
     * 获取有效样本数
     *
     * @return 有效样本数
     */
    public long getSampleSize() {
        return sampleSize;
    }

    /**
     * 设置有效样本数
     *
     * @param sampleSize 有效样本数
     */
    public void setSampleSize(long sampleSize) {
        this.sampleSize = sampleSize;
    }

    /**
     * 获取精英化阶段分布
     *
     * @return 精英化阶段分布
     */
    public Map<String, Long> getElite() {
        return elite;
    }

    /**
     * 设置精英化阶段分布
     *
     * @param elite 精英化阶段分布
     */
    public void setElite(Map<String, Long> elite) {
        this.elite = elite;
    }

    /**
     * 获取技能 1 分布
     *
     * @return 技能 1 分布
     */
    public Map<String, Long> getSkill1() {
        return skill1;
    }

    /**
     * 设置技能 1 分布
     *
     * @param skill1 技能 1 分布
     */
    public void setSkill1(Map<String, Long> skill1) {
        this.skill1 = skill1;
    }

    /**
     * 获取技能 2 分布
     *
     * @return 技能 2 分布
     */
    public Map<String, Long> getSkill2() {
        return skill2;
    }

    /**
     * 设置技能 2 分布
     *
     * @param skill2 技能 2 分布
     */
    public void setSkill2(Map<String, Long> skill2) {
        this.skill2 = skill2;
    }

    /**
     * 获取技能 3 分布
     *
     * @return 技能 3 分布
     */
    public Map<String, Long> getSkill3() {
        return skill3;
    }

    /**
     * 设置技能 3 分布
     *
     * @param skill3 技能 3 分布
     */
    public void setSkill3(Map<String, Long> skill3) {
        this.skill3 = skill3;
    }

    /**
     * 获取 A 模组分布
     *
     * @return A 模组分布
     */
    public Map<String, Long> getEquipA() {
        return equipA;
    }

    /**
     * 设置 A 模组分布
     *
     * @param equipA A 模组分布
     */
    public void setEquipA(Map<String, Long> equipA) {
        this.equipA = equipA;
    }

    /**
     * 获取 X 模组分布
     *
     * @return X 模组分布
     */
    public Map<String, Long> getEquipX() {
        return equipX;
    }

    /**
     * 设置 X 模组分布
     *
     * @param equipX X 模组分布
     */
    public void setEquipX(Map<String, Long> equipX) {
        this.equipX = equipX;
    }

    /**
     * 获取 Y 模组分布
     *
     * @return Y 模组分布
     */
    public Map<String, Long> getEquipY() {
        return equipY;
    }

    /**
     * 设置 Y 模组分布
     *
     * @param equipY Y 模组分布
     */
    public void setEquipY(Map<String, Long> equipY) {
        this.equipY = equipY;
    }

    /**
     * 获取 D 模组分布
     *
     * @return D 模组分布
     */
    public Map<String, Long> getEquipD() {
        return equipD;
    }

    /**
     * 设置 D 模组分布
     *
     * @param equipD D 模组分布
     */
    public void setEquipD(Map<String, Long> equipD) {
        this.equipD = equipD;
    }

    /**
     * 获取 B 模组分布
     *
     * @return B 模组分布
     */
    public Map<String, Long> getEquipB() {
        return equipB;
    }

    /**
     * 设置 B 模组分布
     *
     * @param equipB B 模组分布
     */
    public void setEquipB(Map<String, Long> equipB) {
        this.equipB = equipB;
    }
}
