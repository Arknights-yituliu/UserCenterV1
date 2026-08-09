package com.orange.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * OAuth 2.0 辅助工具：PKCE S256 计算、令牌随机串生成
 *
 * @author UserCenter
 */
public final class OAuthUtil {

    private OAuthUtil() {
    }

    /**
     * 计算 PKCE 的 S256 code_challenge（RFC 7636）
     *
     * <p>算法：BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))，不带 padding</p>
     *
     * @param codeVerifier 客户端生成的随机串
     * @return code_challenge 值
     */
    public static String pkceS256(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("PKCE S256 计算失败", e);
        }
    }

    /**
     * 生成不可预测的授权码/令牌随机串（复用会话 token 生成算法）
     *
     * @return 随机串
     */
    public static String generateToken() {
        return SignUtil.generateToken();
    }
}
