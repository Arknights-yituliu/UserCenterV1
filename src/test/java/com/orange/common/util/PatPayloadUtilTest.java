package com.orange.common.util;

import com.orange.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PatPayloadUtil} 的单元测试：ak_uid 取值口径
 *
 * <p>重点是「绝不返回空串」——业务层把空的 akUid 解释为读默认来源，放行等于换账号越权。</p>
 *
 * @author UserCenter
 */
class PatPayloadUtilTest {

    @Test
    void requireAkUidReadsStringAndTrims() {
        assertThat(PatPayloadUtil.requireAkUid(Map.of("ak_uid", " 123456789 "))).isEqualTo("123456789");
    }

    @Test
    void requireAkUidAcceptsIntegralNumber() {
        assertThat(PatPayloadUtil.requireAkUid(Map.of("ak_uid", 123456789L))).isEqualTo("123456789");
        assertThat(PatPayloadUtil.requireAkUid(Map.of("ak_uid", 42))).isEqualTo("42");
    }

    @Test
    void requireAkUidRejectsMissingKeyOrNullPayload() {
        assertThatThrownBy(() -> PatPayloadUtil.requireAkUid(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置 ak_uid");
        assertThatThrownBy(() -> PatPayloadUtil.requireAkUid(Map.of("other", "1")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置 ak_uid");
    }

    @Test
    void requireAkUidRejectsBlankString() {
        assertThatThrownBy(() -> PatPayloadUtil.requireAkUid(Map.of("ak_uid", "   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void requireAkUidRejectsNonScalarTypes() {
        assertThatThrownBy(() -> PatPayloadUtil.requireAkUid(Map.of("ak_uid", true)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PatPayloadUtil.requireAkUid(Map.of("ak_uid", 1.5)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> PatPayloadUtil.requireAkUid(Map.of("ak_uid", Map.of("v", 1))))
                .isInstanceOf(BusinessException.class);
    }
}
