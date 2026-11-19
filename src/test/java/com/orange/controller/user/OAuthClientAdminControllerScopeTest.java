package com.orange.controller.user;

import com.orange.common.enums.OAuthScope;
import com.orange.entity.vo.oauth.OAuthScopeVO;
import com.orange.service.OAuthClientAdminService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** OAuth scope 元数据接口契约测试。 */
class OAuthClientAdminControllerScopeTest {

    @Test
    void scopesReturnsAllPlatformScopesWithMetadata() {
        OAuthClientAdminController controller = new OAuthClientAdminController(mock(OAuthClientAdminService.class));

        List<OAuthScopeVO> scopes = controller.scopes().getData();

        assertEquals(OAuthScope.selectable().size(), scopes.size());
        assertFalse(scopes.stream().anyMatch(scope -> "user.email".equals(scope.getCode())));
        // all 是仅限管理员手工写库授予的元范围，不能作为自助配置项暴露
        assertFalse(scopes.stream().anyMatch(scope -> "all".equals(scope.getCode())));
        assertTrue(scopes.stream().anyMatch(scope -> "gama-data.read".equals(scope.getCode())));
        assertTrue(scopes.stream().anyMatch(scope -> "gama-data.write".equals(scope.getCode())));
        // 游戏级细粒度范围：明日方舟 / 终末地各自读写
        assertTrue(scopes.stream().anyMatch(scope -> "arknights-data.read".equals(scope.getCode())));
        assertTrue(scopes.stream().anyMatch(scope -> "arknights-data.write".equals(scope.getCode())));
        assertTrue(scopes.stream().anyMatch(scope -> "endfield-data.read".equals(scope.getCode())));
        assertTrue(scopes.stream().anyMatch(scope -> "endfield-data.write".equals(scope.getCode())));
    }
}
