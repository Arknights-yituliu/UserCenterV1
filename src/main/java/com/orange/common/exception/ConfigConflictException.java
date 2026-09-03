package com.orange.common.exception;

/**
 * 用户配置 CAS 冲突。
 *
 * @author UserCenter
 */
public class ConfigConflictException extends RuntimeException {

    private final String currentHash;

    public ConfigConflictException(String currentHash) {
        super("配置已被更新");
        this.currentHash = currentHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }
}
