package com.orange.service;

import com.orange.entity.dto.oauthclient.OAuthClientRegisterRequest;
import com.orange.entity.dto.oauthclient.OAuthClientUpdateRequest;
import com.orange.entity.vo.oauth.OAuthClientCredentialVO;
import com.orange.entity.vo.oauth.OAuthClientVO;

import java.util.List;

/**
 * OAuth 客户端自助管理服务：开发者维护自己名下客户端的注册、查询、更新、密钥轮换、停用、删除
 *
 * @author UserCenter
 */
public interface OAuthClientAdminService {

    /**
     * 注册客户端（client_id / client_secret 由系统生成，secret 明文仅此一次返回）
     *
     * @param uid     开发者用户 uid
     * @param request 注册参数
     * @return 客户端凭证（含明文 secret，仅此一次）
     */
    OAuthClientCredentialVO register(Long uid, OAuthClientRegisterRequest request);

    /**
     * 查询当前开发者名下全部客户端
     *
     * @param uid 开发者用户 uid
     * @return 客户端列表（不返回 secret）
     */
    List<OAuthClientVO> listClients(Long uid);

    /**
     * 查询单个客户端详情（校验归属）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @return 客户端详情（不返回 secret）
     */
    OAuthClientVO getClient(Long uid, String clientId);

    /**
     * 更新客户端（校验归属，client_id/authMethod/grantTypes 不可改）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @param request  更新参数
     */
    void updateClient(Long uid, String clientId, OAuthClientUpdateRequest request);

    /**
     * 轮换客户端密钥（旧 secret 立即失效，新明文仅此一次返回）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @return 客户端凭证（含新明文 secret，仅此一次）
     */
    OAuthClientCredentialVO rotateSecret(Long uid, String clientId);

    /**
     * 停用/启用客户端（停用后授权/换 token 返回 90001）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @param enabled  true=启用 false=停用
     */
    void setClientStatus(Long uid, String clientId, boolean enabled);

    /**
     * 删除客户端（级联吊销其名下全部令牌，不可恢复）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     */
    void deleteClient(Long uid, String clientId);
}
