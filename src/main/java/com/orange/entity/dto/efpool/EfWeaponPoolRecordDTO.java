package com.orange.entity.dto.efpool;

/**
 * 单条武器寻访记录 DTO，对应终末地武器寻访记录接口返回的单条数据
 *
 * @author UserCenter
 */
public class EfWeaponPoolRecordDTO {

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
}
