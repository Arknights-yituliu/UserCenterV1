package com.lhs.uc.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 签名工具类：HMAC-SHA256 签名计算、SHA-256 摘要、随机数生成
 *
 * @author UserCenter
 */
public final class SignUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String SHA_256 = "SHA-256";

    private SignUtil() {
    }

    /**
     * 计算 HMAC-SHA256 签名，输出小写十六进制字符串
     *
     * <p>签名串拼接规则（见设计方案 6.1）：appId + timestamp + nonce + 请求体规范串</p>
     *
     * @param secret     应用密钥
     * @param appId      应用 ID
     * @param timestamp  毫秒时间戳
     * @param nonce      一次性随机数
     * @param bodyString 请求体规范串（可为空）
     * @return 签名（64 位小写十六进制）
     */
    public static String hmacSha256(String secret, String appId, String timestamp, String nonce, String bodyString) {
        String content = appId + timestamp + nonce + (bodyString == null ? "" : bodyString);
        return hmacSha256(secret, content);
    }

    /**
     * 计算 HMAC-SHA256 签名，输出小写十六进制字符串
     *
     * @param secret 密钥
     * @param data   待签名内容
     * @return 签名（64 位小写十六进制）
     */
    public static String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 签名计算失败", e);
        }
    }

    /**
     * 计算 SHA-256 摘要，输出小写十六进制字符串
     *
     * @param data 原文
     * @return 摘要
     */
    public static String sha256(String data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] raw = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 摘要计算失败", e);
        }
    }

    /**
     * 生成接入方一次性随机数 nonce：UUID 去横杠 + 毫秒时间戳
     *
     * @return nonce 字符串
     */
    public static String generateNonce() {
        return UUID.randomUUID().toString().replace("-", "") + System.currentTimeMillis();
    }

    /**
     * 生成用户会话 token：SHA-256(随机UUID + 时间戳)，不可逆、不可预测
     *
     * @return token 字符串
     */
    public static String generateToken() {
        return sha256(UUID.randomUUID().toString().replace("-", "") + System.nanoTime());
    }

    /**
     * 字节数组转小写十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }
}
