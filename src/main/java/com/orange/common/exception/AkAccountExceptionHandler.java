package com.orange.common.exception;

import com.orange.common.enums.ResultCode;
import com.orange.common.util.Result;
import com.orange.controller.AkAccountController;
import com.orange.entity.vo.akoperator.RetryAfterVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 游戏账号与干员数据接口的限定范围异常处理器
 *
 * <p>该业务由应用处理的成功与失败统一使用 HTTP 200，客户端只看 JSON 的 code；
 * 项目 {@link GlobalExceptionHandler} 对部分参数异常仍返回 HTTP 400，因此这里只针对
 * {@link AkAccountController} 覆盖其转换方式，不改动其他业务的返回行为。</p>
 *
 * <p>发生在接口方法之前的登录鉴权失败（拦截器抛出的 {@link BusinessException}）同样由本处理器转换，
 * 因为异常解析时处理器方法已知。</p>
 *
 * @author UserCenter
 */
@Order(0)
@RestControllerAdvice(assignableTypes = AkAccountController.class)
public class AkAccountExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AkAccountExceptionHandler.class);

    /** 沿 cause 链查找根因时的最大深度，避免异常链成环导致死循环 */
    private static final int MAX_CAUSE_DEPTH = 10;

    /**
     * 处理上传限流：业务码 30006，等待秒数通过 data.retryAfterSeconds 返回，不依赖 Retry-After 响应头
     *
     * @param e 限流异常
     * @return HTTP 200 + 限流业务码与等待秒数
     */
    @ExceptionHandler(RateLimitedException.class)
    public Result<RetryAfterVO> handleRateLimited(RateLimitedException e) {
        return Result.error(e.getCode(), e.getMessage(), new RetryAfterVO(e.getRetryAfterSeconds()));
    }

    /**
     * 处理业务异常（未登录、未绑定、限流设施不可用、冲突重试耗尽等）
     *
     * @param e 业务异常
     * @return HTTP 200 + 业务码
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理请求体超过大小预算
     *
     * @param e 请求体超限异常
     * @return HTTP 200 + 业务码 10006
     */
    @ExceptionHandler(PayloadTooLargeException.class)
    public Result<Void> handlePayloadTooLarge(PayloadTooLargeException e) {
        return Result.error(ResultCode.REQUEST_BODY_TOO_LARGE.getCode(),
                ResultCode.REQUEST_BODY_TOO_LARGE.getMessage());
    }

    /**
     * 处理请求体读取失败：计数流超限按 10006 返回，其余（JSON 语法错误、字段类型非法）按 10002 返回
     *
     * @param e 请求体不可读异常
     * @return HTTP 200 + 业务码 10006 或 10002
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleMessageNotReadable(HttpMessageNotReadableException e) {
        if (hasPayloadTooLargeCause(e)) {
            return Result.error(ResultCode.REQUEST_BODY_TOO_LARGE.getCode(),
                    ResultCode.REQUEST_BODY_TOO_LARGE.getMessage());
        }
        log.debug("干员接口请求体解析失败", e);
        return Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), "请求体格式错误或字段类型非法");
    }

    /**
     * 处理请求体参数校验失败（@RequestBody 上的 Bean Validation）
     *
     * @param e 参数校验异常
     * @return HTTP 200 + 业务码 10002 与首个字段错误消息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleArgumentNotValid(MethodArgumentNotValidException e) {
        return Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), firstFieldMessage(e));
    }

    /**
     * 处理参数绑定校验失败
     *
     * @param e 绑定异常
     * @return HTTP 200 + 业务码 10002 与首个字段错误消息
     */
    @ExceptionHandler(BindException.class)
    public Result<Void> handleBindException(BindException e) {
        return Result.error(ResultCode.PARAM_VALID_ERROR.getCode(), firstFieldMessage(e));
    }

    /**
     * 兜底处理未知异常，保证本接口任何应用内失败都返回 HTTP 200 + 业务码，且不泄露堆栈
     *
     * @param e 系统异常
     * @return HTTP 200 + 业务码 40001
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("干员数据接口系统异常", e);
        return Result.error(ResultCode.SYSTEM_ERROR);
    }

    /**
     * 提取首个字段错误消息，无字段错误时返回默认提示
     *
     * @param e 绑定异常
     * @return 错误消息
     */
    private String firstFieldMessage(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        return fieldError == null ? ResultCode.PARAM_VALID_ERROR.getMessage() : fieldError.getDefaultMessage();
    }

    /**
     * 沿 cause 链判断是否由请求体计数流超限引起
     *
     * @param e 请求体不可读异常
     * @return 是否为请求体超限
     */
    private boolean hasPayloadTooLargeCause(Throwable e) {
        Throwable cause = e.getCause();
        for (int depth = 0; cause != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (cause instanceof PayloadTooLargeException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
