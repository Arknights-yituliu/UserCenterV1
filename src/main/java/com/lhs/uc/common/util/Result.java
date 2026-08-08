package com.lhs.uc.common.util;

import com.lhs.uc.common.enums.ResultCode;

/**
 * 统一返回结果封装
 *
 * @param <T> 数据类型
 * @author UserCenter
 */
public class Result<T> {

    /** 业务状态码 */
    private int code;

    /** 提示信息 */
    private String msg;

    /** 返回数据 */
    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    /**
     * 成功返回（无数据）
     *
     * @return 成功结果
     */
    public static <T> Result<T> success() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功返回（带数据）
     *
     * @param data 返回数据
     * @return 成功结果
     */
    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /**
     * 失败返回（按错误码枚举）
     *
     * @param resultCode 错误码枚举
     * @return 失败结果
     */
    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /**
     * 失败返回（自定义错误码与消息）
     *
     * @param code 错误码
     * @param msg  错误消息
     * @return 失败结果
     */
    public static <T> Result<T> error(int code, String msg) {
        return new Result<>(code, msg, null);
    }

    /**
     * 判断结果是否成功
     *
     * @return 是否成功
     */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
