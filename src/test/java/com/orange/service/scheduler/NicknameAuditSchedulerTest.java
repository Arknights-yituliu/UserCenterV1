package com.orange.service.scheduler;

import com.orange.service.NicknameAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NicknameAuditSchedulerTest {

    @Test
    void schedulesEveryFiveMinutesWhenCiIsEnabled() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        contextRunner(taskScheduler).withPropertyValues("tencent.ci.enabled=true").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(NicknameAuditScheduler.class);
            ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
            verify(taskScheduler).schedule(any(Runnable.class), trigger.capture());
            assertThat(trigger.getValue()).isInstanceOf(CronTrigger.class);
            SimpleTriggerContext clock = new SimpleTriggerContext(Clock.fixed(
                    Instant.parse("2026-11-14T18:01:00Z"), ZoneOffset.UTC));
            Instant firstExecution = trigger.getValue().nextExecution(clock);
            assertThat(firstExecution).isEqualTo(Instant.parse("2026-11-14T18:05:00Z"));
            clock.update(firstExecution, firstExecution, firstExecution);
            assertThat(trigger.getValue().nextExecution(clock)).isEqualTo(Instant.parse("2026-11-14T18:10:00Z"));
        });
    }

    @Test
    void doesNotRegisterSchedulerWhenCiIsDisabled() {
        contextRunner(mock(TaskScheduler.class)).run(context -> {
            assertThat(context).hasNotFailed().doesNotHaveBean(NicknameAuditScheduler.class);
        });
    }

    private ApplicationContextRunner contextRunner(TaskScheduler scheduler) {
        return new ApplicationContextRunner()
                .withUserConfiguration(SchedulingConfig.class, NicknameAuditScheduler.class)
                .withBean(NicknameAuditService.class, () -> mock(NicknameAuditService.class))
                .withBean(TaskScheduler.class, () -> scheduler);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfig {
    }
}
