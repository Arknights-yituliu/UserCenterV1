package com.orange.service;

/**
 * OAuth 一次性凭证的原子存储操作。
 *
 * <p>普通的单 key 读写仍可由业务服务完成；凡是涉及“先校验旧值，再同时修改多个 key”
 * 的操作统一收口到这里，并由 Redis Lua 保证其他请求不能插入执行。当前 key 未使用
 * Redis Cluster hash tag，因此该实现以单实例 Redis 为前提。</p>
 */
public interface OAuthTokenStore {

    /** {@link #rotateRefresh} 返回值：轮转成功（旧凭证失效，新凭证生效） */
    long ROTATE_SUCCESS = 1L;

    /** {@link #rotateRefresh} 返回值：旧凭证与业务层读取的快照不一致（已被轮转/吊销/不存在），本次未做任何写入 */
    long ROTATE_STALE = 0L;

    /** {@link #rotateRefresh} 返回值：预校验失败，本次未做任何写入 */
    long ROTATE_REJECTED = -1L;

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
     * 原子签发 access token。refresh token 为固定凭证：有效期内可反复刷新，脚本只校验
     * 旧 refresh 记录仍与业务层读取的原始 JSON 完全一致（未被吊销/替换），然后写入新
     * access token 并同步用户反向索引，不删除也不换发 refresh token。
     *
     * @param request 已序列化的刷新与签发参数
     * @return 是否成功签发；false 表示 refresh 已失效或被其他流程吊销
     */
    boolean issueAccessFromRefresh(RefreshAccessRequest request);

    /**
     * 原子轮转 refresh token。在 {@link #issueAccessFromRefresh} 的基础上多做三件事：
     * 删除旧 refresh 记录并写入重放检测墓碑与宽限期幂等副本、写入换发出的新 refresh 记录、
     * 把新 access 与新 refresh 登记进令牌家族索引。新 refresh 继承旧记录的剩余有效期，
     * 因此轮转不会延长授权。
     *
     * <p>旧记录仍与业务层读取的原始 JSON 完全一致时才会执行；不一致（已被其他请求轮转、
     * 被吊销或已过期）时不做任何写入，由业务层进一步判断是并发落败还是凭证重放。</p>
     *
     * @param request 已序列化的轮转参数
     * @return {@link #ROTATE_SUCCESS} / {@link #ROTATE_STALE} / {@link #ROTATE_REJECTED}
     */
    long rotateRefresh(RotateRefreshRequest request);

    /**
     * 原子签发迁移凭证。一次调用完成三件事：撤销上一轮迁移签发的 refresh_token
     * （Redis 记录与反向索引成员）、写入新 access/refresh 令牌记录与索引成员、
     * 更新“当前迁移凭证”映射，使同一 (uid, clientId) 上恒只保留最新一条迁移凭证。
     *
     * <p>只替换迁移专用的那一条凭证：映射不存在时不触碰该用户通过
     * {@code /oauth2/direct-user} 正常登录产生的授权记录。</p>
     *
     * @param request 已序列化的迁移签发参数
     * @return 签发结果；success=false 表示预校验未通过，此时未做任何写入
     */
    MigrateIssueResult issueMigratedToken(MigrateIssueRequest request);

    /**
     * refresh token 换取 access token 所需的完整参数。
     *
     * <p>使用不可变 record 把参数作为一个整体传递，避免多个字符串/TTL 参数在调用处
     * 发生位置错配。JSON 在进入存储层前已经生成，Lua 只负责原子状态转换。</p>
     */
    record RefreshAccessRequest(
            String oldRefreshToken,
            String expectedOldRefreshJson,
            String newAccessToken,
            String newAccessJson,
            long accessTtlSeconds,
            Long uid) {
    }

    /**
     * refresh token 轮转所需的完整参数。
     *
     * <p>与 {@link RefreshAccessRequest} 的差异：额外携带换发出的新 refresh_token、
     * 令牌家族标识、墓碑内容与宽限期。所有 JSON 均由业务层预先生成，Lua 只负责原子状态转换。</p>
     *
     * @param oldRefreshToken         被轮转的旧 refresh_token 明文
     * @param expectedOldRefreshJson  业务层已完成校验的旧 refresh 原始 JSON（快照）
     * @param newAccessToken          新 access_token 明文
     * @param newAccessJson           新 access 记录 JSON
     * @param accessTtlSeconds        新 access 有效期（秒）
     * @param newRefreshToken         新 refresh_token 明文
     * @param newRefreshJson          新 refresh 记录 JSON（含家族标识）
     * @param refreshTtlSeconds       refresh TTL 兜底值（秒）：旧记录无 TTL 时使用
     * @param uid                     用户 uid
     * @param familyId                令牌家族标识
     * @param tombstoneJson           轮转墓碑 JSON（家族标识/客户端/uid/轮转时刻）
     * @param graceTtlSeconds         宽限期秒数，即幂等副本 TTL
     */
    record RotateRefreshRequest(
            String oldRefreshToken,
            String expectedOldRefreshJson,
            String newAccessToken,
            String newAccessJson,
            long accessTtlSeconds,
            String newRefreshToken,
            String newRefreshJson,
            long refreshTtlSeconds,
            Long uid,
            String familyId,
            String tombstoneJson,
            long graceTtlSeconds) {
    }

    /**
     * 迁移凭证签发参数。
     *
     * <p>JSON 在进入存储层前已经生成，Lua 只负责原子状态转换；映射 key 由
     * clientId + uid 在存储层内部拼装，调用方无需关心 key 结构。</p>
     */
    record MigrateIssueRequest(
            String clientId,
            Long uid,
            String newAccessToken,
            String newAccessJson,
            long accessTtlSeconds,
            String newRefreshToken,
            String newRefreshJson,
            long refreshTtlSeconds) {
    }

    /**
     * 迁移凭证签发结果。
     *
     * @param success             是否签发成功
     * @param revokedRefreshToken 本次被替换撤销的上一轮迁移 refresh_token；
     *                            首次兑换（映射不存在）时为 null
     */
    record MigrateIssueResult(boolean success, String revokedRefreshToken) {
    }
}
