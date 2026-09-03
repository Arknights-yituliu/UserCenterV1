package com.orange.entity.vo;

/**
 * 用户配置保存结果。
 *
 * @author UserCenter
 */
public class UserConfigSaveVO {

    private Long id;
    private String hash;

    public UserConfigSaveVO() {
    }

    public UserConfigSaveVO(Long id, String hash) {
        this.id = id;
        this.hash = hash;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
