package com.orange.service;

/** 注销一图流账户：先保存资料审计，仅删除用户主记录、游戏绑定与 Redis 登录会话。 */
public interface AccountDeletionService {

    /** uid 必须来自已认证的用户会话。 */
    void deleteAccount(Long uid, String code);

    /** 仅从有效 refresh_token 解析注销主体，不接受调用方指定 uid。 */
    void deleteByRefreshToken(String refreshToken, String code);

    /** 发往当前会话用户的绑定邮箱，不接受调用方指定收件地址。 */
    void sendDeletionCode(Long uid, String ip);

    /** 发往 refresh_token 所属用户的绑定邮箱。 */
    void sendDeletionCodeByRefreshToken(String refreshToken, String ip);
}
