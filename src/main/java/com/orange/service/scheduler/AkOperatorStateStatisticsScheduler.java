package com.orange.service.scheduler;

import com.orange.service.AkOperatorStateStatisticsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 干员养成数据统计的定时调度器
 *
 * <p>每小时的第 5 分钟触发一轮全量统计，直接复用
 * {@link AkOperatorStateStatisticsService#triggerOperatorStatisticsRefresh()}：调用即返回，
 * 真正的全表扫描在后台线程执行；同一时刻只允许一轮，已有任务在执行时本次触发被忽略。</p>
 *
 * <p>用 cron 而不是 fixedDelay：统计结果按整点对齐，前端可以固定按「每小时 05 分后的数据」
 * 展示，不必关心上一次跑批何时结束、下一次何时开始。若某轮扫描超过一小时，下一次触发会被
 * 服务内的并发保护忽略，不会出现两轮全表扫描叠加。</p>
 *
 * <p>与皮肤统计（每小时 10 分）错开 5 分钟，避免两次全表扫描同时起跑抢占数据库与内存。
 * 应用重启后不会立刻扫描，只有下一个整点 05 分才会触发。</p>
 *
 * @author UserCenter
 */
@Component
public class AkOperatorStateStatisticsScheduler {

    /** 每小时第 5 分钟触发：秒 分 时 日 月 周 */
    private static final String OPERATOR_STATISTICS_CRON = "0 5 * * * ?";

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
     * 每小时第 5 分钟触发一轮干员数据统计
     */
    @Scheduled(cron = OPERATOR_STATISTICS_CRON)
    public void scheduleOperatorStatisticsRefresh() {
        statisticsService.triggerOperatorStatisticsRefresh();
    }
}
