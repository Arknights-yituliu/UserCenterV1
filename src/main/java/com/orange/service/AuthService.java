package com.orange.service;

import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.dto.auth.ResetPasswordRequest;
import com.orange.entity.vo.auth.LoginVO;
import com.orange.entity.vo.auth.ServerLoginVO;
import com.orange.entity.vo.oauth.DirectLoginSessionVO;
import com.orange.entity.vo.oauth.DirectLoginTicketVO;

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
     * @param clientId   来源客户端 id
     * @return 登录响应（含 token）
     */
    LoginVO register(RegisterRequest request, String ip, String clientId);

    /**
     * 登录（密码 / 邮箱验证码），并记录登录日志
     *
     * @param request 登录参数
     * @param ip      登录 IP
     * @param ua      浏览器 UA
     * @param clientId   来源客户端 id
     * @return 登录响应（含 token）
     */
    LoginVO login(LoginRequest request, String ip, String ua, String clientId);

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
     * 直连登录-发起会话（旧系统后端调用）：client 认证通过后签发短时发起会话凭证，
     * 前端持凭证才能调直连登录，避免 client_secret 暴露给浏览器
     *
     * @param clientId     OAuth 客户端 ID
     * @param clientSecret 客户端密钥
     * @return 发起会话凭证（channel）及有效期
     */
    DirectLoginSessionVO createDirectSession(String clientId, String clientSecret);

    /**
     * 直连登录-提交凭证（前端直接调用）：持发起会话凭证提交登录凭证（密码或邮箱验证码），
     * 校验通过后签发一次性登录票据（凭证不经过旧系统后端）
     *
     * @param channel     发起会话凭证
     * @param accountType 登录方式：password=账号密码（默认）/ email=邮箱验证码
     * @param account     登录账号（密码方式为邮箱或用户名；邮箱方式为邮箱）
     * @param password    明文密码（密码方式必填）
     * @param code        邮箱验证码（邮箱方式必填）
     * @return 一次性登录票据及有效期
     */
    DirectLoginTicketVO directLogin(String channel, String accountType, String account, String password, String code);

    /**
     * 直连注册（前端直接调用）：持发起会话凭证提交注册信息，创建用户后签发一次性登录票据
     * （注册凭证不经过旧系统后端），旧系统后端凭票据兑换用户信息
     *
     * @param channel 发起会话凭证
     * @param request 注册参数（方式/邮箱/用户名/密码/验证码/昵称）
     * @param ip      注册 IP
     * @return 一次性登录票据及有效期
     */
    DirectLoginTicketVO directRegister(String channel, RegisterRequest request, String ip);

    /**
     * 直连登录-兑换用户信息（旧系统后端调用）：凭一次性票据兑换用户信息，
     * 校验票据归属该 client 且未被消费
     *
     * @param clientId     OAuth 客户端 ID
     * @param clientSecret 客户端密钥
     * @param ticket       一次性登录票据
     * @return 用户信息（uid/昵称/头像/脱敏邮箱/状态）
     */
    ServerLoginVO directUser(String clientId, String clientSecret, String ticket);

    /**
     * 签发会话：生成 token 并写入 Redis（设备数不限，删除 key 即踢下线）
     *
     * @param uid   用户 uid
     * @param clientId 来源客户端 id（可为空）
     * @return 会话 token
     */
    String createSession(Long uid, String clientId);
}
