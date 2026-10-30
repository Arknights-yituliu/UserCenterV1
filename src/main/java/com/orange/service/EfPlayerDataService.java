package com.orange.service;

import com.orange.entity.dto.efplayer.EfPlayerDataSaveRequest;
import com.orange.entity.vo.efplayer.EfPlayerDataSaveResultVO;

/**
 * 终末地玩家数据服务
 *
 * <p>归属规则：uid 取自可信登录上下文；玩家数据按 role_id + server_id 只存一份，
 * 由全部绑定用户共享，当前用户首次上传时建立绑定。</p>
 *
 * @author UserCenter
 */
public interface EfPlayerDataService {

    /**
     * 保存终末地玩家数据：整份 JSON 文本直接落库，已存在则覆盖
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（角色标识 + JSON 玩家数据）
     * @return 本次写入的角色标识与保存时间
     */
    EfPlayerDataSaveResultVO savePlayerData(Long uid, EfPlayerDataSaveRequest request);
}
