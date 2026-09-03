package com.orange.common.exception;

/**
 * 请求契约错误，对应 HTTP 400。
 *
 * @author UserCenter
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
