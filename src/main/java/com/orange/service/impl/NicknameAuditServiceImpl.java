package com.orange.service.impl;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.entity.dto.audit.TextAuditResult;
import com.orange.entity.po.UserInfo;
import com.orange.entity.po.UserNicknameAudit;
import com.orange.mapper.UserInfoMapper;
import com.orange.mapper.UserNicknameAuditMapper;
import com.orange.service.NicknameAuditService;
import com.orange.service.TextAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单实例昵称回溯审核，每次一个注册日；不包长事务，数据库失败时保留当日游标。 */
@Service
public class NicknameAuditServiceImpl implements NicknameAuditService {

    static final LocalDate START_DATE = LocalDate.of(2026, 11, 15);
    static final LocalDate EARLIEST_REGISTRATION_DATE = LocalDate.of(2024, 1, 1);
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static final int PAGE_SIZE = 200;

    private final UserInfoMapper userMapper;
    private final UserNicknameAuditMapper auditMapper;
    private final TextAuditService textAuditService;
    private final NicknameAuditProgressStore progress;
    private final TaskExecutor taskExecutor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    @Autowired
    public NicknameAuditServiceImpl(UserInfoMapper userMapper, UserNicknameAuditMapper auditMapper,
                                    TextAuditService textAuditService, NicknameAuditProgressStore progress,
                                    TaskExecutor taskExecutor) {
        this(userMapper, auditMapper, textAuditService, progress, taskExecutor, Clock.system(ZONE));
    }

    NicknameAuditServiceImpl(UserInfoMapper userMapper, UserNicknameAuditMapper auditMapper,
                            TextAuditService textAuditService, NicknameAuditProgressStore progress,
                            TaskExecutor taskExecutor, Clock clock) {
        this.userMapper = userMapper;
        this.auditMapper = auditMapper;
        this.textAuditService = textAuditService;
        this.progress = progress;
        this.taskExecutor = taskExecutor;
        this.clock = clock.withZone(ZONE);
    }

    @Override
    public boolean triggerHistoricalAudit() {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        try {
            taskExecutor.execute(() -> {
                try {
                    executeNextRegistrationDay();
                } catch (Exception e) {
                    LogUtil.error(NicknameAuditServiceImpl.class, "昵称回溯审核未完成，保留当前注册日进度", e);
                } finally {
                    running.set(false);
                }
            });
            return true;
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
    }

    @Override
    public int auditNextRegistrationDay() {
        if (!running.compareAndSet(false, true)) {
            return 0;
        }
        try {
            return executeNextRegistrationDay();
        } finally {
            running.set(false);
        }
    }

    private int executeNextRegistrationDay() {
        LocalDate executionDate = LocalDate.now(clock);
        if (executionDate.isBefore(START_DATE)) {
            return 0;
        }
        long executionSlot = Math.floorDiv(clock.instant().getEpochSecond(), 5 * 60);
        Long lastRunSlot = progress.lastRunSlot();
        if (lastRunSlot != null && lastRunSlot >= executionSlot) {
            return 0;
        }
        LocalDate targetDate = progress.nextRegistrationDate();
        if (targetDate == null) {
            targetDate = START_DATE.minusDays(1);
        }
        if (!targetDate.isBefore(START_DATE)) {
            throw new IllegalStateException("昵称审核回溯游标必须早于 2026-11-15");
        }
        if (targetDate.isBefore(EARLIEST_REGISTRATION_DATE)) {
            return 0;
        }
        int inserted = auditRegistrationDay(targetDate);
        progress.completeDay(targetDate, executionSlot);
        LogUtil.info(NicknameAuditServiceImpl.class, "昵称回溯审核完成: registrationDate={}, inserted={}",
                targetDate, inserted);
        return inserted;
    }

    private int auditRegistrationDay(LocalDate targetDate) {
        Long afterUid = null;
        int inserted = 0;
        while (true) {
            List<UserInfo> users = userMapper.selectUnauditedNicknames(targetDate.atStartOfDay(),
                    targetDate.plusDays(1).atStartOfDay(), afterUid, PAGE_SIZE);
            if (users.isEmpty()) {
                return inserted;
            }
            for (UserInfo user : users) {
                UserNicknameAudit audit = auditNickname(user);
                try {
                    // 只捕获主键冲突；其他落库错误必须阻止日期推进。
                    if (auditMapper.insert(audit) != 1) {
                        throw new IllegalStateException("昵称审核结果未成功插入，停止推进日期");
                    }
                    inserted++;
                } catch (DuplicateKeyException ignored) {
                    // 已有记录保留原始结果，不执行更新。
                }
                afterUid = user.getUid();
            }
        }
    }

    private UserNicknameAudit auditNickname(UserInfo user) {
        UserNicknameAudit audit = new UserNicknameAudit();
        audit.setUid(user.getUid());
        audit.setNickname(user.getNickname());
        audit.setRegisterTime(user.getRegisterTime());
        try {
            TextAuditResult result = textAuditService.auditText(user.getNickname(), "nickname-audit-" + user.getUid());
            audit.setAuditResult(result.decision().name());
            audit.setLabel(result.label());
            audit.setJobId(result.jobId());
            audit.setRequestId(result.requestId());
        } catch (BusinessException e) {
            audit.setAuditResult("ERROR");
            audit.setErrorCode(e.getCode());
            audit.setErrorMessage(e.getCode() == ResultCode.PARAM_ERROR.getCode()
                    ? "昵称为空或不符合送审参数要求" : "数据万象文本审核调用失败");
        } catch (RuntimeException e) {
            audit.setAuditResult("ERROR");
            audit.setErrorCode(ResultCode.SYSTEM_ERROR.getCode());
            audit.setErrorMessage("昵称审核发生异常");
        }
        audit.setAuditTime(LocalDateTime.now(clock));
        return audit;
    }
}
