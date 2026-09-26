package com.orange.entity.po;

import java.time.LocalDateTime;

/**
 * 游戏账号绑定关系实体（ak_account_binding）
 *
 * <p>多对多关系：一个 uid 可绑定多个 ak_uid，一个 ak_uid 也可被多个 uid 绑定。
 * 两个时间列由数据库自动维护，其中 {@code updateTime} 表示该账号干员数据最近一次导入时间，
 * 账号列表按它倒序，前端可默认展示最新导入的账号数据。</p>
 *
 * @author UserCenter
 */
public class AkAccountBinding {

    /** 游戏账号 UID */
    private String akUid;

    /** 绑定关系创建时间：首次导入该账号数据时建立绑定并写入 */
    private LocalDateTime createTime;

    /** 该账号干员数据最近一次导入时间 */
    private LocalDateTime updateTime;

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
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
