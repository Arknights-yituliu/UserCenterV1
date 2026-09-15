package com.orange.common.exception;

/**
 * 请求体超过单次请求大小预算，对应业务错误码 10006。
 *
 * <p>由请求体计数流在读取过程中抛出，会被 Jackson 包装为 HttpMessageNotReadableException，
 * 异常处理器沿 cause 链识别后按超限返回。</p>
 *
 * @author UserCenter
 */
public class PayloadTooLargeException extends RuntimeException {

    /**
     * 构造异常
     *
     * @param message 错误消息
     */
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
