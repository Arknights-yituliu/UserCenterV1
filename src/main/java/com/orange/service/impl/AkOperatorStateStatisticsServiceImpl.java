package com.orange.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.entity.po.AkOperatorState;
import com.orange.entity.po.AkOperatorStateStatistics;
import com.orange.entity.vo.akoperator.OperatorStatisticsVO;
import com.orange.mapper.AkOperatorStateMapper;
import com.orange.mapper.AkOperatorStateStatisticsMapper;
import com.orange.service.AkOperatorStateStatisticsService;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 干员养成数据统计服务实现
 *
 * <p>执行流程：拉取干员实装时间数据 → 按主键键集分页全表扫描 ak_operator_state，边扫边累加
 * 各干员的取值分布，并顺带归集每个游戏账号的最近变更时间（MAX(updated_at)）→
 * 扫描结束后按实装时间计算每个干员的有效样本数。</p>
 *
 * <p>内存控制：每批只保留本批记录，累加完成后即可回收，因此批大小决定每批的堆占用峰值
 * （见 {@link #BATCH_SIZE}）；常驻内存为干员分布表（干员数 × 取值档位，用基本类型数组计数）
 * 与「账号 → 最近变更时间」映射（约 100 字节/账号）。账号数是十万量级、干员记录数是数千万量级，
 * 该映射只占十几 MB，而再发一次聚合查询要把数千万行重新读一遍，缓冲池装不下整表时全是磁盘 I/O，
 * 所以账号时间选择随明细扫描顺带归集，而不是交给数据库分组聚合。</p>
 *
 * <p>样本口径：账号时间取 ak_operator_state 的 MAX(updated_at)，即该账号干员数据最近一次
 * 发生变化的时间，不早于干员实装时间即计为有效样本。这样保证被计入的账号在干员明细里确实有
 * 数据，不含「只有绑定关系、却没有干员数据」的空账号；代价是「重复上传但属性没变化」的账号
 * 时间不推进，会被判为未达标，样本数偏保守。</p>
 *
 * <p>一致性说明：全表扫描不加事务，每批是一次独立查询。统计期间新写入的数据可能落在已扫描
 * 区间之外，属于离线跑批可接受的口径；若开启只读事务，长事务会长时间持有 MVCC 快照，
 * 在低配服务器上反而更危险。</p>
 *
 * <p>扫描必须留在事务外还有一层原因：MyBatis 默认 SESSION 级本地缓存会缓存同一会话内
 * 每条 SELECT 的结果，扫描若与其它写操作共用一个事务会话，循环里没有写操作去清缓存，
 * 已扫过的每一批都会一直留在堆上，堆占用随扫描行数线性增长直至 OOM。</p>
 *
 * <p>落库与查询：{@link #refreshOperatorStatistics()} 在统计完成后用一个只包住落库的小事务
 * 整表替换结果表（清空 + 逐条插入），读方要么看到旧快照、要么看到新快照；
 * {@link #getOperatorStatistics()} 只读结果表，供在线接口使用，不会触发全表扫描。手动触发走
 * {@link #triggerOperatorStatisticsRefresh()}，立即返回并在后台跑一轮，同一时刻只允许一轮。</p>
 *
 * @author UserCenter
 */
@Service
public class AkOperatorStateStatisticsServiceImpl implements AkOperatorStateStatisticsService {

    /** 外部实装时间数据的时间格式，如 2026/09/04 12:00:00 */
    private static final DateTimeFormatter RELEASE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    /** 扫描进度日志的输出间隔（每多少批打印一次，100 批即 100 万条） */
    private static final int PROGRESS_LOG_INTERVAL_BATCHES = 100;

    /**
     * 每批读取的干员记录数
     *
     * <p>取 1 万而不是更大值：JDBC 驱动会先把整批结果集缓冲在客户端，再叠加 MyBatis 映射出的实体，
     * 每批的堆占用大致与行数成正比，1 万行对应几 MB，是 4G 内存服务器上更稳妥的量级。
     * 批次调小只增加主键范围查询的次数，单次查询本身很轻。</p>
     */
    private static final int BATCH_SIZE = 10_000;

    /** 单次统计的最大批次数：5000 × 1 万 = 5000 万条，足以覆盖当前约 4000 万条数据 */
    private static final int MAX_BATCH_ROUNDS = 5_000;

    /** 干员实装时间数据地址 */
    private static final String RELEASE_TIME_URL =
            "https://ark.yituliu.cn/json/operator_update_time.json";

    /** 实装时间数据连接超时（毫秒） */
    private static final int RELEASE_TIME_CONNECT_TIMEOUT_MS = 5000;

    /** 实装时间数据读取超时（毫秒） */
    private static final int RELEASE_TIME_READ_TIMEOUT_MS = 15000;

    private final AkOperatorStateMapper operatorMapper;
    private final AkOperatorStateStatisticsMapper statisticsMapper;
    private final ObjectMapper objectMapper;
    private final TaskExecutor taskExecutor;

    /** 落库专用事务模板：清空结果表 + 逐条插入必须原子，而扫描阶段必须留在事务外 */
    private final TransactionTemplate transactionTemplate;

    /** 统计中标志：手动触发时用 CAS 抢占，保证同一时刻只有一轮全表扫描在跑 */
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    /**
     * 构造器注入依赖
     *
     * @param operatorMapper     干员数据 Mapper，用于取每个账号最近一次数据变更时间并分批扫描明细
     * @param statisticsMapper   统计结果 Mapper
     * @param objectMapper       JSON 解析器，用于解析实装时间数据
     * @param taskExecutor       后台任务执行器，手动触发时把统计提交到独立线程，避免阻塞请求线程
     * @param transactionManager 事务管理器，用于构造仅包裹落库动作的事务模板
     */
    public AkOperatorStateStatisticsServiceImpl(AkOperatorStateMapper operatorMapper,
                                               AkOperatorStateStatisticsMapper statisticsMapper,
                                               ObjectMapper objectMapper,
                                               TaskExecutor taskExecutor,
                                               PlatformTransactionManager transactionManager) {
        this.operatorMapper = operatorMapper;
        this.statisticsMapper = statisticsMapper;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 全量统计所有干员的养成数据分布
     *
     * @return 按干员编码升序排列的统计结果，无数据时返回空列表
     */
    @Override
    public List<OperatorStatisticsVO> collectOperatorStatistics() {
        long totalStartNanos = System.nanoTime();

        long releaseTimeStartNanos = System.nanoTime();
        Map<String, LocalDateTime> releaseTimes = loadOperatorReleaseTimes();
        long releaseTimeMillis = elapsedMillis(releaseTimeStartNanos);

        // 账号 → 最近一次数据变更时间，扫描明细时顺带归集；账号数远小于记录数，映射只占十几 MB
        Map<String, LocalDateTime> latestUpdateByAkUid = new HashMap<>();
        // 干员编码 → 累加器，用 TreeMap 保证输出顺序稳定
        Map<String, OperatorAggregate> aggregates = new TreeMap<>();

        long lastId = 0L;
        long scannedRows = 0L;
        boolean exhausted = false;
        // 拆开统计 DB 往返与内存累加的耗时，便于定位瓶颈在哪一侧
        long queryNanos = 0L;
        long accumulateNanos = 0L;
        for (int batchIndex = 1; batchIndex <= MAX_BATCH_ROUNDS; batchIndex++) {
            long queryStartNanos = System.nanoTime();
            List<AkOperatorState> batch = operatorMapper.selectStatisticsBatch(lastId, BATCH_SIZE);
            queryNanos += System.nanoTime() - queryStartNanos;
            if (batch.isEmpty()) {
                exhausted = true;
                break;
            }
            long accumulateStartNanos = System.nanoTime();
            for (AkOperatorState row : batch) {
                accumulate(aggregates, latestUpdateByAkUid, row);
            }
            accumulateNanos += System.nanoTime() - accumulateStartNanos;
            scannedRows += batch.size();
            lastId = batch.get(batch.size() - 1).getId();
            if (batchIndex % PROGRESS_LOG_INTERVAL_BATCHES == 0) {
                LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "干员数据统计进度：批次 {}/{}，已扫描 {} 条，累计干员 {} 个，"
                                + "DB 查询 {} ms、内存累加 {} ms，总耗时 {} ms",
                        batchIndex, MAX_BATCH_ROUNDS, scannedRows, aggregates.size(),
                        nanosToMillis(queryNanos), nanosToMillis(accumulateNanos), elapsedMillis(totalStartNanos));
            }
            if (batch.size() < BATCH_SIZE) {
                exhausted = true;
                break;
            }
        }
        if (!exhausted) {
            LogUtil.warn(AkOperatorStateStatisticsServiceImpl.class, "已达到最大批次轮数 {}（最多 {} 条），本次统计未覆盖全表，结果可能不完整",
                    MAX_BATCH_ROUNDS, (long) MAX_BATCH_ROUNDS * BATCH_SIZE);
        }
        LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "干员数据扫描完成：共 {} 条记录，{} 个干员，{} 个游戏账号",
                scannedRows, aggregates.size(), latestUpdateByAkUid.size());

        long accountTimeStartNanos = System.nanoTime();
        long[] accountLatestUpdateTimes = toSortedEpochMillis(latestUpdateByAkUid);
        long accountTimeMillis = elapsedMillis(accountTimeStartNanos);

        long buildStartNanos = System.nanoTime();
        List<OperatorStatisticsVO> result = buildResult(aggregates, accountLatestUpdateTimes, releaseTimes);
        long buildMillis = elapsedMillis(buildStartNanos);

        LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "干员数据统计各阶段耗时（ms）：实装时间加载 {}，账号时间归集 {}，DB 查询 {}，内存累加 {}，"
                        + "结果汇总 {}，总耗时 {}",
                releaseTimeMillis, accountTimeMillis, nanosToMillis(queryNanos), nanosToMillis(accumulateNanos),
                buildMillis, elapsedMillis(totalStartNanos));
        return result;
    }

    /**
     * 重新统计干员数据，并整表替换统计结果表
     *
     * <p>落库用 DELETE 而不是 TRUNCATE：TRUNCATE 属 DDL 会隐式提交，无法与插入同处一个事务，
     * 读方就会看到空表窗口。本表只有几百行，逐条插入的代价可忽略。</p>
     *
     * <p>整个方法刻意不加 {@code @Transactional}：事务只包住最后的「清空 + 逐条插入」。
     * 若把全表扫描也纳入事务，MyBatis 会在同一个会话里执行所有分批查询，而 MyBatis 默认
     * SESSION 级本地缓存会缓存每条 SELECT 的结果、且循环中没有任何写操作去清理它，
     * 堆占用将随扫描行数线性增长，扫到几百万行就会 OOM。</p>
     *
     * @return 本轮写入的干员数量
     */
    @Override
    public int refreshOperatorStatistics() {
        long totalStartNanos = System.nanoTime();

        List<OperatorStatisticsVO> statistics = collectOperatorStatistics();
        LocalDateTime statisticsTime = LocalDateTime.now();
        long collectMillis = elapsedMillis(totalStartNanos);

        long persistStartNanos = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> {
            statisticsMapper.deleteAll();
            for (OperatorStatisticsVO vo : statistics) {
                statisticsMapper.insert(toEntity(vo, statisticsTime));
            }
        });
        LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "干员数据统计结果已落库：{} 个干员，统计时间 {}，统计耗时 {} ms，落库耗时 {} ms，合计 {} ms",
                statistics.size(), statisticsTime, collectMillis, elapsedMillis(persistStartNanos),
                elapsedMillis(totalStartNanos));
        return statistics.size();
    }

    /**
     * 异步触发一轮干员数据统计，供手动触发接口与定时调度使用
     *
     * <p>先在调用线程用 CAS 抢占统计中标志：抢不到说明已有一轮在跑，直接返回 false，
     * 避免两轮全表扫描同时进行并互相覆盖结果；抢占成功则立即返回，真正的统计提交到
     * 后台线程执行。</p>
     *
     * @return true 表示本次已成功触发；false 表示已有一轮统计在执行，本次未触发
     */
    @Override
    public boolean triggerOperatorStatisticsRefresh() {
        if (!refreshing.compareAndSet(false, true)) {
            LogUtil.warn(AkOperatorStateStatisticsServiceImpl.class, "干员数据统计已在进行中，本次触发被忽略");
            return false;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    refreshOperatorStatistics();
                } catch (Exception e) {
                    LogUtil.error(AkOperatorStateStatisticsServiceImpl.class, "手动触发的干员数据统计执行失败", e);
                } finally {
                    refreshing.set(false);
                }
            });
        } catch (RuntimeException e) {
            // 提交失败必须立刻释放标志，否则后续触发会被永久挡住
            refreshing.set(false);
            throw e;
        }
        LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "干员数据统计已触发，将在后台执行");
        return true;
    }

    /**
     * 读取最近一轮干员统计结果
     *
     * @return 按干员编码升序排列的统计结果，尚未跑过统计时返回空列表
     */
    @Override
    public List<OperatorStatisticsVO> getOperatorStatistics() {
        List<AkOperatorStateStatistics> rows = statisticsMapper.selectList(null);
        List<OperatorStatisticsVO> result = new ArrayList<>(rows.size());
        for (AkOperatorStateStatistics row : rows) {
            result.add(toVO(row));
        }
        result.sort(Comparator.comparing(OperatorStatisticsVO::getCharId));
        return result;
    }

    /**
     * 拉取并解析干员实装时间数据
     *
     * @return 干员编码 → 实装时间；拉取失败或解析失败时抛出业务异常
     */
    private Map<String, LocalDateTime> loadOperatorReleaseTimes() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(RELEASE_TIME_CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(RELEASE_TIME_READ_TIMEOUT_MS);

        String body;
        try {
            body = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build()
                    .get()
                    .uri(RELEASE_TIME_URL)
                    .retrieve()
                    .body(String.class);
        } catch (RuntimeException e) {
            LogUtil.error(AkOperatorStateStatisticsServiceImpl.class, "干员实装时间数据获取失败：{}", RELEASE_TIME_URL, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "干员实装时间数据获取失败");
        }
        if (body == null || body.isBlank()) {
            LogUtil.error(AkOperatorStateStatisticsServiceImpl.class, "干员实装时间数据为空：{}", RELEASE_TIME_URL);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "干员实装时间数据为空");
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            Map<String, LocalDateTime> releaseTimes = new HashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode updateTime = entry.getValue().path("updateTime");
                if (updateTime.isTextual()) {
                    releaseTimes.put(entry.getKey(),
                            LocalDateTime.parse(updateTime.asText(), RELEASE_TIME_FORMATTER));
                }
            });
            LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "干员实装时间数据加载完成：共 {} 个干员", releaseTimes.size());
            return releaseTimes;
        } catch (Exception e) {
            LogUtil.error(AkOperatorStateStatisticsServiceImpl.class, "干员实装时间数据解析失败：{}", RELEASE_TIME_URL, e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "干员实装时间数据解析失败");
        }
    }

    /**
     * 把「账号 → 最近变更时间」映射转成按时间升序排列的基本类型数组
     *
     * <p>归集阶段已经拿到每个账号的 MAX(updated_at)，这里只保留时间值本身（每账号 8 字节），
     * 丢掉账号标识。排好序后样本数可以用二分查找，复杂度从「干员数 × 账号数」降到
     * 「干员数 × log 账号数」。</p>
     *
     * @param latestUpdateByAkUid 账号 → 最近一次数据变更时间
     * @return 按时间升序排列的账号最近变更时间（epoch 毫秒），无数据时返回空数组
     */
    private static long[] toSortedEpochMillis(Map<String, LocalDateTime> latestUpdateByAkUid) {
        long[] times = new long[latestUpdateByAkUid.size()];
        int index = 0;
        for (LocalDateTime latestUpdateTime : latestUpdateByAkUid.values()) {
            times[index++] = toEpochMilli(latestUpdateTime);
        }
        Arrays.sort(times);
        LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "游戏账号最近变更时间归集完成：共 {} 个账号", times.length);
        return times;
    }

    /**
     * 累加单条干员记录：干员取值分布计数 + 顺带归集该账号的最近变更时间
     *
     * @param aggregates          干员编码 → 累加器
     * @param latestUpdateByAkUid 账号 → 最近一次数据变更时间，按较大值就地更新
     * @param row                 单条干员记录
     */
    private static void accumulate(Map<String, OperatorAggregate> aggregates,
                                   Map<String, LocalDateTime> latestUpdateByAkUid,
                                   AkOperatorState row) {
        OperatorAggregate aggregate = aggregates.computeIfAbsent(row.getOperatorId(),
                key -> new OperatorAggregate());
        aggregate.own++;
        aggregate.elite.increment(row.getEvolvePhase());
        aggregate.skill1.increment(row.getSkill1());
        aggregate.skill2.increment(row.getSkill2());
        aggregate.skill3.increment(row.getSkill3());
        aggregate.modA.increment(row.getEquipA());
        aggregate.modX.increment(row.getEquipX());
        aggregate.modY.increment(row.getEquipY());
        aggregate.modD.increment(row.getEquipD());
        aggregate.modB.increment(row.getEquipB());

        latestUpdateByAkUid.merge(row.getAkUid(), row.getUpdatedAt(),
                (existing, candidate) -> candidate.isAfter(existing) ? candidate : existing);
    }

    /**
     * 汇总最终统计结果，并逐干员自检分布数据
     *
     * @param aggregates               干员编码 → 累加器
     * @param accountLatestUpdateTimes 按时间升序排列的账号最近变更时间（epoch 毫秒）
     * @param releaseTimes             干员编码 → 实装时间
     * @return 按干员编码升序排列的统计结果
     */
    private static List<OperatorStatisticsVO> buildResult(Map<String, OperatorAggregate> aggregates,
                                                          long[] accountLatestUpdateTimes,
                                                          Map<String, LocalDateTime> releaseTimes) {
        List<OperatorStatisticsVO> result = new ArrayList<>(aggregates.size());
        int unknownReleaseCount = 0;
        // 每个干员只需一次二分查找，单列出来确认耗时
        long sampleCountNanos = 0L;
        for (Map.Entry<String, OperatorAggregate> entry : aggregates.entrySet()) {
            OperatorAggregate aggregate = entry.getValue();
            OperatorStatisticsVO vo = new OperatorStatisticsVO();
            vo.setCharId(entry.getKey());
            vo.setOwn(aggregate.own);
            vo.setElite(aggregate.elite.toResult());
            vo.setSkill1(aggregate.skill1.toResult());
            vo.setSkill2(aggregate.skill2.toResult());
            vo.setSkill3(aggregate.skill3.toResult());
            vo.setModA(aggregate.modA.toResult());
            vo.setModX(aggregate.modX.toResult());
            vo.setModY(aggregate.modY.toResult());
            vo.setModD(aggregate.modD.toResult());
            vo.setModB(aggregate.modB.toResult());
            verifyDistributionSums(vo);

            LocalDateTime releaseTime = releaseTimes.get(entry.getKey());
            if (releaseTime == null) {
                unknownReleaseCount++;
                vo.setSampleSize(0L);
            } else {
                long sampleStartNanos = System.nanoTime();
                vo.setSampleSize(countSamples(accountLatestUpdateTimes, releaseTime));
                sampleCountNanos += System.nanoTime() - sampleStartNanos;
            }
            result.add(vo);
        }
        if (unknownReleaseCount > 0) {
            LogUtil.warn(AkOperatorStateStatisticsServiceImpl.class, "有 {} 个干员未匹配到实装时间，其 sampleSize 记为 0", unknownReleaseCount);
        }
        LogUtil.info(AkOperatorStateStatisticsServiceImpl.class, "有效样本数统计耗时 {} ms（干员 {} 个 × 账号 {} 个）",
                nanosToMillis(sampleCountNanos), aggregates.size(), accountLatestUpdateTimes.length);
        return result;
    }

    /**
     * 校验各取值分布之和是否都等于记录总数
     *
     * <p>{@code own} 与各分布都由本类自行累加，正常情况下必须完全一致；不一致说明累加逻辑有缺陷，
     * 此处直接抛异常中断，避免错误结果被返回或落库。</p>
     *
     * @param vo 单个干员的统计结果
     */
    private static void verifyDistributionSums(OperatorStatisticsVO vo) {
        verifyDistributionSum(vo, "elite", vo.getElite());
        verifyDistributionSum(vo, "skill1", vo.getSkill1());
        verifyDistributionSum(vo, "skill2", vo.getSkill2());
        verifyDistributionSum(vo, "skill3", vo.getSkill3());
        verifyDistributionSum(vo, "modA", vo.getModA());
        verifyDistributionSum(vo, "modX", vo.getModX());
        verifyDistributionSum(vo, "modY", vo.getModY());
        verifyDistributionSum(vo, "modD", vo.getModD());
        verifyDistributionSum(vo, "modB", vo.getModB());
    }

    /**
     * 校验单个取值分布之和是否等于记录总数
     *
     * @param vo           单个干员的统计结果
     * @param dimension    维度名称，仅用于异常信息
     * @param distribution 取值分布
     */
    private static void verifyDistributionSum(OperatorStatisticsVO vo, String dimension,
                                              Map<String, Long> distribution) {
        long sum = 0L;
        for (Long count : distribution.values()) {
            sum += count;
        }
        if (sum != vo.getOwn()) {
            throw new IllegalStateException(String.format(
                    "干员 %s 的 %s 分布之和 %d 与记录总数 %d 不一致",
                    vo.getCharId(), dimension, sum, vo.getOwn()));
        }
    }

    /**
     * 统计最近一次数据变更时间不早于实装时间的游戏账号数
     *
     * <p>账号时间已按升序排好，二分找到第一个不早于实装时间的位置，其后的账号数即为有效样本数。</p>
     *
     * @param sortedLatestUpdateTimes 按时间升序排列的账号最近变更时间（epoch 毫秒）
     * @param releaseTime             干员实装时间
     * @return 有效样本数
     */
    private static long countSamples(long[] sortedLatestUpdateTimes,
                                    LocalDateTime releaseTime) {
        int firstQualifiedIndex = lowerBound(sortedLatestUpdateTimes, toEpochMilli(releaseTime));
        return sortedLatestUpdateTimes.length - firstQualifiedIndex;
    }

    /**
     * 二分查找第一个大于等于目标值的位置
     *
     * @param sortedValues 升序排列的数组
     * @param target       目标值
     * @return 第一个大于等于目标值的下标；全部小于目标值时返回数组长度
     */
    private static int lowerBound(long[] sortedValues, long target) {
        int low = 0;
        int high = sortedValues.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sortedValues[mid] < target) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * 把本地时间转换为 epoch 毫秒
     *
     * <p>账号变更时间与干员实装时间都按系统默认时区换算，两侧口径一致，比较结果不受时区影响。</p>
     *
     * @param dateTime 本地时间
     * @return epoch 毫秒
     */
    private static long toEpochMilli(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
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
     * @param vo             单个干员的统计结果
     * @param statisticsTime 本轮统计的完成时间
     * @return 待落库实体
     */
    private static AkOperatorStateStatistics toEntity(OperatorStatisticsVO vo,
                                                      LocalDateTime statisticsTime) {
        AkOperatorStateStatistics entity = new AkOperatorStateStatistics();
        entity.setCharId(vo.getCharId());
        entity.setOwn(vo.getOwn());
        entity.setSampleSize(vo.getSampleSize());
        entity.setElite(vo.getElite());
        entity.setSkill1(vo.getSkill1());
        entity.setSkill2(vo.getSkill2());
        entity.setSkill3(vo.getSkill3());
        entity.setModA(vo.getModA());
        entity.setModX(vo.getModX());
        entity.setModY(vo.getModY());
        entity.setModD(vo.getModD());
        entity.setModB(vo.getModB());
        entity.setStatisticsTime(statisticsTime);
        return entity;
    }

    /**
     * 实体转统计结果 VO
     *
     * @param entity 统计结果实体
     * @return 统计结果 VO
     */
    private static OperatorStatisticsVO toVO(AkOperatorStateStatistics entity) {
        OperatorStatisticsVO vo = new OperatorStatisticsVO();
        vo.setCharId(entity.getCharId());
        vo.setOwn(entity.getOwn());
        vo.setSampleSize(entity.getSampleSize());
        vo.setElite(entity.getElite());
        vo.setSkill1(entity.getSkill1());
        vo.setSkill2(entity.getSkill2());
        vo.setSkill3(entity.getSkill3());
        vo.setModA(entity.getModA());
        vo.setModX(entity.getModX());
        vo.setModY(entity.getModY());
        vo.setModD(entity.getModD());
        vo.setModB(entity.getModB());
        return vo;
    }

    /**
     * 单个干员的统计累加器：各维度按取值计数
     */
    private static final class OperatorAggregate {

        /** 拥有该干员的记录总数 */
        private long own;

        /** 精英化阶段分布 */
        private final Histogram elite = new Histogram();

        /** 技能 1 分布 */
        private final Histogram skill1 = new Histogram();

        /** 技能 2 分布 */
        private final Histogram skill2 = new Histogram();

        /** 技能 3 分布 */
        private final Histogram skill3 = new Histogram();

        /** A 模组分布 */
        private final Histogram modA = new Histogram();

        /** X 模组分布 */
        private final Histogram modX = new Histogram();

        /** Y 模组分布 */
        private final Histogram modY = new Histogram();

        /** D 模组分布 */
        private final Histogram modD = new Histogram();

        /** B 模组分布 */
        private final Histogram modB = new Histogram();
    }

    /**
     * 单维度取值直方图：下标即属性取值，元素为该取值的记录数
     *
     * <p>这些维度都来自 {@code TINYINT UNSIGNED NOT NULL} 列，取值必然是非负整数且实际只有个位数档位，
     * 因此用基本类型数组计数即可。相比 {@code Map<Integer, Long>} 的写法，每次累加不再装箱 Long，
     * 也不再做哈希查找——4000 万行 × 9 个维度是数亿次累加，装箱产生的垃圾量足以主导整个跑批的 GC 开销。</p>
     */
    private static final class Histogram {

        /** 下标 i 的元素表示取值 i 的记录数，长度按实际出现的最大取值增长 */
        private long[] counts = new long[0];

        /**
         * 累加一次取值
         *
         * @param value 属性取值，非负整数
         */
        private void increment(int value) {
            if (value >= counts.length) {
                counts = Arrays.copyOf(counts, value + 1);
            }
            counts[value]++;
        }

        /**
         * 转换为按取值升序排列的字符串 key 分布，未出现过的取值不输出
         *
         * @return 取值（字符串）→ 记录数
         */
        private Map<String, Long> toResult() {
            Map<String, Long> result = new LinkedHashMap<>(counts.length);
            for (int value = 0; value < counts.length; value++) {
                if (counts[value] > 0) {
                    result.put(String.valueOf(value), counts[value]);
                }
            }
            return result;
        }
    }
}
