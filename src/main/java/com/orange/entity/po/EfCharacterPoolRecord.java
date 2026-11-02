package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 干员寻访记录实体（character_pool_record）
 *
 * <p>沿用终末地一图流原有表结构与表名，一行一条寻访记录，按 role_id + seq_id 去重。</p>
 *
 * @author UserCenter
 */
@TableName("character_pool_record")
public class EfCharacterPoolRecord {

    /** 记录 ID，主键 */
    @TableId(type = IdType.INPUT)
    private Long id;

    /** 终末地游戏内角色 UID */
    private String roleId;

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

    /** 语言 */
    private String lang;

    /** 卡池类型 */
    private String poolType;

    /** 服务器 ID */
    private String serverId;

    public EfCharacterPoolRecord() {
    }

    public EfCharacterPoolRecord(Long id, String roleId, String poolId, String poolName, String charId, String charName,
                                 Integer rarity, Boolean isFree, Boolean isNew, String gachaTs, String seqId,
                                 String lang, String poolType, String serverId) {
        this.id = id;
        this.roleId = roleId;
        this.poolId = poolId;
        this.poolName = poolName;
        this.charId = charId;
        this.charName = charName;
        this.rarity = rarity;
        this.isFree = isFree;
        this.isNew = isNew;
        this.gachaTs = gachaTs;
        this.seqId = seqId;
        this.lang = lang;
        this.poolType = poolType;
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

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getPoolType() {
        return poolType;
    }

    public void setPoolType(String poolType) {
        this.poolType = poolType;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
}
