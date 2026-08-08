package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 登录日志实体（login_log）
 *
 * @author UserCenter
 */
@TableName("login_log")
public class LoginLog {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 uid（失败时可能为空） */
    private Long uid;

    /** 来源应用 */
    private String appId;

    /** 登录方式：password/email_code/wechat/qq */
    private String loginType;

    /** 登录 IP */
    private String ip;

    /** UA */
    private String userAgent;

    /** 结果：1=成功 0=失败 */
    private Integer status;

    /** 登录时间 */
    private LocalDateTime loginTime;

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

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getLoginType() {
        return loginType;
    }

    public void setLoginType(String loginType) {
        this.loginType = loginType;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }
}
