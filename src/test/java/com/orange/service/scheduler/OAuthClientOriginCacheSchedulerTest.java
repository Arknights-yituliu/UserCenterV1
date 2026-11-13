package com.orange.service.scheduler;

import com.orange.common.config.OAuthProperties;
import com.orange.service.OAuthClientOriginCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 验证 Spring 定时任务能解析 OAuthProperties 中的刷新周期。 */
class OAuthClientOriginCacheSchedulerTest {

    @Test
    void schedulesCacheRefreshUsingPropertiesWithoutYaml() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        new ApplicationContextRunner()
                .withUserConfiguration(SchedulingConfig.class, OAuthProperties.class,
                        OAuthClientOriginCacheScheduler.class)
                .withBean(OAuthClientOriginCache.class, () -> mock(OAuthClientOriginCache.class))
                .withBean(TaskScheduler.class, () -> taskScheduler)
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    verify(taskScheduler).scheduleWithFixedDelay(
                            any(Runnable.class), any(Instant.class), eq(Duration.ofSeconds(60)));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    static class SchedulingConfig {
    }
}
