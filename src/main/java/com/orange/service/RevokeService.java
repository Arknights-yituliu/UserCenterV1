package com.orange.service;

/**
 * 吊销编排服务：按用户维度统一执行本地吊销
 *
 * <p>收拢「踢出登录会话」与「吊销 OAuth 令牌」的 Redis 操作，作为按 uid 吊销的唯一实现点，
 * 供 revoke-user 等按 uid 吊销的入口复用，后续级联吊销也在此扩展</p>
 *
 * @author UserCenter
 */
public interface RevokeService {

    /**
     * 吊销指定用户在本系统内的全部凭证：先踢登录会话，再吊销 OAuth 令牌
     *
     * @param uid 用户 uid
     */
    void revokeUser(Long uid);

    /**
     * 踢出用户全部登录会话（基于 uid 反向索引直达，删除后清理索引）
     *
     * @param uid 用户 uid
     */
    void kickAllSessions(Long uid);

    /**
     * 吊销用户名下全部 OAuth 令牌（access_token / refresh_token 一并吊销，
     * refresh_token 连带吊销其派生出的 access_token），并清理 uid 反向索引
     *
     * @param uid 用户 uid
     */
    void revokeUserTokens(Long uid);
}
