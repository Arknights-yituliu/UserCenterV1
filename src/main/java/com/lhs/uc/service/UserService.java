package com.lhs.uc.service;

import com.lhs.uc.entity.dto.user.UpdatePasswordRequest;
import com.lhs.uc.entity.dto.user.UpdateProfileRequest;
import com.lhs.uc.entity.vo.SessionVO;
import com.lhs.uc.entity.vo.UserInfoVO;

import java.util.List;

/**
 * 用户服务接口：个人资料、修改密码、会话管理
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
     * 踢指定设备下线（校验会话归属当前用户）
     *
     * @param uid   用户 uid
     * @param token 要踢出的会话 token
     */
    void kickSession(Long uid, String token);
}
