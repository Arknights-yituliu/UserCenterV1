package com.orange.service;

import com.orange.entity.dto.oauth.MigrateTokenRequest;
import com.orange.entity.vo.oauth.MigrateTokenVO;

/**
 * OAuth 令牌迁移兑换服务（按需兑换 / 懒迁移）
 *
 * <p>供 BackEndV3 在识别出「用户持有旧自签 token」后，凭 uid 现场换取一对全新的
 * UC 令牌。该端点跨公网可达，认证层（Ed25519 验签 + 时间窗 + nonce 防重放）是唯一
 * 准入屏障，IP 白名单、HTTPS、限流仅为加固。</p>
 *
 * @author UserCenter
 */
public interface OAuthMigrateService {

    /**
     * 迁移兑换：依次完成协议、开关、来源、时效、防重放、签名、客户端、用户状态与限流
     * 校验，通过后为一个 uid 现场签发一对全新 UC 令牌。
     *
     * <p>签发会先撤销同一 (uid, clientId) 上上一轮迁移签发的 refresh_token，使该组合上
     * 恒只保留最新一条迁移凭证；不影响该用户正常登录产生的其他授权记录。</p>
     *
     * @param request 迁移请求参数（含接入层补充的来源 IP 与 HTTPS 判定）
     * @return 令牌响应（access_token + refresh_token + scope）
     */
    MigrateTokenVO migrateToken(MigrateTokenRequest request);
}
