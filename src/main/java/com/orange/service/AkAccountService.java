package com.orange.service;

import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.ItemListVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.entity.vo.akoperator.SkinListVO;

import java.util.List;

/**
 * 游戏账号与干员数据服务：绑定关系查询、干员/物品全量读取、干员批量保存、游戏账号解绑
 *
 * <p>归属规则：uid 取自可信登录上下文，客户端即使携带 uid 也不作为归属依据；
 * 绑定关系按用户维度维护，干员与物品数据按 ak_uid 只存一份并由全部绑定用户共享。</p>
 *
 * @author UserCenter
 */
public interface AkAccountService {

    /**
     * 查询当前用户已绑定的游戏账号列表（不含干员正文）
     *
     * <p>用户设置的默认来源排在首位，其余按最近变更时间倒序。</p>
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
     * {@code akUid} 是否等于当前用户中心 UID 自动识别，手动条目未命中再回落到自动数据。
     * {@code akUid} 为空时先取用户设置的默认来源，未设置或已失效则回退到最近变更的条目。</p>
     *
     * @param uid    用户中心 UID
     * @param akUid  数据条目 UID：自动数据为游戏账号 UID，手动数据复用用户中心 UID，可为空
     * @param source 数据来源，可选：manual 手动版，skland 森空岛自动导入，为空时自动识别
     * @return 对应来源的全部干员数据
     */
    OperatorListVO listOperators(Long uid, String akUid, String source);

    /**
     * 设置当前用户的默认数据来源
     *
     * <p>每个用户只保留一条默认偏好：manual 来源要求 {@code akUid} 为本人用户中心 UID
     * 且已存在手动修改版数据；skland 来源要求 {@code akUid} 为本人已绑定的游戏账号。</p>
     *
     * @param uid    用户中心 UID
     * @param akUid  数据条目 UID
     * @param source 数据来源：manual 或 skland
     */
    void setDefaultSource(Long uid, String akUid, String source);

    /**
     * 解除当前用户与森空岛游戏账号的绑定
     *
     * <p>只删除绑定关系与指向该账号的默认偏好。角色信息、干员数据与物品存量按 ak_uid
     * 共享，不随解绑删除，其他绑定用户与后续重新导入均不受影响。</p>
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     */
    void unbindAccount(Long uid, String akUid);

    /**
     * 全量读取游戏账号物品存量，读取前必须通过绑定关系鉴权
     *
     * <p>物品为自动导入侧的全量快照，返回该账号最近一次携带 items 上传的完整数据；
     * 该账号从未上报过物品时 items 为空数组、updateTime 为 null。</p>
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     * @return 物品全量数据与最近上传时间
     */
    ItemListVO listItems(Long uid, String akUid);

    /**
     * 全量读取游戏账号已拥有皮肤，读取前必须通过绑定关系鉴权
     *
     * <p>皮肤只有自动导入侧的全量集合，手动条目没有皮肤数据；该账号从未上报过皮肤时
     * items 为空数组、updateTime 为 null。返回项只含皮肤编码与获得时间，名称与立绘由调用方
     * 关联游戏静态数据补全。</p>
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     * @return 皮肤全量数据与最近变更时间
     */
    SkinListVO listSkins(Long uid, String akUid);

    /**
     * 批量保存某游戏账号的角色信息、干员数据、物品存量与皮肤数据：有则按属性比较后更新，无则新增，未传入的记录不处理
     *
     * <p>皮肤语义与其他数据不同：携带 {@code skins} 时按本次提交全量对齐，未提交的旧皮肤会被删除；
     * 省略时不改动已有皮肤数据。</p>
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（角色信息 + 干员数组 + 可选物品数组 + 可选皮肤数组），目标游戏账号取自 request.playerInfo.akUid
     * @return 新增/更新/未变更条数统计（仅统计干员）
     */
    OperatorSaveResultVO saveOperators(Long uid, OperatorSaveRequest request);
}
