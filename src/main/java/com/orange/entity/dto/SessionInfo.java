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

    /** 来源客户端 id */
    private String clientId;

    /** 会话创建时间（登录时间） */
    private LocalDateTime createTime;

    public SessionInfo() {
    }

    public SessionInfo(Long uid, String clientId, LocalDateTime createTime) {
        this.uid = uid;
        this.clientId = clientId;
        this.createTime = createTime;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
