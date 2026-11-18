package com.orange.common.context;

import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.pat.PatPayload;
import com.orange.service.PatPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** PAT 上下文测试：主体存取与线程清理。 */
class PatContextTest {

    @AfterEach
    void clearContext() {
        PatContext.clear();
    }

    @Test
    void requireReturnsStoredPrincipal() {
        PatPrincipal principal = new PatPrincipal(7001L, "this_land_cloud", "ak-data.read",
                new PatPayload(), true);
        PatContext.set(principal);

        assertSame(principal, PatContext.require());
    }

    @Test
    void requireRejectsMissingPrincipal() {
        BusinessException exception = assertThrows(BusinessException.class, PatContext::require);

        assertEquals(80001, exception.getCode());
    }

    @Test
    void clearRemovesPrincipal() {
        PatContext.set(new PatPrincipal(7001L, "this_land_cloud", "ak-data.read",
                new PatPayload(), true));

        PatContext.clear();

        assertThrows(BusinessException.class, PatContext::require);
    }
}
