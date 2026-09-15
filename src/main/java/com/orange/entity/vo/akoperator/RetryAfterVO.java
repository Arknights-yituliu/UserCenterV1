package com.orange.entity.vo.akoperator;

/**
 * 限流响应附加数据：客户端需等待的秒数
 *
 * <p>业务码 30006 的等待秒数通过 Result 的 data 返回，不依赖 Retry-After 响应头。</p>
 *
 * @author UserCenter
 */
public class RetryAfterVO {

    /** 建议等待秒数 */
    private long retryAfterSeconds;

    public RetryAfterVO() {
    }

    /**
     * 构造限流附加数据
     *
     * @param retryAfterSeconds 建议等待秒数
     */
    public RetryAfterVO(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(long retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
