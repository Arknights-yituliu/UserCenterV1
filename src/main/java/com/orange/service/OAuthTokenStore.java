package com.orange.service;

/**
 * OAuth 一次性凭证的原子存储操作。
 *
 * <p>普通的单 key 读写仍可由业务服务完成；凡是涉及“先校验旧值，再同时修改多个 key”
 * 的操作统一收口到这里，并由 Redis Lua 保证其他请求不能插入执行。当前 key 未使用
 * Redis Cluster hash tag，因此该实现以单实例 Redis 为前提。</p>
 */
public interface OAuthTokenStore {

    /**
     * 读取授权码原始 JSON。保留原始文本是为了在消费时进行 compare-and-delete，确保
     * Java 校验过的记录与 Lua 最终删除的是同一个版本。
     *
     * @param code 授权码
     * @return 原始 JSON，不存在时返回 null
     */
    String readAuthorizationCode(String code);

    /**
     * 判断授权码是否已经成功消费，用于对外区分“无效/过期”和“已使用”。
     *
     * @param code 授权码
     * @return 是否存在已使用标记
     */
    boolean isAuthorizationCodeUsed(String code);

    /**
     * 在 Lua 中比较并消费授权码，同时写入短期已使用标记。
     *
     * @param code            授权码
     * @param expectedJson    Java 已完成全部安全校验的原始 JSON
     * @param usedTtlSeconds  已使用标记 TTL
     * @return 仅当记录仍存在且内容未变化时返回 true
     */
    boolean consumeAuthorizationCode(String code, String expectedJson, long usedTtlSeconds);

    /**
     * 读取 refresh token 原始 JSON，供业务层完成客户端归属、认证方式和 grant 校验。
     *
     * @param refreshToken refresh token
     * @return 原始 JSON，不存在时返回 null
     */
    String readRefreshToken(String refreshToken);

    /**
     * 原子轮换 refresh token。只有旧记录仍与业务层读取的原始 JSON 完全一致时，才会
     * 删除旧 refresh token、写入新 token 对并同步用户反向索引。
     *
     * @param rotation 已完成序列化的新旧令牌数据
     * @return 是否成功完成轮换；false 表示旧 token 已被其他请求消费
     */
    boolean rotateRefreshToken(RefreshTokenRotation rotation);

    /**
     * refresh token 原子轮换所需的完整参数。
     *
     * <p>使用不可变 record 把参数作为一个整体传递，避免多个字符串/TTL 参数在调用处
     * 发生位置错配。JSON 在进入存储层前已经生成，Lua 只负责原子状态转换。</p>
     */
    record RefreshTokenRotation(
            String oldRefreshToken,
            String expectedOldRefreshJson,
            String newAccessToken,
            String newAccessJson,
            long accessTtlSeconds,
            String newRefreshToken,
            String newRefreshJson,
            long refreshTtlSeconds,
            Long uid) {
    }
}
