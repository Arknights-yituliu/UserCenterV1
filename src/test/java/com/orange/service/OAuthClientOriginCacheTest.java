package com.orange.service;

import com.orange.mapper.OAuthClientOriginMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** OAuth 客户端 Origin 内存快照测试。 */
class OAuthClientOriginCacheTest {

    @Test
    void refreshAtomicallyReplacesAllowedOrigins() {
        OAuthClientOriginMapper mapper = mock(OAuthClientOriginMapper.class);
        when(mapper.selectApprovedOrigins()).thenReturn(List.of(
                "https://one.example.com",
                "https://two.example.com",
                "https://one.example.com"));
        OAuthClientOriginCache cache = new OAuthClientOriginCache(mapper);

        int count = cache.refresh();

        assertEquals(2, count);
        assertTrue(cache.isAllowed("https://one.example.com"));
        assertTrue(cache.isAllowed("https://two.example.com"));
        assertFalse(cache.isAllowed("https://unknown.example.com"));
    }

    @Test
    void failedRefreshKeepsLastKnownGoodSnapshot() {
        OAuthClientOriginMapper mapper = mock(OAuthClientOriginMapper.class);
        when(mapper.selectApprovedOrigins())
                .thenReturn(List.of("https://one.example.com"))
                .thenThrow(new IllegalStateException("database unavailable"));
        OAuthClientOriginCache cache = new OAuthClientOriginCache(mapper);
        cache.refresh();

        assertThrows(IllegalStateException.class, cache::refresh);
        assertTrue(cache.isAllowed("https://one.example.com"));
    }
}
