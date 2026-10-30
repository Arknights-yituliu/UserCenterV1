package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户手动修改版干员数据实体（ak_operator_manual）：一行一个干员，按用户 uid 归属
 *
 * <p>与 {@link AkOperatorState}（游戏账号自动导入、被全部绑定用户共享）不同，本表保存
 * 用户手工调整后的干员数据，并额外记录「是否持有」。一个用户只能有一份手动修改版，
 * 由唯一键 {@code uk_uid_operator_manual (uid, operator_id)} 保证。</p>
 *
 * <p>所有数值属性非空，缺省/null/空字符串在保存前统一归一化为 0。</p>
 *
 * @author UserCenter
 */
@TableName("ak_operator_manual")
public class AkOperatorManual {

    /** 主键，响应中称 recordId */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户中心 UID，数据归属键 */
    private Long uid;

    /** 稳定干员编码，对应 JSON 中的 id */
    private String operatorId;

    /** 是否持有：0 否，1 是 */
    private Integer owned;

    /** 干员星级，0 表示未提供 */
    private Integer rarity;

    /** 干员等级 */
    private Integer level;

    /** 精英化阶段 */
    private Integer evolvePhase;

    /** 基础技能等级 */
    private Integer mainSkillLevel;

    /** 技能 1 等级或状态 */
    private Integer skill1;

    /** 技能 2 等级或状态 */
    private Integer skill2;

    /** 技能 3 等级或状态 */
    private Integer skill3;

    /** X 模组数值 */
    private Integer equipX;

    /** Y 模组数值 */
    private Integer equipY;

    /** D 模组数值 */
    private Integer equipD;

    /** A 模组数值 */
    private Integer equipA;

    /** B 模组数值 */
    private Integer equipB;

    /** 潜能等级，不是星级 */
    private Integer potentialRank;

    /** 该干员手动版最后一次实际变更时间，服务端生成 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Integer getOwned() {
        return owned;
    }

    public void setOwned(Integer owned) {
        this.owned = owned;
    }

    public Integer getRarity() {
        return rarity;
    }

    public void setRarity(Integer rarity) {
        this.rarity = rarity;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Integer getEvolvePhase() {
        return evolvePhase;
    }

    public void setEvolvePhase(Integer evolvePhase) {
        this.evolvePhase = evolvePhase;
    }

    public Integer getMainSkillLevel() {
        return mainSkillLevel;
    }

    public void setMainSkillLevel(Integer mainSkillLevel) {
        this.mainSkillLevel = mainSkillLevel;
    }

    public Integer getSkill1() {
        return skill1;
    }

    public void setSkill1(Integer skill1) {
        this.skill1 = skill1;
    }

    public Integer getSkill2() {
        return skill2;
    }

    public void setSkill2(Integer skill2) {
        this.skill2 = skill2;
    }

    public Integer getSkill3() {
        return skill3;
    }

    public void setSkill3(Integer skill3) {
        this.skill3 = skill3;
    }

    public Integer getEquipX() {
        return equipX;
    }

    public void setEquipX(Integer equipX) {
        this.equipX = equipX;
    }

    public Integer getEquipY() {
        return equipY;
    }

    public void setEquipY(Integer equipY) {
        this.equipY = equipY;
    }

    public Integer getEquipD() {
        return equipD;
    }

    public void setEquipD(Integer equipD) {
        this.equipD = equipD;
    }

    public Integer getEquipA() {
        return equipA;
    }

    public void setEquipA(Integer equipA) {
        this.equipA = equipA;
    }

    public Integer getEquipB() {
        return equipB;
    }

    public void setEquipB(Integer equipB) {
        this.equipB = equipB;
    }

    public Integer getPotentialRank() {
        return potentialRank;
    }

    public void setPotentialRank(Integer potentialRank) {
        this.potentialRank = potentialRank;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
