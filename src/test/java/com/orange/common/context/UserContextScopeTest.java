package com.orange.common.context;

import com.orange.common.enums.OAuthScope;
import com.orange.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** OAuth scope 上下文校验测试。 */
class UserContextScopeTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void requireScopeAllowsExactGrantedScope() {
        UserContext.setScope("user.read,config.read");

        assertDoesNotThrow(() -> UserContext.requireScope(OAuthScope.CONFIG_READ));
    }

    @Test
    void requireScopeRejectsMissingScope() {
        UserContext.setScope("user.read");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserContext.requireScope(OAuthScope.CONFIG_WRITE));

        assertEquals(80008, exception.getCode());
    }

    @Test
    void requireScopeAllowsEverythingWhenAllGranted() {
        // 元范围 all 视为已获得全部范围
        UserContext.setScope(OAuthScope.ALL.getCode());

        assertDoesNotThrow(() -> UserContext.requireScope(OAuthScope.CONFIG_WRITE));
        assertDoesNotThrow(() -> UserContext.requireScope(OAuthScope.GAMA_DATA_WRITE));
    }

    @Test
    void requireScopeAllowsChildScopeWhenParentGranted() {
        // 上级范围 gama-data.read 覆盖两个游戏的读取范围
        UserContext.setScope("gama-data.read");

        assertDoesNotThrow(() -> UserContext.requireScope(OAuthScope.AK_DATA_READ));
        assertDoesNotThrow(() -> UserContext.requireScope(OAuthScope.EF_DATA_READ));
        // 读权限不隐含写权限
        BusinessException exception = assertThrows(BusinessException.class,
                () -> UserContext.requireScope(OAuthScope.AK_DATA_WRITE));
        assertEquals(80008, exception.getCode());
    }

    @Test
    void requireScopeRejectsOtherGameAndParentWhenChildGranted() {
        // 只持有明日方舟范围时，不能访问终末地，也不能反向获得上级范围
        UserContext.setScope("arknights-data.read");

        assertDoesNotThrow(() -> UserContext.requireScope(OAuthScope.AK_DATA_READ));
        assertThrows(BusinessException.class, () -> UserContext.requireScope(OAuthScope.EF_DATA_READ));
        assertThrows(BusinessException.class, () -> UserContext.requireScope(OAuthScope.GAMA_DATA_READ));
    }
}
