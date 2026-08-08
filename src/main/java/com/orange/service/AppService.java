package com.orange.service;

import com.orange.entity.vo.app.AppUserVO;

/**
 * 接入方服务接口：Token 校验、用户信息拉取、踢用户下线
 *
 * @author UserCenter
 */
public interface AppService {

    /**
     * 校验用户 token：实时查询 Redis 会话，返回用户全局资料 + 站点扩展资料
     *
     * @param token 用户会话 token
     * @param appId 来源应用 AppId
     * @return 用户信息
     */
    AppUserVO verifyToken(String token, String appId);

    /**
     * 按 uid 拉取用户全局资料 + 指定站点扩展资料
     *
     * @param uid   用户 uid
     * @param appId 来源应用 AppId
     * @return 用户信息
     */
    AppUserVO getUserInfo(Long uid, String appId);

    /**
     * 踢指定用户全部设备下线（删除该用户所有会话）
     *
     * @param uid 用户 uid
     */
    void kickUser(Long uid);
}
