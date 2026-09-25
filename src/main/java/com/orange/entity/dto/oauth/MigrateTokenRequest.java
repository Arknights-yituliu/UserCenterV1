package com.orange.entity.dto.oauth;

/**
 * 迁移兑换请求参数（BackEndV3 → UC 的服务端间调用）
 *
 * <p>表单原始值统一以字符串承载，由服务层负责格式校验与错误码转换，避免参数解析
 * 异常绕过统一的错误码体系（否则非法 uid 会得到框架默认的 400 而非 10001）。</p>
 *
 * @param clientId        目标 OAuth 客户端 ID
 * @param uidText         uid 字符串
 * @param kid             公钥标识（对应 UC 侧 kid → 公钥映射表，用于轮换）
 * @param tsText          请求时间戳（Unix 秒）
 * @param nonce           一次性随机串
 * @param sig             Ed25519 签名（Base64）
 * @param origin          来源标识（可选，仅审计）
 * @param legacyTokenHash 旧 token 的 sha256 十六进制（可选，仅审计，不参与鉴权）
 * @param requestIp       请求来源 IP
 * @param httpsRequest    请求是否经 HTTPS 到达（含反向代理的 X-Forwarded-Proto 判定）
 *
 * @author UserCenter
 */
public record MigrateTokenRequest(
        String clientId,
        String uidText,
        String kid,
        String tsText,
        String nonce,
        String sig,
        String origin,
        String legacyTokenHash,
        String requestIp,
        boolean httpsRequest) {
}
