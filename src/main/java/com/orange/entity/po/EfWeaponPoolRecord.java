package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 武器寻访记录实体（weapon_pool_record）
 *
 * <p>沿用终末地一图流原有表结构与表名，一行一条武器寻访记录。</p>
 *
 * @author UserCenter
 */
@TableName("weapon_pool_record")
public class EfWeaponPoolRecord {

    /** 记录 ID，主键 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 终末地游戏内角色 UID */
    private String roleId;

    /** 卡池 ID */
    private String poolId;

    /** 卡池名称 */
    private String poolName;

    /** 武器 ID */
    private String weaponId;

    /** 武器名称 */
    private String weaponName;

    /** 武器类型 */
    private String weaponType;

    /** 稀有度 */
    private Integer rarity;

    /** 是否新武器 */
    private Boolean isNew;

    /** 寻访时间戳（毫秒） */
    private String gachaTs;

    /** 序列 ID */
    private String seqId;

    /** 语言 */
    private String lang;

    /** 服务器 ID */
    private String serverId;

    public EfWeaponPoolRecord() {
    }

    public EfWeaponPoolRecord(Long id, String roleId, String poolId, String poolName, String weaponId, String weaponName,
                              String weaponType, Integer rarity, Boolean isNew, String gachaTs, String seqId,
                              String lang, String serverId) {
        this.id = id;
        this.roleId = roleId;
        this.poolId = poolId;
        this.poolName = poolName;
        this.weaponId = weaponId;
        this.weaponName = weaponName;
        this.weaponType = weaponType;
        this.rarity = rarity;
        this.isNew = isNew;
        this.gachaTs = gachaTs;
        this.seqId = seqId;
        this.lang = lang;
        this.serverId = serverId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getPoolId() {
        return poolId;
    }

    public void setPoolId(String poolId) {
        this.poolId = poolId;
    }

    public String getPoolName() {
        return poolName;
    }

    public void setPoolName(String poolName) {
        this.poolName = poolName;
    }

    public String getWeaponId() {
        return weaponId;
    }

    public void setWeaponId(String weaponId) {
        this.weaponId = weaponId;
    }

    public String getWeaponName() {
        return weaponName;
    }

    public void setWeaponName(String weaponName) {
        this.weaponName = weaponName;
    }

    public String getWeaponType() {
        return weaponType;
    }

    public void setWeaponType(String weaponType) {
        this.weaponType = weaponType;
    }

    public Integer getRarity() {
        return rarity;
    }

    public void setRarity(Integer rarity) {
        this.rarity = rarity;
    }

    public Boolean getIsNew() {
        return isNew;
    }

    public void setIsNew(Boolean isNew) {
        this.isNew = isNew;
    }

    public String getGachaTs() {
        return gachaTs;
    }

    public void setGachaTs(String gachaTs) {
        this.gachaTs = gachaTs;
    }

    public String getSeqId() {
        return seqId;
    }

    public void setSeqId(String seqId) {
        this.seqId = seqId;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
