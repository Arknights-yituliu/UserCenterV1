package com.orange.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志工具类：业务代码统一通过本类记录日志，
 * 后续更换日志框架时仅需修改本类内部实现，业务代码无需改动
 *
 * @author UserCenter
 */
public final class LogUtil {

    private LogUtil() {
    }

    /**
     * 记录 debug 级别日志
     *
     * @param clazz  日志所属类（定位日志来源）
     * @param format 消息模板（SLF4J 占位符风格，如 "参数: {}"）
     * @param args   模板参数
     */
    public static void debug(Class<?> clazz, String format, Object... args) {
        LoggerFactory.getLogger(clazz).debug(format, args);
    }

    /**
     * 记录 info 级别日志
     *
     * @param clazz  日志所属类（定位日志来源）
     * @param format 消息模板（SLF4J 占位符风格）
     * @param args   模板参数
     */
    public static void info(Class<?> clazz, String format, Object... args) {
        LoggerFactory.getLogger(clazz).info(format, args);
    }

    /**
     * 记录 warn 级别日志
     *
     * @param clazz  日志所属类（定位日志来源）
     * @param format 消息模板（SLF4J 占位符风格）
     * @param args   模板参数
     */
    public static void warn(Class<?> clazz, String format, Object... args) {
        LoggerFactory.getLogger(clazz).warn(format, args);
    }

    /**
     * 记录 error 级别日志
     *
     * @param clazz  日志所属类（定位日志来源）
     * @param format 消息模板（SLF4J 占位符风格）
     * @param args   模板参数
     */
    public static void error(Class<?> clazz, String format, Object... args) {
        LoggerFactory.getLogger(clazz).error(format, args);
    }

    /**
     * 记录 error 级别日志（带异常堆栈）
     *
     * @param clazz 日志所属类（定位日志来源）
     * @param msg   日志消息
     * @param e     异常
     */
    public static void error(Class<?> clazz, String msg, Throwable e) {
        LoggerFactory.getLogger(clazz).error(msg, e);
    }
}
