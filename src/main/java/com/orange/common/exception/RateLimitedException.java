package com.orange.common.exception;

import com.orange.common.enums.ResultCode;

/**
 * 限流异常：业务错误码为 3xxxx 限流分段，响应 data 携带建议等待秒数。
 *
 * @author UserCenter
 */
public class RateLimitedException extends BusinessException {

    /** 建议客户端等待的秒数 */
    private final long retryAfterSeconds;

    /**
     * 构造限流异常
     *
     * @param resultCode        限流错误码
     * @param retryAfterSeconds 建议等待秒数
     */
    public RateLimitedException(ResultCode resultCode, long retryAfterSeconds) {
        super(resultCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * 获取建议等待秒数
     *
     * @return 秒数
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
