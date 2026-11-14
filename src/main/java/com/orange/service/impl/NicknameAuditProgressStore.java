package com.orange.service.impl;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/** 保存单实例任务的回溯进度，重启后继续执行；进度不设 TTL。 */
@Component
public class NicknameAuditProgressStore {

    private static final String NEXT_DATE_KEY = "uc:nickname-audit:next-registration-date";
    private static final String LAST_RUN_KEY = "uc:nickname-audit:last-run-slot";

    private final StringRedisTemplate redis;

    public NicknameAuditProgressStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public LocalDate nextRegistrationDate() {
        return readDate(NEXT_DATE_KEY);
    }

    public Long lastRunSlot() {
        String value = redis.opsForValue().get(LAST_RUN_KEY);
        return value == null ? null : Long.parseLong(value);
    }

    public void completeDay(LocalDate registrationDate, long executionSlot) {
        // MSET 一次保存日期与调度时段，避免进度只写入一半。
        redis.opsForValue().multiSet(Map.of(
                NEXT_DATE_KEY, registrationDate.minusDays(1).toString(),
                LAST_RUN_KEY, Long.toString(executionSlot)));
    }

    private LocalDate readDate(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? null : LocalDate.parse(value);
    }
}
