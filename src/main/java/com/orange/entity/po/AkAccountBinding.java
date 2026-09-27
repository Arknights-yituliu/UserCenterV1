package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 游戏账号绑定关系实体（ak_account_binding）
 *
 * <p>多对多关系：一个 uid 可绑定多个 ak_uid，一个 ak_uid 也可被多个 uid 绑定。
 * 两个时间列由数据库自动维护，其中 {@code updateTime} 表示该账号干员数据最近一次导入时间，
 * 账号列表按它倒序，前端可默认展示最新导入的账号数据。</p>
 *
 * <p>表主键为代理自增 id，{@code (akUid, ownerUid)} 由唯一键 {@code uk_ak_owner} 保证业务唯一。</p>
 *
 * @author UserCenter
 */
@TableName("ak_account_binding")
public class AkAccountBinding {

    /** 数据库自增行 ID，主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏账号 UID，与 ownerUid 组成业务唯一键 uk_ak_owner */
    private String akUid;

    /** 用户中心 UID（绑定关系的所有者），与 akUid 组成业务唯一键 uk_ak_owner */
    private Long ownerUid;

    /** 绑定关系创建时间：首次导入该账号数据时建立绑定并写入 */
    private LocalDateTime createTime;

    /** 该账号干员数据最近一次导入时间 */
    private LocalDateTime updateTime;

    /**
     * 获取数据库自增主键
     *
     * @return 主键 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置数据库自增主键
     *
     * @param id 主键 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    /**
     * 获取绑定关系所属的用户中心 UID
     *
     * @return 用户中心 UID
     */
    public Long getOwnerUid() {
        return ownerUid;
    }

    /**
     * 设置绑定关系所属的用户中心 UID
     *
     * @param ownerUid 用户中心 UID
     */
    public void setOwnerUid(Long ownerUid) {
        this.ownerUid = ownerUid;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
