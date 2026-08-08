package com.orange.service;

import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.vo.auth.LoginVO;

/**
 * 认证服务接口：注册、登录、登出、会话签发
 *
 * @author UserCenter
 */
public interface AuthService {

    /**
     * 注册（密码注册 / 邮箱验证码注册），注册成功后直接签发会话登录
     *
     * @param request 注册参数
     * @param ip      注册 IP
     * @param appId   来源应用 AppId
     * @return 登录响应（含 token）
     */
    LoginVO register(RegisterRequest request, String ip, String appId);

    /**
     * 登录（密码 / 邮箱验证码），并记录登录日志
     *
     * @param request 登录参数
     * @param ip      登录 IP
     * @param ua      浏览器 UA
     * @param appId   来源应用 AppId
     * @return 登录响应（含 token）
     */
    LoginVO login(LoginRequest request, String ip, String ua, String appId);

    /**
     * 登出：删除 Redis 会话
     *
     * @param token 会话 token
     */
    void logout(String token);

    /**
     * 签发会话：生成 token 并写入 Redis（设备数不限，删除 key 即踢下线）
     *
     * @param uid   用户 uid
     * @param appId 来源应用 AppId（可为空）
     * @return 会话 token
     */
    String createSession(Long uid, String appId);
}
