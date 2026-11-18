package com.orange.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PatUtil} 的单元测试：令牌形态、字符表、展示前缀、格式识别
 *
 * @author UserCenter
 */
class PatUtilTest {

    @Test
    void generateProduces72CharPrefixedToken() {
        String token = PatUtil.generate();

        assertThat(token).hasSize(72).startsWith("tlc_pat_");
        assertThat(token.substring(PatUtil.PREFIX.length())).matches("[0-9a-f]{64}");
        assertThat(PatUtil.isPat(token)).isTrue();
    }

    @Test
    void generateIsNotRepeatable() {
        assertThat(PatUtil.generate()).isNotEqualTo(PatUtil.generate());
    }

    @Test
    void generateCoversFullHexAlphabet() {
        // 单枚令牌只有 64 位，逐枚断言会因随机缺字符偶发失败；累计足够样本后断言字符表完整。
        // 字符表若被改坏（重复字符或缺失字符），[0-9a-f]{64} 的正则断言仍会通过，只有本用例能发现。
        Set<Character> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            PatUtil.generate().substring(PatUtil.PREFIX.length())
                    .chars().forEach(c -> seen.add((char) c));
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(
                "0123456789abcdef".chars().mapToObj(c -> (char) c).toList());
    }

    @Test
    void displayPrefixTakesFirst12Chars() {
        String token = PatUtil.generate();

        assertThat(PatUtil.displayPrefix(token)).hasSize(12).isEqualTo(token.substring(0, 12));
        assertThat(PatUtil.displayPrefix("")).isEmpty();
        assertThat(PatUtil.displayPrefix(null)).isEmpty();
    }

    @Test
    void isPatRejectsLegacy64HexToken() {
        String legacy = SignUtil.sha256("legacy-token");

        assertThat(PatUtil.isPat(legacy)).isFalse();
        assertThat(PatUtil.isPat(null)).isFalse();
        assertThat(PatUtil.isPat("tlc_pat_")).isFalse();
        assertThat(PatUtil.isPat("tlc_pat_" + "Z".repeat(64))).isFalse();
    }

    @Test
    void isLegacyTokenMatchesBare64LowerHexOnly() {
        String legacy = "0123456789abcdef".repeat(4);

        assertThat(PatUtil.isLegacyToken(legacy)).isTrue();
        assertThat(PatUtil.isLegacyToken(PatUtil.generate())).isFalse();
        assertThat(PatUtil.isLegacyToken(legacy.substring(1))).isFalse();
        assertThat(PatUtil.isLegacyToken(legacy + "a")).isFalse();
        assertThat(PatUtil.isLegacyToken(legacy.toUpperCase())).isFalse();
        assertThat(PatUtil.isLegacyToken(null)).isFalse();
    }
}
