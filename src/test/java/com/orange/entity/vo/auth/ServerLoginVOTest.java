package com.orange.entity.vo.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** OAuth 直连登录响应脱敏测试。 */
class ServerLoginVOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializedDirectLoginUserNeverContainsEmail() {
        ServerLoginVO result = new ServerLoginVO();
        result.setUid(7L);

        assertFalse(objectMapper.valueToTree(result).has("email"));
    }
}
