package com.orange.entity.vo;

/**
 * 用户配置 CAS 冲突结果。
 *
 * @author UserCenter
 */
public class UserConfigConflictVO {

    private String currentHash;

    public UserConfigConflictVO() {
    }

    public UserConfigConflictVO(String currentHash) {
        this.currentHash = currentHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public void setCurrentHash(String currentHash) {
        this.currentHash = currentHash;
    }
}
