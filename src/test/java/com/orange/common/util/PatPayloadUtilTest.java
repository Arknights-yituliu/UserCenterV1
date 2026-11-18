package com.orange.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.pat.PatPayload;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PatPayloadUtil} 的单元测试：game_uid 取值口径
 *
 * <p>重点是「绝不返回空串」——业务层把空的 akUid 解释为读默认来源，放行等于换账号越权。</p>
 *
 * @author UserCenter
 */
class PatPayloadUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requireGameUidReadsStringAndTrims() {
        assertThat(PatPayloadUtil.requireGameUid(payload(" 123456789 "))).isEqualTo("123456789");
    }

    @Test
    void requireGameUidRejectsMissingFieldOrNullPayload() {
        assertThatThrownBy(() -> PatPayloadUtil.requireGameUid(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置 game_uid");
        assertThatThrownBy(() -> PatPayloadUtil.requireGameUid(new PatPayload()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置 game_uid");
    }

    @Test
    void gameNameAloneDoesNotSatisfyGameUid() {
        PatPayload payload = new PatPayload();
        payload.setGameName("明日方舟");

        assertThatThrownBy(() -> PatPayloadUtil.requireGameUid(payload))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置 game_uid");
    }

    @Test
    void requireGameUidRejectsBlankString() {
        assertThatThrownBy(() -> PatPayloadUtil.requireGameUid(payload("   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void integralJsonNumberBecomesDecimalString() throws Exception {
        PatPayload payload = objectMapper.readValue("{\"game_uid\":123456789}", PatPayload.class);

        assertThat(payload.getGameUid()).isEqualTo("123456789");
        assertThat(PatPayloadUtil.requireGameUid(payload)).isEqualTo("123456789");
    }

    @Test
    void scalarCoercionIsDocumented() throws Exception {
        // 字段声明为 String 后，Jackson 会把布尔/浮点标量转成字符串，取值层不再以 10002 拒绝；
        // 这类值最终会被业务层的 ak_account_binding 绑定校验挡下（80008），不会读到他人数据。
        PatPayload bool = objectMapper.readValue("{\"game_uid\":true}", PatPayload.class);
        PatPayload decimal = objectMapper.readValue("{\"game_uid\":1.5}", PatPayload.class);

        assertThat(PatPayloadUtil.requireGameUid(bool)).isEqualTo("true");
        assertThat(PatPayloadUtil.requireGameUid(decimal)).isEqualTo("1.5");
    }

    @Test
    void objectValueIsRejectedAtBinding() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"game_uid\":{\"v\":1}}", PatPayload.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    private PatPayload payload(String gameUid) {
        PatPayload payload = new PatPayload();
        payload.setGameUid(gameUid);
        return payload;
    }
}
