package com.orange.entity.vo.akoperator;

import java.util.Map;

/**
 * 干员养成数据统计结果（对应一图流统计脚本的输出格式）
 *
 * <p>{@code own} 为拥有该干员的记录总数（operatorOwnedCount）；{@code sampleSize} 为最近一次数据变更时间
 * 不早于该干员实装时间的游戏账号数，未能匹配到实装时间的干员记为 0；其余字段为该干员在全部记录上的
 * 取值分布，key 为属性取值、value 为该取值的记录数，各分布之和等于 {@code own}。</p>
 *
 * @author UserCenter
 */
public class OperatorStatisticsVO {

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
    private Map<String, Long> modA;

    /** X 模组数值分布（对应 equip_x） */
    private Map<String, Long> modX;

    /** Y 模组数值分布（对应 equip_y） */
    private Map<String, Long> modY;

    /** D 模组数值分布（对应 equip_d） */
    private Map<String, Long> modD;

    /** B 模组数值分布（对应 equip_b） */
    private Map<String, Long> modB;

    public String getCharId() {
        return charId;
    }

    public void setCharId(String charId) {
        this.charId = charId;
    }

    public long getOwn() {
        return own;
    }

    public void setOwn(long own) {
        this.own = own;
    }

    public long getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(long sampleSize) {
        this.sampleSize = sampleSize;
    }

    public Map<String, Long> getElite() {
        return elite;
    }

    public void setElite(Map<String, Long> elite) {
        this.elite = elite;
    }

    public Map<String, Long> getSkill1() {
        return skill1;
    }

    public void setSkill1(Map<String, Long> skill1) {
        this.skill1 = skill1;
    }

    public Map<String, Long> getSkill2() {
        return skill2;
    }

    public void setSkill2(Map<String, Long> skill2) {
        this.skill2 = skill2;
    }

    public Map<String, Long> getSkill3() {
        return skill3;
    }

    public void setSkill3(Map<String, Long> skill3) {
        this.skill3 = skill3;
    }

    public Map<String, Long> getModA() {
        return modA;
    }

    public void setModA(Map<String, Long> modA) {
        this.modA = modA;
    }

    public Map<String, Long> getModX() {
        return modX;
    }

    public void setModX(Map<String, Long> modX) {
        this.modX = modX;
    }

    public Map<String, Long> getModY() {
        return modY;
    }

    public void setModY(Map<String, Long> modY) {
        this.modY = modY;
    }

    public Map<String, Long> getModD() {
        return modD;
    }

    public void setModD(Map<String, Long> modD) {
        this.modD = modD;
    }

    public Map<String, Long> getModB() {
        return modB;
    }

    public void setModB(Map<String, Long> modB) {
        this.modB = modB;
    }
}
