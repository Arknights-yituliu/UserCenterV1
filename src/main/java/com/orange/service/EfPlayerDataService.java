package com.orange.service;

import com.orange.entity.dto.efplayer.EfPlayerDataSaveRequest;
import com.orange.entity.vo.efplayer.EfAccountVO;
import com.orange.entity.vo.efplayer.EfPlayerDataSaveResultVO;
import com.orange.entity.vo.efplayer.EfPlayerDataVO;

import java.util.List;

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
     * 查询当前用户已绑定的终末地角色列表，按最近导入时间倒序
     *
     * @param uid 用户中心 UID
     * @return 已绑定角色列表（不含玩家数据正文），无绑定关系时返回空列表
     */
    List<EfAccountVO> listBoundAccounts(Long uid);

    /**
     * 读取指定角色的玩家数据：先校验当前用户对该角色的绑定关系，再按角色定位数据
     *
     * @param uid      用户中心 UID
     * @param roleId   终末地游戏内角色 UID
     * @param serverId 服务器 ID，可省略（按空串处理）
     * @return 玩家数据；绑定存在但数据行缺失时返回 null
     */
    EfPlayerDataVO getPlayerData(Long uid, String roleId, String serverId);

    /**
     * 保存终末地玩家数据：整份 JSON 文本直接落库，已存在则覆盖
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（角色标识 + JSON 玩家数据）
     * @return 本次写入的角色标识与保存时间
     */
    EfPlayerDataSaveResultVO savePlayerData(Long uid, EfPlayerDataSaveRequest request);
}
