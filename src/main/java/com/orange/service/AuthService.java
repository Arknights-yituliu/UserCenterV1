package com.orange.service;

import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.dto.auth.ResetPasswordRequest;
import com.orange.entity.vo.auth.LoginVO;
import com.orange.entity.vo.auth.ServerLoginVO;

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
     * 发送重设密码验证码到账号绑定的邮箱
     *
     * <p>账号为邮箱或用户名；未绑定邮箱的账号无法自助重设（需先登录绑定邮箱）</p>
     *
     * @param account 账号（邮箱或用户名）
     * @param ip      请求 IP（限流维度）
     */
    void sendResetCode(String account, String ip);

    /**
     * 通过邮箱验证码重置密码（重置后踢出该用户全部会话）
     *
     * @param request 重设参数（账号 + 验证码 + 新密码）
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * 登出：删除 Redis 会话
     *
     * @param token 会话 token
     */
    void logout(String token);

    /**
     * 服务端登录（供旧系统服务端调用）：以 client_id + client_secret 认证后，
     * 校验账号密码并返回用户信息（不签发 UC 会话，邮箱脱敏）
     *
     * @param clientId     OAuth 客户端 ID
     * @param clientSecret 客户端密钥
     * @param account      登录账号（邮箱或用户名）
     * @param password     明文密码
     * @return 用户信息（uid/昵称/头像/脱敏邮箱/状态）
     */
    ServerLoginVO serverLogin(String clientId, String clientSecret, String account, String password);

    /**
     * 签发会话：生成 token 并写入 Redis（设备数不限，删除 key 即踢下线）
     *
     * @param uid   用户 uid
     * @param appId 来源应用 AppId（可为空）
     * @return 会话 token
     */
    String createSession(Long uid, String appId);
}
