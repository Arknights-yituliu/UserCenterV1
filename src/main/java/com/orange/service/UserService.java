package com.orange.service;

import com.orange.entity.dto.user.BindEmailRequest;
import com.orange.entity.dto.user.ChangeEmailRequest;
import com.orange.entity.dto.user.UpdatePasswordRequest;
import com.orange.entity.dto.user.UpdateProfileRequest;
import com.orange.entity.vo.SessionVO;
import com.orange.entity.vo.UserInfoVO;

import java.util.List;

/**
 * 用户服务接口：个人资料、修改密码、邮箱绑定、会话管理
 *
 * @author UserCenter
 */
public interface UserService {

    /**
     * 获取当前用户资料
     *
     * @param uid 用户 uid
     * @return 用户信息
     */
    UserInfoVO getProfile(Long uid);

    /**
     * 修改个人资料（昵称/头像）
     *
     * @param uid     用户 uid
     * @param request 修改参数
     */
    void updateProfile(Long uid, UpdateProfileRequest request);

    /**
     * 修改密码（校验旧密码）
     *
     * @param uid     用户 uid
     * @param request 修改参数
     */
    void updatePassword(Long uid, UpdatePasswordRequest request);

    /**
     * 查看当前用户的全部登录设备
     *
     * @param uid 用户 uid
     * @return 会话列表
     */
    List<SessionVO> listSessions(Long uid);

    /**
     * 绑定邮箱（面向无邮箱用户，需验证新邮箱所有权）
     *
     * @param uid     用户 uid
     * @param request 绑定参数（新邮箱 + 验证码）
     */
    void bindEmail(Long uid, BindEmailRequest request);

    /**
     * 发送换绑邮箱验证码（发到当前绑定邮箱，前端无需回传邮箱）
     *
     * @param uid 用户 uid
     * @param ip  请求 IP（发送限流维度）
     */
    void sendChangeEmailCode(Long uid, String ip);

    /**
     * 换绑邮箱（旧邮箱以服务端当前绑定为准，仅校验旧/新邮箱验证码）
     *
     * @param uid     用户 uid
     * @param request 换绑参数（旧邮箱验证码 + 新邮箱 + 新邮箱验证码）
     */
    void changeEmail(Long uid, ChangeEmailRequest request);

    /**
     * 踢出用户全部会话（修改密码/重设密码后调用）
     *
     * @param uid 用户 uid
     */
    void kickAllSessions(Long uid);

    /**
     * 踢指定设备下线（校验会话归属当前用户）
     *
     * @param uid   用户 uid
     * @param token 要踢出的会话 token
     */
    void kickSession(Long uid, String token);
}
