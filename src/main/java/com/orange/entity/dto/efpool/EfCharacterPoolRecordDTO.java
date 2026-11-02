package com.orange.entity.dto.efpool;

/**
 * 单条干员寻访记录 DTO，对应终末地寻访记录接口返回的单条数据
 *
 * @author UserCenter
 */
public class EfCharacterPoolRecordDTO {

    /** 卡池 ID */
    private String poolId;

    /** 卡池名称 */
    private String poolName;

    /** 角色 ID */
    private String charId;

    /** 角色名称 */
    private String charName;

    /** 稀有度 */
    private Integer rarity;

    /** 是否免费抽取 */
    private Boolean isFree;

    /** 是否新角色 */
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

    public String getCharId() {
        return charId;
    }

    public void setCharId(String charId) {
        this.charId = charId;
    }

    public String getCharName() {
        return charName;
    }

    public void setCharName(String charName) {
        this.charName = charName;
    }

    public Integer getRarity() {
        return rarity;
    }

    public void setRarity(Integer rarity) {
        this.rarity = rarity;
    }

    public Boolean getIsFree() {
        return isFree;
    }

    public void setIsFree(Boolean isFree) {
        this.isFree = isFree;
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
