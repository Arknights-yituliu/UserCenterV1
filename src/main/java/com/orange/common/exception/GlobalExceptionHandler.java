package com.orange.common.exception;

import com.orange.common.enums.ResultCode;
import com.orange.common.util.Result;
import com.orange.entity.vo.UserConfigConflictVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * 处理用户配置 CAS 冲突。
     *
     * @param e CAS 冲突异常
     * @return HTTP 409 与数据库当前 hash
     */
    @ExceptionHandler(ConfigConflictException.class)
    public ResponseEntity<Result<UserConfigConflictVO>> handleConfigConflict(ConfigConflictException e) {
        Result<UserConfigConflictVO> result = new Result<>(
                ResultCode.CONFIG_HASH_CONFLICT.getCode(),
                ResultCode.CONFIG_HASH_CONFLICT.getMessage(),
                new UserConfigConflictVO(e.getCurrentHash()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    /**
     * 处理请求契约错误。
     *
     * @param e 请求错误
     * @return HTTP 400
     */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Result<Void>> handleBadRequest(BadRequestException e) {
        return ResponseEntity.badRequest().body(
                Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), e.getMessage()));
    }

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
    public ResponseEntity<Result<Void>> handleValidException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCode.PARAM_VALID_ERROR.getMessage() : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), message));
    }

    /**
     * 处理参数绑定异常（表单参数校验失败）
     *
     * @param e 绑定异常
     * @return 统一返回结果
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? ResultCode.PARAM_VALID_ERROR.getMessage() : fieldError.getDefaultMessage();
        return ResponseEntity.badRequest().body(Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), message));
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
