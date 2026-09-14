package com.orange.service;

/**
 * 邮箱验证码服务接口
 *
 * @author UserCenter
 */
public interface EmailCodeService {

    /**
     * 发送邮箱验证码（带发送限流）
     *
     * <p>usage=register 时会在发信前校验邮箱是否已注册，已注册抛 EMAIL_ALREADY_EXISTS，
     * 该次调用不消耗邮箱维度的发送间隔额度。</p>
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
