package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * SMTP 邮件渠道配置实体（smtp_config）
 *
 * <p>多渠道邮件降级发送的渠道配置，存数据库可动态调整，无需重启。</p>
 *
 * @author UserCenter
 */
@TableName("smtp_config")
public class SmtpConfig {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 渠道标识，如 mail-163-1 / mail-163-2 */
    private String accountKey;

    /** SMTP 服务器地址 */
    private String host;

    /** SMTP 端口 */
    private Integer port;

    /** 登录账号（发件人邮箱） */
    private String username;

    /** SMTP 授权码 */
    private String password;

    /** 协议，默认 smtp */
    private String protocol;

    /** 默认编码，默认 UTF-8 */
    private String defaultEncoding;

    /** 是否启用 SSL：1=启用 0=关闭 */
    private Boolean sslEnable;

    /** 是否启用该渠道：1=启用 0=停用 */
    private Boolean enabled;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountKey() {
        return accountKey;
    }

    public void setAccountKey(String accountKey) {
        this.accountKey = accountKey;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getDefaultEncoding() {
        return defaultEncoding;
    }

    public void setDefaultEncoding(String defaultEncoding) {
        this.defaultEncoding = defaultEncoding;
    }

    public Boolean getSslEnable() {
        return sslEnable;
    }

    public void setSslEnable(Boolean sslEnable) {
        this.sslEnable = sslEnable;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
