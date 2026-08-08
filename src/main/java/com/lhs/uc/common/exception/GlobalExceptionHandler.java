package com.lhs.uc.common.exception;

import com.lhs.uc.common.enums.ResultCode;
import com.lhs.uc.common.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器：将各类异常统一转换为 Result 返回
 *
 * @author UserCenter
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 统一返回结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理参数校验异常（@RequestBody 校验失败）
     *
     * @param e 参数校验异常
     * @return 统一返回结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCode.PARAM_VALID_ERROR.getMessage() : fieldError.getDefaultMessage();
        return Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), message);
    }

    /**
     * 处理参数绑定异常（表单参数校验失败）
     *
     * @param e 绑定异常
     * @return 统一返回结果
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCode.PARAM_VALID_ERROR.getMessage() : fieldError.getDefaultMessage();
        return Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), message);
    }

    /**
     * 处理未知系统异常，避免堆栈信息泄露给前端
     *
     * @param e 系统异常
     * @return 统一返回结果
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }
}
