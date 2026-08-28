package com.orange.entity.dto.userconfig;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 保存用户配置请求参数
 *
 * @author UserCenter
 */
public class UserConfigSaveRequest {

    /** 配置 id（编辑时传，为空表示新增） */
    private Long id;

    /** 配置分类 */
    @NotBlank(message = "配置分类不能为空")
    @Size(max = 64, message = "配置分类长度不能超过 64")
    private String category;

    /** 配置版本 */
    @Size(max = 32, message = "配置版本长度不能超过 32")
    private String version;

    /** 来源：web/mini_app 等 */
    @Size(max = 32, message = "来源长度不能超过 32")
    private String source;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String note;

    /** 配置内容（JSON 对象或 JSON 字符串） */
    @NotNull(message = "配置内容不能为空")
    private Object config;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
