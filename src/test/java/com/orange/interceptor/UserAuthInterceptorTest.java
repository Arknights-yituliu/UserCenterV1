package com.orange.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.UserContext;
import com.orange.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserAuthInterceptorTest {
    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"TCL-Token", "UC-Token"})
    void bothHeadersReadExistingSessionNamespace(String header) {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("uc:token:existing-session")).thenReturn("{\"uid\":9,\"clientId\":\"original-client\"}");
        UserAuthInterceptor interceptor = new UserAuthInterceptor(redis, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(header, "existing-session");

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(9L, UserContext.getUid());
        assertEquals("original-client", UserContext.getClientId());
        verify(values).get("uc:token:existing-session");
    }

    @Test
    void conflictingHeadersFailBeforeReadingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UserAuthInterceptor interceptor = new UserAuthInterceptor(redis, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("TCL-Token", "first");
        request.addHeader("UC-Token", "second");
        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(redis);
    }
}
