package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.orange.common.handler.JsonLongMapTypeHandler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 干员养成数据统计结果实体（ak_operator_state_statistics）
 *
 * <p>一行一个干员，只保留最新一轮统计结果，整表由统计任务在同一事务内「清表 + 批量插入」替换。
 * 9 个分布字段以 JSON 字符串存放在 VARCHAR 列里，经 {@link JsonLongMapTypeHandler} 与
 * {@code Map<String, Long>} 互转，key 为属性取值、value 为该取值的记录数，各分布之和等于
 * {@code own}。</p>
 *
 * @author UserCenter
 */
@TableName(value = "ak_operator_state_statistics", autoResultMap = true)
public class AkOperatorStateStatistics {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 干员编码，对应 ak_operator_state.operator_id */
    private String charId;

    /** 拥有该干员的记录总数（operatorOwnedCount） */
    private Long own;

    /** 有效样本数：最近一次数据变更时间不早于该干员实装时间的游戏账号数 */
    private Long sampleSize;

    /** 精英化阶段分布（对应 evolve_phase） */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> elite;

    /** 技能 1 等级或状态分布 */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> skill1;

    /** 技能 2 等级或状态分布 */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> skill2;

    /** 技能 3 等级或状态分布 */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> skill3;

    /** A 模组数值分布（对应 equip_a） */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> modA;

    /** X 模组数值分布（对应 equip_x） */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> modX;

    /** Y 模组数值分布（对应 equip_y） */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> modY;

    /** D 模组数值分布（对应 equip_d） */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> modD;

    /** B 模组数值分布（对应 equip_b） */
    @TableField(typeHandler = JsonLongMapTypeHandler.class)
    private Map<String, Long> modB;

    /** 本轮统计的完成时间 */
    private LocalDateTime statisticsTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCharId() {
        return charId;
    }

    public void setCharId(String charId) {
        this.charId = charId;
    }

    public Long getOwn() {
        return own;
    }

    public void setOwn(Long own) {
        this.own = own;
    }

    public Long getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(Long sampleSize) {
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

    public LocalDateTime getStatisticsTime() {
        return statisticsTime;
    }

    public void setStatisticsTime(LocalDateTime statisticsTime) {
        this.statisticsTime = statisticsTime;
    }
}
