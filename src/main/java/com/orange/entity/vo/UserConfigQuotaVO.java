package com.orange.entity.vo;

/**
 * 用户配置配额使用情况。
 *
 * @author UserCenter
 */
public class UserConfigQuotaVO {

    private long usedBytes;
    private long limitBytes;
    private long remainingBytes;

    public UserConfigQuotaVO() {
    }

    public UserConfigQuotaVO(long usedBytes, long limitBytes) {
        this.usedBytes = usedBytes;
        this.limitBytes = limitBytes;
        this.remainingBytes = Math.max(limitBytes - usedBytes, 0);
    }

    public long getUsedBytes() {
        return usedBytes;
    }

    public void setUsedBytes(long usedBytes) {
        this.usedBytes = usedBytes;
    }

    public long getLimitBytes() {
        return limitBytes;
    }

    public void setLimitBytes(long limitBytes) {
        this.limitBytes = limitBytes;
    }

    public long getRemainingBytes() {
        return remainingBytes;
    }

    public void setRemainingBytes(long remainingBytes) {
        this.remainingBytes = remainingBytes;
    }
}
