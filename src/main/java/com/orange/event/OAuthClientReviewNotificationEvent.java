package com.orange.event;

/**
 * OAuth 客户端注册或更新后的管理员审核通知事件。
 *
 * @param action     操作类型
 * @param clientId   OAuth 客户端 ID
 * @param clientName 客户端名称
 * @param ownerUid   所有者用户 UID
 * @param origin     客户端登记的 Origin，可为空
 * @author UserCenter
 */
public record OAuthClientReviewNotificationEvent(
        String action,
        String clientId,
        String clientName,
        Long ownerUid,
        String origin) {
}
