package com.orange.service.impl;

import com.orange.entity.po.OAuthCustomToken;
import com.orange.entity.po.TlcPat;
import com.orange.entity.vo.pat.PatMigrationResultVO;
import com.orange.mapper.OAuthCustomTokenMapper;
import com.orange.mapper.TlcPatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 全量迁移服务的行为固化
 *
 * <p>这一版刻意<strong>不做 Redis 判空</strong>：旧表中已被撤销（Redis 明细已删、签发记录仍在）
 * 的行也会迁成可用 PAT。该取舍由需求方确认，测试用 {@code opsForValue()} 从未被调用把这条钉住，
 * 避免后续有人「好心」把判空加回来。</p>
 *
 * @author UserCenter
 */
class PatMigrationServiceImplTest {

    private OAuthCustomTokenMapper oauthCustomTokenMapper;

    private TlcPatMapper tlcPatMapper;

    private StringRedisTemplate stringRedisTemplate;

    private SetOperations<String, String> setOperations;

    private PatMigrationServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        oauthCustomTokenMapper = mock(OAuthCustomTokenMapper.class);
        tlcPatMapper = mock(TlcPatMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        service = new PatMigrationServiceImpl(oauthCustomTokenMapper, tlcPatMapper, stringRedisTemplate);
    }

    /**
     * 未过期行全部搬迁，不论其在 Redis 中是否还有明细
     */
    @Test
    void migratesEveryAliveRowWithoutConsultingRedis() {
        when(oauthCustomTokenMapper.countAlive()).thenReturn(2L);
        when(oauthCustomTokenMapper.selectAliveAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(row(1L, 100L, "aaa"), row(2L, 100L, "bbb")))
                .thenReturn(List.of());

        PatMigrationResultVO result = service.run();

        assertEquals(2L, result.getTotal());
        assertEquals(2L, result.getMigrated());
        assertEquals(0L, result.getSkipped());
        assertEquals(0L, result.getFailed());
        verify(oauthCustomTokenMapper, times(2)).selectAliveAfterId(anyLong(), anyInt());
        verify(tlcPatMapper, times(1)).insertIgnoreBatch(anyList());
        // 判空被移除：整轮迁移不读令牌明细
        verify(stringRedisTemplate, never()).opsForValue();
        verify(stringRedisTemplate, times(1)).delete(anyCollection());
        verify(setOperations, times(1)).remove(eq("uc:uid:oauth:100"), any(Object[].class));
    }

    /**
     * 三个字段要原样搬过去：明文、展示前缀、签发时间
     */
    @Test
    @SuppressWarnings("unchecked")
    void carriesPlaintextPrefixAndCreateTimeIntoNewTable() {
        String token = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        LocalDateTime created = LocalDateTime.of(2025, 3, 1, 12, 0);
        when(oauthCustomTokenMapper.countAlive()).thenReturn(1L);
        when(oauthCustomTokenMapper.selectAliveAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(row(1L, 7L, token, created)))
                .thenReturn(List.of());

        service.run();

        ArgumentCaptor<List<TlcPat>> captor = ArgumentCaptor.forClass(List.class);
        verify(tlcPatMapper).insertIgnoreBatch(captor.capture());
        TlcPat pat = captor.getValue().get(0);
        assertEquals(token, pat.getToken());
        assertEquals("0123456789ab", pat.getTokenPrefix());
        assertEquals(created, pat.getCreateTime());
        assertEquals("app-1", pat.getClientId());
    }

    /**
     * 插入失败时不得清理 Redis：宁可重跑，也不能出现「旧凭证已删、新表又没有」
     */
    @Test
    void keepsRedisUntouchedWhenInsertFails() {
        when(oauthCustomTokenMapper.countAlive()).thenReturn(2L);
        when(oauthCustomTokenMapper.selectAliveAfterId(anyLong(), anyInt()))
                .thenReturn(List.of(row(1L, 100L, "aaa"), row(2L, 100L, "bbb")))
                .thenReturn(List.of());
        when(tlcPatMapper.insertIgnoreBatch(anyList())).thenThrow(new IllegalStateException("db down"));

        PatMigrationResultVO result = service.run();

        assertEquals(2L, result.getTotal());
        assertEquals(0L, result.getMigrated());
        assertEquals(2L, result.getFailed());
        verify(stringRedisTemplate, never()).delete(anyCollection());
        verify(setOperations, never()).remove(anyString(), any(Object[].class));
    }

    /**
     * 没有待处理行时不发任何写命令（重跑的第二轮就是这个形态）
     */
    @Test
    void doesNothingWhenNoAliveRowLeft() {
        when(oauthCustomTokenMapper.countAlive()).thenReturn(0L);
        when(oauthCustomTokenMapper.selectAliveAfterId(anyLong(), anyInt())).thenReturn(List.of());

        PatMigrationResultVO result = service.run();

        assertEquals(0L, result.getTotal());
        assertEquals(0L, result.getMigrated());
        assertEquals(0L, result.getFailed());
        verify(tlcPatMapper, never()).insertIgnoreBatch(anyList());
        verify(stringRedisTemplate, never()).delete(anyCollection());
    }

    private OAuthCustomToken row(long id, long uid, String token) {
        return row(id, uid, token, LocalDateTime.of(2024, 6, 1, 8, 0));
    }

    private OAuthCustomToken row(long id, long uid, String token, LocalDateTime createTime) {
        OAuthCustomToken row = new OAuthCustomToken();
        row.setId(id);
        row.setUid(uid);
        row.setClientId("app-1");
        row.setName("token-" + id);
        row.setToken(token);
        row.setScope("userinfo");
        row.setExpiresIn(86400L);
        row.setExpireTime(LocalDateTime.of(2027, 1, 1, 0, 0));
        row.setCreateTime(createTime);
        return row;
    }
}
