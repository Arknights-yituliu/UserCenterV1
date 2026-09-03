package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户配置实体（user_config）
 *
 * @author UserCenter
 */
@TableName("user_config")
public class UserConfig {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 uid */
    private Long uid;

    /** 客户端标识 */
    private String clientId;

    /** 配置分类 */
    private String category;

    /** 配置版本 */
    private String version;

    /** 配置名称（同版本下的命名快照，可空：空表示该版本默认配置） */
    private String name;

    /** 来源：web/mini_app 等 */
    private String source;

    /** 备注 */
    private String note;

    /** 配置内容（JSON 字符串） */
    private String config;

    /** 配置内容 SHA-256 */
    private String contentHash;

    /** 配置内容 UTF-8 字节数 */
    private Long configBytes;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Long getConfigBytes() {
        return configBytes;
    }

    public void setConfigBytes(Long configBytes) {
        this.configBytes = configBytes;
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
