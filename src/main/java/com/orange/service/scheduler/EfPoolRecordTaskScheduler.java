package com.orange.service.scheduler;

import com.orange.service.EfPoolRecordTaskService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 终末地寻访记录导入任务的定时调度器
 *
 * <p>两个任务节奏不同，因此放在同一个调度类里用不同 cron 表达，业务实现仍留在
 * {@link EfPoolRecordTaskService}：调度类只负责“什么时候触发”，不掺业务逻辑。</p>
 *
 * <p>注意：两者共用 Spring 默认的调度线程池。若导入任务单次执行较慢，会推迟 token 覆盖
 * 与其它调度任务的触发时机，必要时可通过 {@code spring.task.scheduling.pool.size} 扩容。</p>
 *
 * @author UserCenter
 */
@Component
public class EfPoolRecordTaskScheduler {

    private final EfPoolRecordTaskService taskService;

    /**
     * 构造器注入任务服务
     *
     * @param taskService 终末地寻访记录导入任务服务
     */
    public EfPoolRecordTaskScheduler(EfPoolRecordTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 每分钟把 24 小时前、15 分钟前之间的已完成任务 token 覆盖为占位值，避免 hgToken 长期留存
     */
    @Scheduled(cron = "0 * * * * ?")
    public void maskExpiredTokens() {
        taskService.maskExpiredToken();
    }

    /**
     * 每秒取一条最早创建且未开始的任务执行导入
     *
     * <p>超过有效窗口仍未开始的任务由服务侧标记为已开始、不再执行，避免堆积旧任务。</p>
     */
    @Scheduled(cron = "* * * * * ?")
    public void loadPendingPoolRecordTask() {
        taskService.loadingPoolRecord();
    }
}
