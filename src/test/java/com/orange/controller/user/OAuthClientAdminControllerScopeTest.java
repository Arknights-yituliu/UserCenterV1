package com.orange.controller.user;

import com.orange.entity.vo.oauth.OAuthScopeVO;
import com.orange.service.OAuthClientAdminService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** OAuth scope 元数据接口契约测试。 */
class OAuthClientAdminControllerScopeTest {

    @Test
    void scopesReturnsAllPlatformScopesWithMetadata() {
        OAuthClientAdminController controller = new OAuthClientAdminController(mock(OAuthClientAdminService.class));

        List<OAuthScopeVO> scopes = controller.scopes().getData();

        assertEquals(7, scopes.size());
        OAuthScopeVO email = scopes.stream()
                .filter(scope -> "user.email".equals(scope.getCode()))
                .findFirst()
                .orElseThrow();
        assertEquals("绑定邮箱", email.getName());
        assertTrue(email.isSensitive());
        assertTrue(scopes.stream().anyMatch(scope -> "gama-data.read".equals(scope.getCode())));
        assertTrue(scopes.stream().anyMatch(scope -> "gama-data.write".equals(scope.getCode())));
    }
}
