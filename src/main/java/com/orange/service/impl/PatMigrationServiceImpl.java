package com.orange.service.impl;

import com.orange.common.util.LogUtil;
import com.orange.common.util.PatUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.po.OAuthCustomToken;
import com.orange.entity.po.TlcPat;
import com.orange.entity.vo.pat.PatMigrationResultVO;
import com.orange.mapper.OAuthCustomTokenMapper;
import com.orange.mapper.TlcPatMapper;
import com.orange.service.PatMigrationService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 旧自定义令牌 → PAT 迁移实现（阶段二，一次性）
 *
 * <p>三个动作的顺序是固定的，不能调换：先 {@code INSERT IGNORE} 保证新表可查，再
 * {@code SREM} 让令牌脱离 OAuth 索引（否则批量吊销仍会删它、与 PAT 语义冲突），最后
 * {@code DEL} 旧 key 消除双副本（否则按 id 撤销删不掉旧副本）。</p>
 *
 * <p>{@code GET} 判空是唯一分支：{@code revokeClientAuthorization} 一直只删 Redis 不删记录，
 * 存量里必有一批「已被吊销但记录还在」的行，不加这一步会把它们迁成可用 PAT。</p>
 *
 * <p>旧令牌明文原样写入 {@code tlc_pat.token}，不换形态、不计算摘要：迁移只改存储位置，
 * 已经发出去多年的旧凭证必须能被消费端按同一串明文查到。</p>
 *
 * @author UserCenter
 */
@Service
public class PatMigrationServiceImpl implements PatMigrationService {

    /** 每批读取行数 */
    private static final int BATCH_SIZE = 500;

    /** uid 反向索引中 access_token 成员前缀（与 OAuthTokenServiceImpl 保持一致） */
    private static final String OAUTH_ACCESS_MEMBER_PREFIX = "access:";

    /** 迁移来源标记 */
    private static final String SOURCE_LEGACY = "legacy";

    private final OAuthCustomTokenMapper oauthCustomTokenMapper;

    private final TlcPatMapper tlcPatMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造器注入依赖
     *
     * @param oauthCustomTokenMapper 旧自定义令牌 Mapper（只读）
     * @param tlcPatMapper           PAT Mapper（写入新表）
     * @param stringRedisTemplate    Redis 客户端（读旧 key、摘索引、删旧 key）
     */
    public PatMigrationServiceImpl(OAuthCustomTokenMapper oauthCustomTokenMapper, TlcPatMapper tlcPatMapper,
                                   StringRedisTemplate stringRedisTemplate) {
        this.oauthCustomTokenMapper = oauthCustomTokenMapper;
        this.tlcPatMapper = tlcPatMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 执行一次全量迁移
     *
     * <p>单行异常只计入 {@code failed}，不阻断后续；游标按 id 前进，重跑从已处理位置之后继续，
     * 已处理行的旧 key 已空、会落进 {@code skipped}。</p>
     *
     * @return 迁移统计
     */
    @Override
    public PatMigrationResultVO run() {
        long startedAt = System.currentTimeMillis();
        PatMigrationResultVO result = new PatMigrationResultVO();
        result.setTotal(oauthCustomTokenMapper.countAlive());

        long cursor = 0L;
        while (true) {
            List<OAuthCustomToken> batch = oauthCustomTokenMapper.selectAliveAfterId(cursor, BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (OAuthCustomToken row : batch) {
                cursor = row.getId();
                try {
                    migrateRow(row, result);
                } catch (Exception e) {
                    result.setFailed(result.getFailed() + 1);
                    LogUtil.warn(PatMigrationServiceImpl.class,
                            "[PAT迁移] 单行失败，跳过: id={}, uid={}", row.getId(), row.getUid(), e);
                }
            }
            LogUtil.info(PatMigrationServiceImpl.class,
                    "[PAT迁移] 进度: cursor={}, migrated={}, skipped={}, failed={}",
                    cursor, result.getMigrated(), result.getSkipped(), result.getFailed());
        }

        result.setElapsedMs(System.currentTimeMillis() - startedAt);
        LogUtil.info(PatMigrationServiceImpl.class,
                "[PAT迁移] 完成: total={}, migrated={}, skipped={}, failed={}, elapsedMs={}",
                result.getTotal(), result.getMigrated(), result.getSkipped(),
                result.getFailed(), result.getElapsedMs());
        return result;
    }

    /**
     * 迁移单行：旧 key 存在才迁，迁完摘索引、删旧 key
     *
     * @param row    旧表记录
     * @param result 统计累加器
     */
    private void migrateRow(OAuthCustomToken row, PatMigrationResultVO result) {
        String oldKey = RedisKeyUtil.oauthAccess(row.getToken());
        String raw = stringRedisTemplate.opsForValue().get(oldKey);
        if (raw == null) {
            // 已被吊销或已迁移过：不迁，避免把「记录在但令牌已失效」的行恢复成可用 PAT
            result.setSkipped(result.getSkipped() + 1);
            return;
        }

        TlcPat pat = new TlcPat();
        pat.setUid(row.getUid());
        pat.setClientId(row.getClientId());
        pat.setName(row.getName());
        pat.setToken(row.getToken());
        pat.setTokenPrefix(PatUtil.displayPrefix(row.getToken()));
        pat.setScope(row.getScope());
        pat.setExpiresIn(row.getExpiresIn());
        pat.setExpireTime(row.getExpireTime());
        pat.setSource(SOURCE_LEGACY);
        pat.setPayload(null);
        // 明文已存在时影响 0 行，仍继续清理旧桶——重跑同一条时以这种形式收敛
        tlcPatMapper.insertIgnore(pat);

        stringRedisTemplate.opsForSet().remove(
                RedisKeyUtil.uidOauth(row.getUid()), OAUTH_ACCESS_MEMBER_PREFIX + row.getToken());
        stringRedisTemplate.delete(oldKey);
        result.setMigrated(result.getMigrated() + 1);
    }
}
