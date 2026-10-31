package com.orange.service;

import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;

import java.util.List;

/**
 * 游戏账号与干员数据服务：绑定关系查询、干员全量读取、干员批量保存
 *
 * <p>归属规则：uid 取自可信登录上下文，客户端即使携带 uid 也不作为归属依据；
 * 绑定关系按用户维度维护，干员数据按 ak_uid 只存一份并由全部绑定用户共享。</p>
 *
 * @author UserCenter
 */
public interface AkAccountService {

    /**
     * 查询当前用户已绑定的游戏账号列表（不含干员正文）
     *
     * @param uid 用户中心 UID
     * @return 已绑定游戏账号列表
     */
    List<AkAccountVO> listBoundAccounts(Long uid);

    /**
     * 全量读取干员数据，读取前必须通过绑定关系鉴权
     *
     * <p>数据来源由 {@code source} 显式指定：{@code manual} 读本人手动修改版，
     * {@code skland} 读森空岛自动导入数据；{@code source} 为空时按
     * {@code akUid} 是否等于当前用户中心 UID 自动识别，手动条目未命中再回落到自动数据。</p>
     *
     * @param uid    用户中心 UID
     * @param akUid  数据条目 UID：自动数据为游戏账号 UID，手动数据复用用户中心 UID
     * @param source 数据来源，可选：manual 手动版，skland 森空岛自动导入，为空时自动识别
     * @return 对应来源的全部干员数据
     */
    OperatorListVO listOperators(Long uid, String akUid, String source);

    /**
     * 批量保存某游戏账号的角色信息与干员数据：有则按属性比较后更新，无则新增，未传入的记录不处理
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（角色信息 + 干员数组），目标游戏账号取自 request.playerInfo.akUid
     * @return 新增/更新/未变更条数统计
     */
    OperatorSaveResultVO saveOperators(Long uid, OperatorSaveRequest request);
}
