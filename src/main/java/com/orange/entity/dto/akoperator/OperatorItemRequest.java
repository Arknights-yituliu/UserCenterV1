package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 单条干员数据请求参数
 *
 * <p>数值字段允许缺省/JSON null/空字符串，服务端统一归一化为整数 0（与显式传 0 等价，
 * 不表示沿用旧值）；负数与超出列范围的值由 Bean Validation 拒绝，返回业务码 10002。</p>
 *
 * @author UserCenter
 */
public class OperatorItemRequest {

    /** 稳定干员编码，缺失或空必须拒绝，不能归一化为 0 */
    @NotBlank(message = "干员ID不能为空")
    @Pattern(regexp = "^[\\x21-\\x7E]{1,64}$", message = "干员ID必须为不超过64位的可见ASCII字符")
    private String id;

    /** 干员星级，0 表示未提供；5/6 星筛选由调用方完成 */
    @Min(value = 0, message = "rarity不能为负数")
    @Max(value = 6, message = "rarity取值范围为0~6")
    private Integer rarity;

    /** 干员等级（SMALLINT UNSIGNED） */
    @Min(value = 0, message = "level不能为负数")
    @Max(value = 65535, message = "level取值范围为0~65535")
    private Integer level;

    /** 精英化阶段 */
    @Min(value = 0, message = "evolvePhase不能为负数")
    @Max(value = 255, message = "evolvePhase取值范围为0~255")
    private Integer evolvePhase;

    /** 基础技能等级 */
    @Min(value = 0, message = "mainSkillLevel不能为负数")
    @Max(value = 255, message = "mainSkillLevel取值范围为0~255")
    private Integer mainSkillLevel;

    /** 技能 1 等级或状态 */
    @Min(value = 0, message = "skill1不能为负数")
    @Max(value = 255, message = "skill1取值范围为0~255")
    private Integer skill1;

    /** 技能 2 等级或状态 */
    @Min(value = 0, message = "skill2不能为负数")
    @Max(value = 255, message = "skill2取值范围为0~255")
    private Integer skill2;

    /** 技能 3 等级或状态 */
    @Min(value = 0, message = "skill3不能为负数")
    @Max(value = 255, message = "skill3取值范围为0~255")
    private Integer skill3;

    /** X 模组数值 */
    @Min(value = 0, message = "equipX不能为负数")
    @Max(value = 255, message = "equipX取值范围为0~255")
    private Integer equipX;

    /** Y 模组数值 */
    @Min(value = 0, message = "equipY不能为负数")
    @Max(value = 255, message = "equipY取值范围为0~255")
    private Integer equipY;

    /** D 模组数值 */
    @Min(value = 0, message = "equipD不能为负数")
    @Max(value = 255, message = "equipD取值范围为0~255")
    private Integer equipD;

    /** A 模组数值 */
    @Min(value = 0, message = "equipA不能为负数")
    @Max(value = 255, message = "equipA取值范围为0~255")
    private Integer equipA;

    /** B 模组数值 */
    @Min(value = 0, message = "equipB不能为负数")
    @Max(value = 255, message = "equipB取值范围为0~255")
    private Integer equipB;

    /** 潜能等级，不是星级 */
    @Min(value = 0, message = "potentialRank不能为负数")
    @Max(value = 255, message = "potentialRank取值范围为0~255")
    private Integer potentialRank;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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
}
