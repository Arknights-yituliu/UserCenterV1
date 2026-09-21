package com.orange.entity.vo.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.entity.po.UserInfo;
import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** OAuth 用户信息脱敏测试。 */
class UserInfoVOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializedUserInfoNeverContainsEmail() {
        UserInfo user = new UserInfo();
        user.setEmail("user@example.com");
        user.setUserName("orange-user");

        UserInfoVO result = UserInfoVO.of(new OAuthTokenPrincipal(7L, "client-1", "user.read"), user);

        assertFalse(objectMapper.valueToTree(result).has("email"));
    }
}
