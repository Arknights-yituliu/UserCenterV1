package com.orange.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 干员养成数据统计的定时调度器
 *
 * <p>按固定间隔（30 分钟）触发一轮全量统计，直接复用
 * {@link AkOperatorStateStatisticsService#triggerOperatorStatisticsRefresh()}：调用即返回，
 * 真正的全表扫描在后台线程执行；同一时刻只允许一轮，已有任务在执行时本次触发被忽略。</p>
 *
 * <p>首次执行延迟一个间隔，避免应用每次重启都立刻发起一次全表扫描；需要立即得到新结果时，
 * 仍可调用 {@code GET /open/ak-operator-statistics/refresh} 手动触发。</p>
 *
 * @author UserCenter
 */
@Component
public class AkOperatorStateStatisticsScheduler {

    /** 统计刷新间隔：30 分钟（毫秒） */
    private static final long REFRESH_INTERVAL_MS = 30 * 60 * 1000L;

    private final AkOperatorStateStatisticsService statisticsService;

    /**
     * 构造器注入统计服务
     *
     * @param statisticsService 干员数据统计服务
     */
    public AkOperatorStateStatisticsScheduler(AkOperatorStateStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 按固定间隔触发一轮干员数据统计
     *
     * <p>间隔固定为 30 分钟；采用 fixedDelay 而非 fixedRate：以上一轮结束为起点重新计时，
     * 配合服务内的并发保护，避免两轮全表扫描叠加。首次执行同样延迟 30 分钟，避免应用每次重启
     * 都立刻发起一次全表扫描。</p>
     */
    @Scheduled(initialDelay = REFRESH_INTERVAL_MS, fixedDelay = REFRESH_INTERVAL_MS)
    public void scheduleOperatorStatisticsRefresh() {
        statisticsService.triggerOperatorStatisticsRefresh();
    }
}
