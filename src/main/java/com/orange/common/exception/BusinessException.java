package com.orange.common.exception;

import com.orange.common.enums.ResultCode;

/**
 * 业务异常：服务层抛出，由全局异常处理器统一转换为 Result 返回
 *
 * @author UserCenter
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码 */
    private final int code;

    /**
     * 按错误码枚举构造业务异常
     *
     * @param resultCode 错误码枚举
     */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /**
     * 按错误码枚举 + 自定义消息构造业务异常
     *
     * @param resultCode 错误码枚举
     * @param message    自定义错误消息
     */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public int getCode() {
        return code;
    }
}
