package com.orange.service;

import com.orange.entity.dto.akoperator.ManualOperatorSaveRequest;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.entity.vo.akoperator.OperatorVO;

import java.util.List;

/**
 * 用户手动修改版干员数据服务：按用户读取与保存一份手动调整后的干员数据
 *
 * <p>归属规则：uid 取自可信登录上下文，客户端即使携带 uid 也不作为归属依据。
 * 一个用户只能有一份手动修改版，与具体游戏账号（akUid）无关。</p>
 *
 * @author UserCenter
 */
public interface AkOperatorManualService {

    /**
     * 读取当前用户的手动修改版干员数据
     *
     * @param uid 用户中心 UID
     * @return 手动版干员列表，无数据时返回空列表
     */
    List<OperatorVO> listManualOperators(Long uid);

    /**
     * 保存当前用户的手动修改版干员数据：有则按属性比较后更新，无则新增，未传入的记录不处理
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（干员数组）
     * @return 新增/更新/未变更条数统计
     */
    OperatorSaveResultVO saveManualOperators(Long uid, ManualOperatorSaveRequest request);
}
