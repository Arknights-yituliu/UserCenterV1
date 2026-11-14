package com.orange.service.scheduler;

import com.orange.service.NicknameAuditService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每五分钟触发一次昵称历史回溯；起止日期与游标由 Service 控制。 */
@Component
@ConditionalOnProperty(prefix = "tencent.ci", name = "enabled", havingValue = "true")
public class NicknameAuditScheduler {

    private final NicknameAuditService auditService;

    public NicknameAuditScheduler(NicknameAuditService auditService) {
        this.auditService = auditService;
    }

    @Scheduled(cron = "0 */5 * * * ?", zone = "Asia/Shanghai")
    public void auditHistoricalNicknames() {
        auditService.triggerHistoricalAudit();
    }
}
