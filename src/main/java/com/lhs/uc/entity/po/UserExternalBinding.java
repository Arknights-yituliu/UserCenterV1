package com.lhs.uc.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 第三方绑定实体（user_external_binding）
 *
 * @author UserCenter
 */
@TableName("user_external_binding")
public class UserExternalBinding {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 uid */
    private Long uid;

    /** 第三方类型：wechat/qq */
    private String provider;

    /** 第三方 open_id */
    private String openId;

    /** 第三方 union_id */
    private String unionId;

    /** 绑定时间 */
    private LocalDateTime bindTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getOpenId() {
        return openId;
    }

    public void setOpenId(String openId) {
        this.openId = openId;
    }

    public String getUnionId() {
        return unionId;
    }

    public void setUnionId(String unionId) {
        this.unionId = unionId;
    }

    public LocalDateTime getBindTime() {
        return bindTime;
    }

    public void setBindTime(LocalDateTime bindTime) {
        this.bindTime = bindTime;
    }
}
