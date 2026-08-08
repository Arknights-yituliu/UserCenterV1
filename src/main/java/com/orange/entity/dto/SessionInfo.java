package com.orange.entity.dto;

import java.time.LocalDateTime;

/**
 * 用户会话信息（存 Redis uc:token:{token}）
 *
 * <p>createTime 用于设备列表展示登录时间；删除 Redis key 即踢下线</p>
 *
 * @author UserCenter
 */
public class SessionInfo {

    /** 用户 uid */
    private Long uid;

    /** 来源应用 AppId */
    private String appId;

    /** 会话创建时间（登录时间） */
    private LocalDateTime createTime;

    public SessionInfo() {
    }

    public SessionInfo(Long uid, String appId, LocalDateTime createTime) {
        this.uid = uid;
        this.appId = appId;
        this.createTime = createTime;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
