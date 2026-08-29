package com.orange.entity.vo;

import java.time.LocalDateTime;

/**
 * 用户配置视图对象
 *
 * @author UserCenter
 */
public class UserConfigVO {

    /** 配置 id */
    private Long id;

    /** 客户端标识 */
    private String clientId;

    /** 配置分类 */
    private String category;

    /** 配置版本 */
    private String version;

    /** 配置名称（同版本下的命名快照，可空） */
    private String name;

    /** 来源：web/mini_app 等 */
    private String source;

    /** 备注 */
    private String note;

    /** 配置内容（JSON 对象） */
    private Object config;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Object getConfig() {
        return config;
    }

    public void setConfig(Object config) {
        this.config = config;
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
