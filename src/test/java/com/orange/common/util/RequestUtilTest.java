package com.orange.common.util;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class RequestUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"token", "client", "ticket"})
    void acceptsEitherNameAndMatchingAliases(String kind) {
        assertEquals("value", resolve(kind, " value ", null));
        assertEquals("value", resolve(kind, null, " value "));
        assertEquals("value", resolve(kind, " value ", "value"));
        assertEquals("value", resolve(kind, "  ", "value"));
        assertEquals("value", resolve(kind, "value", "  "));
        assertNull(resolve(kind, null, null));
        assertNull(resolve(kind, " ", " "));
    }

    @ParameterizedTest
    @ValueSource(strings = {"token", "client", "ticket"})
    void rejectsConflictingAliases(String kind) {
        BusinessException error = assertThrows(BusinessException.class,
                () -> resolve(kind, "first", "second"));
        assertEquals(ResultCode.PARAM_ERROR.getCode(), error.getCode());
    }

    @Test
    void bearerRetainsPriorityOverBothAliasHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bearer-token ");
        request.addHeader("TCL-Token", "new-token");
        request.addHeader("UC-Token", "old-token");
        assertEquals("bearer-token", RequestUtil.resolveToken(request));
    }

    private String resolve(String kind, String current, String legacy) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if ("ticket".equals(kind)) {
            if (current != null) request.addParameter("tcl_ticket", current);
            if (legacy != null) request.addParameter("uc_ticket", legacy);
            return RequestUtil.resolveLoginTicket(request);
        }
        String suffix = "token".equals(kind) ? "Token" : "Client-Id";
        if (current != null) request.addHeader("TCL-" + suffix, current);
        if (legacy != null) request.addHeader("UC-" + suffix, legacy);
        return "token".equals(kind) ? RequestUtil.resolveToken(request) : RequestUtil.resolveClientId(request);
    }
}
