package com.orange.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * 签名工具类：SHA-256 摘要、随机数生成
 *
 * @author UserCenter
 */
public final class SignUtil {

    private static final String SHA_256 = "SHA-256";

    private SignUtil() {
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
