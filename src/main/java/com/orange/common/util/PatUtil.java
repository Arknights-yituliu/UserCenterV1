package com.orange.common.util;

import java.security.SecureRandom;

/**
 * 个人访问令牌（PAT）字符串工具：生成、展示前缀、格式识别
 *
 * <p>令牌形态固定为 {@code tlc_pat_} + 64 位十六进制（共 72 字符），与旧的自定义令牌
 * （纯 64 位 hex）在字面上即可区分，便于消费端与日志排查时辨认凭证类型。</p>
 *
 * <p><strong>全链路明文（业务取舍）</strong>：PAT 不计算摘要，数据库、Redis 预留键、
 * 列表接口都保存或返回令牌明文本身；迁移进来的旧令牌也原样保留明文。取舍换来的是
 * 「令牌可回看、旧令牌无感迁移、消费端一次精确匹配」，代价是存储与传输链路都成为
 * 敏感数据源：必须配套数据库最小权限、备份加密、TLS，以及日志/异常不上报明文。</p>
 *
 * @author UserCenter
 */
public final class PatUtil {

    /** 令牌统一前缀 */
    public static final String PREFIX = "tlc_pat_";

    /** 随机部分字节数（32 字节 → 64 位十六进制） */
    private static final int RANDOM_BYTES = 32;

    /** 随机部分十六进制长度 */
    private static final int HEX_LENGTH = RANDOM_BYTES * 2;

    /** 完整令牌长度：前缀 + 随机部分 */
    public static final int TOKEN_LENGTH = PREFIX.length() + HEX_LENGTH;

    /** 列表展示用前缀长度 */
    public static final int DISPLAY_PREFIX_LENGTH = 12;

    /** 十六进制字符表（小写，与 {@link SignUtil#sha256} 输出一致） */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PatUtil() {
    }

    /**
     * 生成一枚新的 PAT
     *
     * @return 形如 {@code tlc_pat_} + 64 位 hex 的明文令牌
     */
    public static String generate() {
        byte[] random = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(random);
        StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
        sb.append(PREFIX);
        for (byte b : random) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0F]);
            sb.append(HEX_CHARS[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * 取列表展示前缀（明文前 12 位）
     *
     * @param token 明文令牌
     * @return 展示前缀；入参为空时返回空串
     */
    public static String displayPrefix(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        return token.length() <= DISPLAY_PREFIX_LENGTH ? token : token.substring(0, DISPLAY_PREFIX_LENGTH);
    }

    /**
     * 判断字符串是否符合 PAT 格式
     *
     * <p>用于在日志、入参里区分 PAT 与旧的自定义令牌（纯 64 位 hex，本方法返回 false）。</p>
     *
     * @param token 待判断字符串
     * @return true=形如 tlc_pat_ + 64 位 hex
     */
    public static boolean isPat(String token) {
        return token != null && token.length() == TOKEN_LENGTH
                && token.startsWith(PREFIX) && isHex(token, PREFIX.length());
    }

    /**
     * 判断字符串是否符合旧版自定义令牌格式
     *
     * <p>旧令牌由 {@code SignUtil.generateToken()} 生成，是**不带前缀**的 64 位小写 hex，
     * 与 PAT 前缀规则升级前的存量令牌一致；迁移进 {@code tlc_pat} 的行（{@code source=legacy}）
     * 明文仍是这个形态，因此消费端必须按前缀区分两代令牌。</p>
     *
     * @param token 待判断字符串
     * @return true=纯 64 位小写 hex
     */
    public static boolean isLegacyToken(String token) {
        return token != null && token.length() == HEX_LENGTH && isHex(token, 0);
    }

    /**
     * 判断指定区间是否全为小写十六进制字符
     *
     * @param token 待判断字符串
     * @param from  起始下标（含）
     * @return 区间内全部为 0-9 / a-f 时返回 true
     */
    private static boolean isHex(String token, int from) {
        for (int i = from; i < token.length(); i++) {
            char c = token.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}
