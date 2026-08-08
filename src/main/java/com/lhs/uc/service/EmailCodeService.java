package com.lhs.uc.service;

/**
 * 邮箱验证码服务接口
 *
 * @author UserCenter
 */
public interface EmailCodeService {

    /**
     * 发送邮箱验证码（带发送限流）
     *
     * @param email 目标邮箱
     * @param usage 验证码用途
     * @param ip    请求 IP（限流维度）
     */
    void sendCode(String email, String usage, String ip);

    /**
     * 校验验证码（校验通过后立即删除，保证一次性使用）
     *
     * @param email 邮箱
     * @param code  验证码
     */
    void verifyCode(String email, String code);
}
