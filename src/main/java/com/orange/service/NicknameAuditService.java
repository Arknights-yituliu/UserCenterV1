package com.orange.service;

/** 单实例运行、按注册日逐日向历史回溯的昵称审核任务。 */
public interface NicknameAuditService {

    /** 异步触发下一天的回溯审核，同一进程已有任务运行时返回 false。 */
    boolean triggerHistoricalAudit();

    /**
     * 同步执行一轮，每个五分钟时段最多完成一个注册日；全部结果落库后才推进日期。
     * 从 2026-11-15 启用，首次目标为 2026-11-14，逐日回溯至 2024-01-01（含）。
     *
     * @return 本轮新插入的结果条数
     */
    int auditNextRegistrationDay();
}
