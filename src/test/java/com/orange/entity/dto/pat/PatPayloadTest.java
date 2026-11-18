package com.orange.entity.dto.pat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PatPayload} 的反序列化容错测试
 *
 * <p>payload 在 DB 里是 TEXT，读取时才转成实体，因此必须能同时吃下未定义键、未来版本
 * 新增的键、以及字段缺失或全空的 JSON；任何形态都不应让接口报错。</p>
 *
 * @author UserCenter
 */
class PatPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void emptyObjectLeavesAllFieldsNull() throws Exception {
        PatPayload payload = objectMapper.readValue("{}", PatPayload.class);

        assertThat(payload).isNotNull();
        assertThat(payload.getGameUid()).isNull();
        assertThat(payload.getGameName()).isNull();
        assertThat(payload.isEmpty()).isTrue();
    }

    @Test
    void missingFieldsStayNullWhenOthersArePresent() throws Exception {
        PatPayload payload = objectMapper.readValue("{\"game_uid\":\"123456789\"}", PatPayload.class);

        assertThat(payload.getGameUid()).isEqualTo("123456789");
        assertThat(payload.getGameName()).isNull();
        assertThat(payload.isEmpty()).isFalse();
    }

    @Test
    void gameNameAloneIsAValidPartialPayload() throws Exception {
        PatPayload payload = objectMapper.readValue("{\"game_name\":\"明日方舟\"}", PatPayload.class);

        assertThat(payload.getGameUid()).isNull();
        assertThat(payload.getGameName()).isEqualTo("明日方舟");
        assertThat(payload.isEmpty()).isFalse();
    }

    @Test
    void unknownFieldsAreIgnoredAndOnlyDefinedFieldsAreSerialized() throws Exception {
        PatPayload payload = objectMapper.readValue(
                "{\"game_uid\":\"123456789\",\"game_name\":\"明日方舟\",\"legacy_blob\":{\"a\":1},"
                        + "\"future_flag\":true,\"numbers\":[1,2,3]}", PatPayload.class);

        assertThat(payload.getGameUid()).isEqualTo("123456789");
        assertThat(payload.getGameName()).isEqualTo("明日方舟");
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(payload));
        assertThat(json.size()).isEqualTo(2);
        assertThat(json.get("game_uid").asText()).isEqualTo("123456789");
        assertThat(json.get("game_name").asText()).isEqualTo("明日方舟");
    }

    @Test
    void allUnknownFieldsBecomeAnEmptyEntity() throws Exception {
        PatPayload payload = objectMapper.readValue(
                "{\"keep\":\"me\",\"a\":1}", PatPayload.class);

        assertThat(payload.isEmpty()).isTrue();
    }

    @Test
    void integralGameUidIsCoercedToString() throws Exception {
        PatPayload payload = objectMapper.readValue("{\"game_uid\":123456789}", PatPayload.class);

        assertThat(payload.getGameUid()).isEqualTo("123456789");
    }

    @Test
    void camelCaseAliasesAreAccepted() throws Exception {
        PatPayload payload = objectMapper.readValue(
                "{\"gameUid\":\"123456789\",\"gameName\":\"明日方舟\"}", PatPayload.class);

        assertThat(payload.getGameUid()).isEqualTo("123456789");
        assertThat(payload.getGameName()).isEqualTo("明日方舟");
    }

    @Test
    void nullFieldsAreTreatedAsAbsent() throws Exception {
        PatPayload payload = objectMapper.readValue(
                "{\"game_uid\":null,\"game_name\":null}", PatPayload.class);

        assertThat(payload.isEmpty()).isTrue();
    }

    @Test
    void sourceAloneIsAValidPartialPayload() throws Exception {
        PatPayload payload = objectMapper.readValue("{\"source\":\"mini_app\"}", PatPayload.class);

        assertThat(payload.getSource()).isEqualTo("mini_app");
        assertThat(payload.getGameUid()).isNull();
        // 只填 source 也算有效 payload，不能被当空对象归一化成 DB 的 NULL
        assertThat(payload.isEmpty()).isFalse();
    }

    @Test
    void sourceIsSerializedWithSnakeCaseKeyAndNoAlias() throws Exception {
        PatPayload payload = new PatPayload();
        payload.setSource("web");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(payload));
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.get("source").asText()).isEqualTo("web");
    }

    @Test
    void emptyEntitySerializesToEmptyObject() throws Exception {
        assertThat(objectMapper.writeValueAsString(new PatPayload())).isEqualTo("{}");
    }
}
