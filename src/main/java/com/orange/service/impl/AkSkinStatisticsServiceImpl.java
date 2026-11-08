package com.orange.service.impl;

import com.orange.common.util.LogUtil;
import com.orange.entity.po.AkSkinState;
import com.orange.entity.po.AkSkinStatistics;
import com.orange.entity.vo.akoperator.SkinStatisticsVO;
import com.orange.mapper.AkSkinStateMapper;
import com.orange.mapper.AkSkinStatisticsMapper;
import com.orange.service.AkSkinStatisticsService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 皮肤持有统计服务实现
 *
 * <p>执行流程：按主键键集分页全表扫描 ak_skin_state，边扫边按 skin_id 累加「拥有该皮肤的账号数」，
 * 同时归集出现过的 ak_uid 用于得到本轮参与统计的账号总数。</p>
 *
 * <p>口径说明：</p>
 * <ul>
 *   <li>{@code own} 直接等于该皮肤的行数——uk_ak_skin 保证一个账号同一皮肤只有一行，
 *       所以「行数」就是「拥有的账号数」，不需要额外去重。</li>
 *   <li>{@code totalAccounts} 取本次扫描中出现的不同 ak_uid 数，即「上报过皮肤数据的账号数」。
 *       未上报过皮肤的绑定账号不在分母内，否则会把从未同步过的账号算成「没有皮肤」，拉低持有率。</li>
 * </ul>
 *
 * <p>不统计获得时间：{@code gain_ts} 只能得到样本中的最早/最晚，受样本覆盖影响大，
 * 容易被误读成皮肤上线时间或「当前是否可获取」；这两类信息属于游戏静态数据与运营配置，
 * 玩家数据不做反推。</p>
 *
 * <p>内存控制：每批只保留本批记录，累加完成后即可回收，批大小决定每批的堆占用峰值
 * （见 {@link #BATCH_SIZE}）；常驻内存为「皮肤 → 账号数」映射（皮肤数量级，几千行）
 * 与账号集合（约 50~80 字节/账号，十万账号约几 MB）。皮肤行数是千万量级，若改用
 * {@code GROUP BY} 一次聚合，数据库要物化整表结果，低配服务器上反而更危险。</p>
 *
 * <p>一致性说明：全表扫描不加事务，每批是一次独立查询。统计期间新写入的数据可能落在已扫描
 * 区间之外，属于离线跑批可接受的口径。扫描必须留在事务外还有一层原因：MyBatis 默认 SESSION 级
 * 本地缓存会缓存同一会话内每条 SELECT 的结果，扫描若与其它写操作共用一个事务会话，循环里没有
 * 写操作去清缓存，已扫过的每一批都会一直留在堆上直至 OOM。</p>
 *
 * <p>落库与查询：{@link #refreshSkinStatistics()} 在统计完成后用一个只包住落库的小事务整表替换
 * 结果表（清空 + 逐条插入），读方要么看到旧快照、要么看到新快照；{@link #getSkinStatistics()}
 * 只读结果表，供在线接口使用，不会触发全表扫描。</p>
 *
 * @author UserCenter
 */
@Service
public class AkSkinStatisticsServiceImpl implements AkSkinStatisticsService {

    /** 扫描进度日志的输出间隔（每多少批打印一次，100 批即 100 万条） */
    private static final int PROGRESS_LOG_INTERVAL_BATCHES = 100;

    /**
     * 每批读取的皮肤记录数
     *
     * <p>取 1 万：JDBC 驱动会先把整批结果集缓冲在客户端，再叠加 MyBatis 映射出的实体，
     * 每批堆占用大致与行数成正比，1 万行对应几 MB，是 4G 内存服务器上更稳妥的量级。
     * 批次调小只增加主键范围查询的次数，单次查询本身很轻。</p>
     */
    private static final int BATCH_SIZE = 10_000;

    /** 单次统计的最大批次数：10000 × 1 万 = 1 亿条，足以覆盖当前皮肤表的千万量级数据 */
    private static final int MAX_BATCH_ROUNDS = 10_000;

    private final AkSkinStateMapper skinStateMapper;
    private final AkSkinStatisticsMapper statisticsMapper;
    private final TaskExecutor taskExecutor;

    /** 落库专用事务模板：清空结果表 + 逐条插入必须原子，而扫描阶段必须留在事务外 */
    private final TransactionTemplate transactionTemplate;

    /** 统计中标志：用 CAS 抢占，保证同一时刻只有一轮全表扫描在跑 */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * 构造器注入依赖
     *
     * @param skinStateMapper    皮肤数据 Mapper，用于分批扫描明细
     * @param statisticsMapper   统计结果 Mapper
     * @param taskExecutor       后台任务执行器，触发后把统计提交到独立线程，避免阻塞调度线程
     * @param transactionManager 事务管理器，用于构造仅包裹落库动作的事务模板
     */
    public AkSkinStatisticsServiceImpl(AkSkinStateMapper skinStateMapper,
                                       AkSkinStatisticsMapper statisticsMapper,
                                       TaskExecutor taskExecutor,
                                       PlatformTransactionManager transactionManager) {
        this.skinStateMapper = skinStateMapper;
        this.statisticsMapper = statisticsMapper;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 全量统计所有皮肤的持有情况
     *
     * @return 按皮肤编码升序排列的统计结果，无数据时返回空列表
     */
    @Override
    public List<SkinStatisticsVO> collectSkinStatistics() {
        long totalStartNanos = System.nanoTime();

        // 皮肤编码 → 拥有该皮肤的账号数，用 TreeMap 保证输出顺序稳定
        Map<String, Long> skinOwnCounts = new TreeMap<>();
        // 本轮出现过的账号，仅用于得到持有率分母；账号数远小于皮肤行数，集合只占几 MB
        Set<String> akUids = new HashSet<>();

        long lastId = 0L;
        long scannedRows = 0L;
        boolean exhausted = false;
        // 拆开统计 DB 往返与内存累加的耗时，便于定位瓶颈在哪一侧
        long queryNanos = 0L;
        long accumulateNanos = 0L;
        for (int batchIndex = 1; batchIndex <= MAX_BATCH_ROUNDS; batchIndex++) {
            long queryStartNanos = System.nanoTime();
            List<AkSkinState> batch = skinStateMapper.selectStatisticsBatch(lastId, BATCH_SIZE);
            queryNanos += System.nanoTime() - queryStartNanos;
            if (batch.isEmpty()) {
                exhausted = true;
                break;
            }
            long accumulateStartNanos = System.nanoTime();
            for (AkSkinState row : batch) {
                skinOwnCounts.merge(row.getSkinId(), 1L, Long::sum);
                akUids.add(row.getAkUid());
            }
            accumulateNanos += System.nanoTime() - accumulateStartNanos;
            scannedRows += batch.size();
            lastId = batch.get(batch.size() - 1).getId();
            if (batchIndex % PROGRESS_LOG_INTERVAL_BATCHES == 0) {
                LogUtil.info(AkSkinStatisticsServiceImpl.class,
                        "皮肤数据统计进度：批次 {}/{}，已扫描 {} 条，累计皮肤 {} 个、账号 {} 个，"
                                + "DB 查询 {} ms、内存累加 {} ms，总耗时 {} ms",
                        batchIndex, MAX_BATCH_ROUNDS, scannedRows, skinOwnCounts.size(), akUids.size(),
                        nanosToMillis(queryNanos), nanosToMillis(accumulateNanos), elapsedMillis(totalStartNanos));
            }
            if (batch.size() < BATCH_SIZE) {
                exhausted = true;
                break;
            }
        }
        if (!exhausted) {
            LogUtil.warn(AkSkinStatisticsServiceImpl.class,
                    "已达到最大批次轮数 {}（最多 {} 条），本次统计未覆盖全表，结果可能不完整",
                    MAX_BATCH_ROUNDS, (long) MAX_BATCH_ROUNDS * BATCH_SIZE);
        }

        long totalAccounts = akUids.size();
        List<SkinStatisticsVO> result = buildResult(skinOwnCounts, totalAccounts);

        LogUtil.info(AkSkinStatisticsServiceImpl.class,
                "皮肤数据统计完成：共扫描 {} 条记录，{} 个皮肤，{} 个账号，"
                        + "DB 查询 {} ms、内存累加 {} ms，总耗时 {} ms",
                scannedRows, result.size(), totalAccounts, nanosToMillis(queryNanos),
                nanosToMillis(accumulateNanos), elapsedMillis(totalStartNanos));
        return result;
    }

    /**
     * 重新统计皮肤数据，并整表替换统计结果表
     *
     * <p>落库用 DELETE 而不是 TRUNCATE：TRUNCATE 属 DDL 会隐式提交，无法与插入同处一个事务，
     * 读方就会看到空表窗口。本表只有数千行，逐条插入的代价可忽略。</p>
     *
     * <p>整个方法刻意不加 {@code @Transactional}：事务只包住最后的「清空 + 逐条插入」。
     * 若把全表扫描也纳入事务，MyBatis 会在同一个会话里执行所有分批查询，而 MyBatis 默认
     * SESSION 级本地缓存会缓存每条 SELECT 的结果、且循环中没有任何写操作去清理它，
     * 堆占用将随扫描行数线性增长，扫到几百万行就会 OOM。</p>
     *
     * @return 本轮写入的皮肤数量
     */
    @Override
    public int refreshSkinStatistics() {
        long totalStartNanos = System.nanoTime();

        List<SkinStatisticsVO> statistics = collectSkinStatistics();
        LocalDateTime statisticsTime = LocalDateTime.now();
        long collectMillis = elapsedMillis(totalStartNanos);

        long persistStartNanos = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> {
            statisticsMapper.deleteAll();
            for (SkinStatisticsVO vo : statistics) {
                statisticsMapper.insert(toEntity(vo, statisticsTime));
            }
        });
        LogUtil.info(AkSkinStatisticsServiceImpl.class,
                "皮肤数据统计结果已落库：{} 个皮肤，统计时间 {}，统计耗时 {} ms，落库耗时 {} ms，合计 {} ms",
                statistics.size(), statisticsTime, collectMillis, elapsedMillis(persistStartNanos),
                elapsedMillis(totalStartNanos));
        return statistics.size();
    }

    /**
     * 异步触发一轮皮肤数据统计，供定时调度使用
     *
     * <p>先在调用线程用 CAS 抢占统计中标志：抢不到说明已有一轮在跑，直接返回 false，
     * 避免两轮全表扫描同时进行并互相覆盖结果；抢占成功则立即返回，真正的统计提交到
     * 后台线程执行。</p>
     *
     * @return true 表示本次已成功触发；false 表示已有一轮统计在执行，本次未触发
     */
    @Override
    public boolean triggerSkinStatisticsRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            LogUtil.warn(AkSkinStatisticsServiceImpl.class, "皮肤数据统计已在进行中，本次触发被忽略");
            return false;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    refreshSkinStatistics();
                } catch (Exception e) {
                    LogUtil.error(AkSkinStatisticsServiceImpl.class, "定时触发的皮肤数据统计执行失败", e);
                } finally {
                    refreshing.set(false);
                }
            });
        } catch (RuntimeException e) {
            // 提交失败必须立刻释放标志，否则后续触发会被永久挡住
            refreshing.set(false);
            throw e;
        }
        LogUtil.info(AkSkinStatisticsServiceImpl.class, "皮肤数据统计已触发，将在后台执行");
        return true;
    }

    /**
     * 读取最近一轮皮肤统计结果
     *
     * @return 按皮肤编码升序排列的统计结果，尚未跑过统计时返回空列表
     */
    @Override
    public List<SkinStatisticsVO> getSkinStatistics() {
        List<AkSkinStatistics> rows = statisticsMapper.selectList(null);
        List<SkinStatisticsVO> result = new ArrayList<>(rows.size());
        for (AkSkinStatistics row : rows) {
            result.add(toVO(row));
        }
        result.sort(Comparator.comparing(SkinStatisticsVO::getSkinId));
        return result;
    }

    /**
     * 汇总最终统计结果
     *
     * @param skinOwnCounts 皮肤编码 → 拥有该皮肤的账号数
     * @param totalAccounts 本轮参与统计的账号总数
     * @return 按皮肤编码升序排列的统计结果
     */
    private static List<SkinStatisticsVO> buildResult(Map<String, Long> skinOwnCounts,
                                                      long totalAccounts) {
        List<SkinStatisticsVO> result = new ArrayList<>(skinOwnCounts.size());
        for (Map.Entry<String, Long> entry : skinOwnCounts.entrySet()) {
            SkinStatisticsVO vo = new SkinStatisticsVO();
            vo.setSkinId(entry.getKey());
            vo.setOwn(entry.getValue());
            vo.setTotalAccounts(totalAccounts);
            result.add(vo);
        }
        return result;
    }

    /**
     * 计算从指定时间点至今经过的毫秒数
     *
     * @param startNanos 起始时间点，由 {@link System#nanoTime()} 获取
     * @return 经过的毫秒数
     */
    private static long elapsedMillis(long startNanos) {
        return nanosToMillis(System.nanoTime() - startNanos);
    }

    /**
     * 纳秒转毫秒
     *
     * @param nanos 纳秒数
     * @return 毫秒数
     */
    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }

    /**
     * 统计结果 VO 转实体
     *
     * @param vo             单个皮肤的统计结果
     * @param statisticsTime 本轮统计的完成时间
     * @return 待落库实体
     */
    private static AkSkinStatistics toEntity(SkinStatisticsVO vo, LocalDateTime statisticsTime) {
        AkSkinStatistics entity = new AkSkinStatistics();
        entity.setSkinId(vo.getSkinId());
        entity.setOwn(vo.getOwn());
        entity.setTotalAccounts(vo.getTotalAccounts());
        entity.setStatisticsTime(statisticsTime);
        return entity;
    }

    /**
     * 实体转统计结果 VO
     *
     * @param entity 统计结果实体
     * @return 统计结果 VO
     */
    private static SkinStatisticsVO toVO(AkSkinStatistics entity) {
        SkinStatisticsVO vo = new SkinStatisticsVO();
        vo.setSkinId(entity.getSkinId());
        vo.setOwn(entity.getOwn() == null ? 0L : entity.getOwn());
        vo.setTotalAccounts(entity.getTotalAccounts() == null ? 0L : entity.getTotalAccounts());
        return vo;
    }
}
