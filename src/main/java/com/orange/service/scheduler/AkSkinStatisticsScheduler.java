package com.orange.service.scheduler;

import com.orange.service.AkSkinStatisticsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 皮肤持有数据统计的定时调度器
 *
 * <p>每小时的第 10 分钟触发一轮全量统计，直接复用
 * {@link AkSkinStatisticsService#triggerSkinStatisticsRefresh()}：调用即返回，
 * 真正的全表扫描在后台线程执行；同一时刻只允许一轮，已有任务在执行时本次触发被忽略。</p>
 *
 * <p>用 cron 而不是 fixedDelay：统计结果按整点对齐，与干员统计（每小时 05 分）错开 5 分钟，
 * 避免两次全表扫描同时起跑抢占数据库与内存。若某轮扫描超过一小时，下一次触发会被服务内的
 * 并发保护忽略，不会叠加。应用重启后不会立刻扫描，只有下一个整点 10 分才会触发。</p>
 *
 * <p>皮肤数据的变化频率远低于干员养成数据，若线上压力偏大，只需调整
 * {@code SKIN_STATISTICS_CRON} 的小时位（例如改成每两小时一次），无需改动统计逻辑。</p>
 *
 * @author UserCenter
 */
@Component
public class AkSkinStatisticsScheduler {

    /** 每小时第 10 分钟触发：秒 分 时 日 月 周 */
    private static final String SKIN_STATISTICS_CRON = "0 10 * * * ?";

    private final AkSkinStatisticsService statisticsService;

    /**
     * 构造器注入统计服务
     *
     * @param statisticsService 皮肤数据统计服务
     */
    public AkSkinStatisticsScheduler(AkSkinStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 每小时第 10 分钟触发一轮皮肤数据统计
     */
    @Scheduled(cron = SKIN_STATISTICS_CRON)
    public void scheduleSkinStatisticsRefresh() {
        statisticsService.triggerSkinStatisticsRefresh();
    }
}
