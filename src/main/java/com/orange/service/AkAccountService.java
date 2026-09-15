package com.orange.service;

import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;

import java.util.List;

/**
 * 游戏账号与干员数据服务：绑定关系查询、干员全量读取、干员批量保存
 *
 * <p>归属规则：uid 取自登录上下文，client_id 取自登录上下文，客户端即使携带 uid 也不作为归属依据；
 * 绑定关系为多对多，干员数据按 ak_uid 只存一份并由全部绑定用户共享。</p>
 *
 * @author UserCenter
 */
public interface AkAccountService {

    /**
     * 查询当前用户在当前客户端下已绑定的游戏账号列表（不含干员正文）
     *
     * @param uid 用户中心 UID
     * @return 已绑定游戏账号列表
     */
    List<AkAccountVO> listBoundAccounts(Long uid);

    /**
     * 全量读取某游戏账号的干员数据，读取前必须通过绑定关系鉴权
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     * @return 该账号全部干员数据
     */
    OperatorListVO listOperators(Long uid, String akUid);

    /**
     * 批量保存某游戏账号的角色信息与干员数据：有则按属性比较后更新，无则新增，未传入的记录不处理
     *
     * @param uid     用户中心 UID
     * @param akUid   游戏账号 UID
     * @param request 保存参数（角色信息 + 干员数组）
     * @return 新增/更新/未变更条数统计
     */
    OperatorSaveResultVO saveOperators(Long uid, String akUid, OperatorSaveRequest request);
}
