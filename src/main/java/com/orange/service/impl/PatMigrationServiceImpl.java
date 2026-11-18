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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 旧自定义令牌 → PAT 迁移实现（阶段二，一次性）
 *
 * <p><strong>全量搬迁</strong>：旧表所有未过期的行原样写入 {@code tlc_pat}，不再逐条判空。
 * 这是明确的业务取舍——旧表里既有「用户主动撤销过、Redis 明细已删但签发记录仍在」的行，
 * 全量搬迁会把它们一并搬成可用 PAT（即所谓「复活」）。已确认接受该代价，换取部署期不必
 * 逐条往返 Redis、整轮迁移可在秒级完成。</p>
 *
 * <p>三个动作在每个批次内完成，顺序固定、不能调换：先批量 {@code INSERT IGNORE} 让新表可查，
 * 再 {@code SREM} 让令牌脱离 OAuth 索引（否则批量吊销仍会删它、与 PAT 语义冲突），最后
 * {@code DEL} 旧 key 消除双副本（否则按 id 撤销删不掉旧副本）。插入抛异常时本批一条 Redis
 * 都不动，保证不会出现「旧凭证已删、新表又没有」的空窗。</p>
 *
 * <p>旧令牌明文原样写入 {@code tlc_pat.token}，不换形态、不计算摘要：迁移只改存储位置，
 * 已经发出去多年的旧凭证必须能被消费端按同一串明文查到。{@code create_time} 一并搬迁，
 * 否则会落到列默认值 {@code CURRENT_TIMESTAMP}，用户列表里全部显示为迁移当天创建。</p>
 *
 * @author UserCenter
 */
@Service
public class PatMigrationServiceImpl implements PatMigrationService {

    /** 每批读取、写入与清理的行数 */
    private static final int BATCH_SIZE = 500;

    /** uid 反向索引中 access_token 成员前缀（与 OAuthTokenServiceImpl 保持一致） */
    private static final String OAUTH_ACCESS_MEMBER_PREFIX = "access:";

    private final OAuthCustomTokenMapper oauthCustomTokenMapper;

    private final TlcPatMapper tlcPatMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 构造器注入依赖
     *
     * @param oauthCustomTokenMapper 旧自定义令牌 Mapper（只读）
     * @param tlcPatMapper           PAT Mapper（批量写入新表）
     * @param stringRedisTemplate    Redis 客户端（摘索引、删旧 key）
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
     * <p>批次异常只计入 {@code failed}，不阻断后续；游标按 id 前进，重跑从已处理位置之后继续。
     * 已写过的行因唯一键 {@code uk_tlc_pat_token} 被 {@code INSERT IGNORE} 跳过，重复调用不会翻倍。</p>
     *
     * <p>端点已无开关、浏览器或运维脚本都可能重复触发，故方法加锁串行：并发双跑会让同一批行被
     * 两个线程各自插入与清理，{@code migrated} 重复计数而 {@code total} 只在入口取一次，
     * 统计口径（{@code migrated + failed == total}）当场失真。后到的请求排队等前一次跑完。</p>
     *
     * @return 迁移统计
     */
    @Override
    public synchronized PatMigrationResultVO run() {
        long startedAt = System.currentTimeMillis();
        PatMigrationResultVO result = new PatMigrationResultVO();
        result.setTotal(oauthCustomTokenMapper.countAlive());

        long cursor = 0L;
        while (true) {
            List<OAuthCustomToken> batch = oauthCustomTokenMapper.selectAliveAfterId(cursor, BATCH_SIZE);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            cursor = batch.get(batch.size() - 1).getId();
            try {
                migrateBatch(batch);
                result.setMigrated(result.getMigrated() + batch.size());
            } catch (Exception e) {
                result.setFailed(result.getFailed() + batch.size());
                LogUtil.warn(PatMigrationServiceImpl.class,
                        "[PAT迁移] 批次失败，跳过: cursor={}, size={}", cursor, batch.size(), e);
            }
            LogUtil.info(PatMigrationServiceImpl.class,
                    "[PAT迁移] 进度: cursor={}, migrated={}, failed={}",
                    cursor, result.getMigrated(), result.getFailed());
        }

        result.setElapsedMs(System.currentTimeMillis() - startedAt);
        LogUtil.info(PatMigrationServiceImpl.class,
                "[PAT迁移] 完成: total={}, migrated={}, skipped={}, failed={}, elapsedMs={}",
                result.getTotal(), result.getMigrated(), result.getSkipped(),
                result.getFailed(), result.getElapsedMs());
        return result;
    }

    /**
     * 迁移一批：整批写入新表，再整批清理旧链路在 Redis 中的痕迹
     *
     * <p>写入与清理保持「先写后删」：插入抛异常时直接向上抛，本批的 Redis key 一条都不动。</p>
     *
     * @param batch 本批旧表记录
     */
    private void migrateBatch(List<OAuthCustomToken> batch) {
        List<TlcPat> rows = new ArrayList<>(batch.size());
        List<String> tokenKeys = new ArrayList<>(batch.size());
        Map<Long, List<Object>> membersByUid = new HashMap<>();

        for (OAuthCustomToken source : batch) {
            rows.add(toPat(source));
            tokenKeys.add(RedisKeyUtil.oauthAccess(source.getToken()));
            membersByUid.computeIfAbsent(source.getUid(), key -> new ArrayList<>())
                    .add(OAUTH_ACCESS_MEMBER_PREFIX + source.getToken());
        }

        tlcPatMapper.insertIgnoreBatch(rows);

        // 反向索引按 uid 分组，同一用户的多条令牌一次 SREM 摘除，减少往返
        for (Map.Entry<Long, List<Object>> entry : membersByUid.entrySet()) {
            stringRedisTemplate.opsForSet().remove(
                    RedisKeyUtil.uidOauth(entry.getKey()), entry.getValue().toArray());
        }
        // 变参 DEL：一批 500 个 key 只发一条命令，避免逐条往返
        stringRedisTemplate.delete(tokenKeys);
    }

    /**
     * 旧表记录 → PAT 实体（字段一一对应，不做任何转换）
     *
     * @param source 旧表记录
     * @return 待写入的 PAT 实体
     */
    private TlcPat toPat(OAuthCustomToken source) {
        TlcPat pat = new TlcPat();
        pat.setUid(source.getUid());
        pat.setClientId(source.getClientId());
        pat.setName(source.getName());
        pat.setToken(source.getToken());
        pat.setTokenPrefix(PatUtil.displayPrefix(source.getToken()));
        pat.setScope(source.getScope());
        pat.setExpiresIn(source.getExpiresIn());
        pat.setExpireTime(source.getExpireTime());
        pat.setPayload(null);
        pat.setCreateTime(source.getCreateTime());
        return pat;
    }
}
