package com.orange.common.util;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * 签名工具类：SHA-256 摘要、随机数生成、Ed25519 验签
 *
 * @author UserCenter
 */
public final class SignUtil {

    private static final String SHA_256 = "SHA-256";

    /** Ed25519 签名算法名（JDK 15+ 内置，无需第三方库） */
    private static final String ED25519 = "Ed25519";

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
     * Ed25519 验签：校验原文与签名是否匹配给定公钥
     *
     * <p>公钥为 Base64 编码的 X.509 SubjectPublicKeyInfo（Java、OpenSSL 的默认导出格式）。
     * 验签由 JDK 以恒定时间方式完成，调用方无需再做签名串比对。解析或验签过程中的
     * 任何异常都返回 false，由调用方统一按签名校验失败处理，避免异常细节外泄。</p>
     *
     * @param publicKeyBase64 公钥（Base64 的 X.509 SubjectPublicKeyInfo）
     * @param data            待核验原文
     * @param signatureBase64 签名（Base64）
     * @return true=验签通过
     */
    public static boolean ed25519Verify(String publicKeyBase64, String data, String signatureBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()
                || data == null || signatureBase64 == null || signatureBase64.isBlank()) {
            return false;
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance(ED25519)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            Signature verifier = Signature.getInstance(ED25519);
            verifier.initVerify(publicKey);
            verifier.update(data.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
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
