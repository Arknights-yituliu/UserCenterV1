package com.orange.service.impl;

import com.orange.common.enums.ResultCode;
import com.orange.common.enums.TextAuditDecision;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.audit.TextAuditResult;
import com.orange.entity.po.UserInfo;
import com.orange.entity.po.UserNicknameAudit;
import com.orange.mapper.UserInfoMapper;
import com.orange.mapper.UserNicknameAuditMapper;
import com.orange.service.TextAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NicknameAuditServiceImplTest {

    private static final LocalDate FIRST_DATE = LocalDate.of(2026, 11, 14);
    private static final LocalDate EXECUTION_DATE = LocalDate.of(2026, 11, 15);

    @Mock private UserInfoMapper userMapper;
    @Mock private UserNicknameAuditMapper auditMapper;
    @Mock private TextAuditService textAuditService;
    @Mock private NicknameAuditProgressStore progress;
    @Mock private TaskExecutor executor;

    @BeforeEach
    void allowResultInsert() {
        lenient().when(auditMapper.insert(any(UserNicknameAudit.class))).thenReturn(1);
    }

    @Test
    void doesNothingBeforeStartDate() {
        assertThat(serviceOn(LocalDate.of(2026, 11, 14)).auditNextRegistrationDay()).isZero();
        verifyNoInteractions(progress, userMapper, auditMapper, textAuditService);
    }

    @Test
    void usesShanghaiDateAndPersistsPassReviewBlockAndFailures() {
        // 此时 UTC 仍为 11-14，但北京时间已经是 11-15 凌晨 2 点。
        NicknameAuditServiceImpl service = new NicknameAuditServiceImpl(userMapper, auditMapper,
                textAuditService, progress, executor, Clock.fixed(Instant.parse("2026-11-14T18:00:00Z"), ZoneOffset.UTC));
        firstPage(List.of(user(1), user(2), user(3), user(4)));
        when(textAuditService.auditText(anyString(), anyString()))
                .thenReturn(result(TextAuditDecision.PASS), result(TextAuditDecision.REVIEW), result(TextAuditDecision.BLOCK))
                .thenThrow(new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "raw provider detail"));

        assertThat(service.auditNextRegistrationDay()).isEqualTo(4);

        List<UserNicknameAudit> rows = savedRows(4);
        assertThat(rows).extracting(UserNicknameAudit::getAuditResult)
                .containsExactly("PASS", "REVIEW", "BLOCK", "ERROR");
        assertThat(rows).extracting(UserNicknameAudit::getUid).containsExactly(1L, 2L, 3L, 4L);
        assertThat(rows.get(0).getNickname()).isEqualTo("nickname-1");
        assertThat(rows.get(0).getRegisterTime()).isEqualTo(FIRST_DATE.atTime(12, 0));
        assertThat(rows.get(0).getAuditTime()).isEqualTo(EXECUTION_DATE.atTime(2, 0));
        assertThat(rows.get(0).getRequestId()).isEqualTo("request-123");
        assertThat(rows.get(3).getErrorCode()).isEqualTo(ResultCode.OUTER_API_INVOKE_ERROR.getCode());
        assertThat(rows.get(3).getErrorMessage()).doesNotContain("raw provider detail");
        verify(progress).completeDay(eq(FIRST_DATE), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void resumesPersistedHistoricalDateRatherThanUsingYesterday() {
        LocalDate historicalDate = LocalDate.of(2026, 10, 29);
        when(progress.nextRegistrationDate()).thenReturn(historicalDate);

        serviceOn(LocalDate.of(2026, 12, 1)).auditNextRegistrationDay();

        verify(userMapper).selectUnauditedNicknames(eq(historicalDate.atStartOfDay()),
                eq(historicalDate.plusDays(1).atStartOfDay()), isNull(), eq(200));
        verify(progress).completeDay(eq(historicalDate), eq(executionSlot(LocalDate.of(2026, 12, 1))));
    }

    @Test
    void keysetPaginationKeepsHalfOpenRegistrationDayBoundaries() {
        firstPage(List.of(user(1), user(2)));
        when(userMapper.selectUnauditedNicknames(any(), any(), eq(2L), anyInt())).thenReturn(List.of(user(3)));
        when(textAuditService.auditText(anyString(), anyString())).thenReturn(result(TextAuditDecision.PASS));

        assertThat(serviceOn(EXECUTION_DATE).auditNextRegistrationDay()).isEqualTo(3);

        verify(userMapper).selectUnauditedNicknames(eq(FIRST_DATE.atStartOfDay()),
                eq(EXECUTION_DATE.atStartOfDay()), isNull(), eq(200));
        verify(userMapper).selectUnauditedNicknames(eq(FIRST_DATE.atStartOfDay()),
                eq(EXECUTION_DATE.atStartOfDay()), eq(2L), eq(200));
        verify(userMapper).selectUnauditedNicknames(eq(FIRST_DATE.atStartOfDay()),
                eq(EXECUTION_DATE.atStartOfDay()), eq(3L), eq(200));
    }

    @Test
    void savesUnsendableNicknameAndUnexpectedAuditFailureThenContinues() {
        UserInfo blank = user(1);
        blank.setNickname(null);
        firstPage(List.of(blank, user(2)));
        doThrow(new BusinessException(ResultCode.PARAM_ERROR)).when(textAuditService).auditText(isNull(), anyString());
        doThrow(new IllegalStateException("raw text")).when(textAuditService).auditText(eq("nickname-2"), anyString());

        assertThat(serviceOn(EXECUTION_DATE).auditNextRegistrationDay()).isEqualTo(2);

        List<UserNicknameAudit> rows = savedRows(2);
        assertThat(rows).extracting(UserNicknameAudit::getAuditResult).containsOnly("ERROR");
        assertThat(rows.get(0).getErrorCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        assertThat(rows.get(1).getErrorCode()).isEqualTo(ResultCode.SYSTEM_ERROR.getCode());
        assertThat(rows.get(1).getErrorMessage()).doesNotContain("raw text");
        verify(progress).completeDay(eq(FIRST_DATE), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void emptyOrAlreadyAuditedDayStillAdvancesWithoutCloudCalls() {
        assertThat(serviceOn(EXECUTION_DATE).auditNextRegistrationDay()).isZero();
        verifyNoInteractions(textAuditService, auditMapper);
        verify(progress).completeDay(eq(FIRST_DATE), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void doesNotAdvanceTwiceInSameFiveMinuteSlot() {
        when(progress.lastRunSlot()).thenReturn(executionSlot(EXECUTION_DATE));

        assertThat(serviceOn(EXECUTION_DATE).auditNextRegistrationDay()).isZero();

        verifyNoInteractions(userMapper, auditMapper, textAuditService);
        verify(progress, never()).completeDay(any(), anyLong());
    }

    @Test
    void resumesNextRegistrationDayInNextFiveMinuteSlotOnSameDate() {
        when(progress.lastRunSlot()).thenReturn(executionSlot(EXECUTION_DATE));
        when(progress.nextRegistrationDate()).thenReturn(FIRST_DATE.minusDays(1));

        assertThat(serviceAt(EXECUTION_DATE.atTime(2, 5)).auditNextRegistrationDay()).isZero();

        verify(userMapper).selectUnauditedNicknames(eq(FIRST_DATE.minusDays(1).atStartOfDay()),
                eq(FIRST_DATE.atStartOfDay()), isNull(), eq(200));
        verify(progress).completeDay(eq(FIRST_DATE.minusDays(1)),
                eq(executionSlot(EXECUTION_DATE) + 1));
    }

    @Test
    void skipsOtherMinuteWithinSameFiveMinuteSlot() {
        when(progress.lastRunSlot()).thenReturn(executionSlot(EXECUTION_DATE));

        assertThat(serviceAt(EXECUTION_DATE.atTime(2, 4, 59)).auditNextRegistrationDay()).isZero();

        verifyNoInteractions(userMapper, auditMapper, textAuditService);
        verify(progress, never()).completeDay(any(), anyLong());
    }

    @Test
    void includesJanuaryFirst2024AsLastRegistrationDay() {
        LocalDate earliestDate = LocalDate.of(2024, 1, 1);
        when(progress.nextRegistrationDate()).thenReturn(earliestDate);

        serviceOn(EXECUTION_DATE).auditNextRegistrationDay();

        verify(userMapper).selectUnauditedNicknames(eq(earliestDate.atStartOfDay()),
                eq(earliestDate.plusDays(1).atStartOfDay()), isNull(), eq(200));
        verify(progress).completeDay(eq(earliestDate), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void stopsBeforeQueryingDaysEarlierThanJanuary2024() {
        when(progress.nextRegistrationDate()).thenReturn(LocalDate.of(2023, 12, 31));

        assertThat(serviceOn(EXECUTION_DATE).auditNextRegistrationDay()).isZero();

        verifyNoInteractions(userMapper, auditMapper, textAuditService);
        verify(progress, never()).completeDay(any(), anyLong());
    }

    @Test
    void synchronousCallSkipsWhileAsyncWorkIsQueued() {
        NicknameAuditServiceImpl service = serviceOn(EXECUTION_DATE);
        assertThat(service.triggerHistoricalAudit()).isTrue();

        assertThat(service.auditNextRegistrationDay()).isZero();

        verifyNoInteractions(progress, userMapper, auditMapper, textAuditService);
    }

    @Test
    void duplicateUidNeverOverwritesOriginalRecord() {
        firstPage(List.of(user(1)));
        when(textAuditService.auditText(anyString(), anyString())).thenReturn(result(TextAuditDecision.PASS));
        when(auditMapper.insert(any(UserNicknameAudit.class))).thenThrow(new DuplicateKeyException("duplicate uid"));

        assertThat(serviceOn(EXECUTION_DATE).auditNextRegistrationDay()).isZero();

        verify(auditMapper, never()).updateById(any(UserNicknameAudit.class));
        verify(progress).completeDay(eq(FIRST_DATE), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void databaseFailureKeepsDateAndAllowsRetry() {
        NicknameAuditServiceImpl service = serviceOn(EXECUTION_DATE);
        firstPage(List.of(user(1)));
        when(textAuditService.auditText(anyString(), anyString())).thenReturn(result(TextAuditDecision.BLOCK));
        when(auditMapper.insert(any(UserNicknameAudit.class)))
                .thenThrow(new DataAccessResourceFailureException("database unavailable")).thenReturn(1);

        assertThatThrownBy(service::auditNextRegistrationDay)
                .isInstanceOf(DataAccessResourceFailureException.class);

        verify(progress, never()).completeDay(any(), anyLong());

        assertThat(service.auditNextRegistrationDay()).isEqualTo(1);
        verify(progress).completeDay(eq(FIRST_DATE), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void unsuccessfulInsertDoesNotAdvanceDate() {
        firstPage(List.of(user(1)));
        when(textAuditService.auditText(anyString(), anyString())).thenReturn(result(TextAuditDecision.PASS));
        when(auditMapper.insert(any(UserNicknameAudit.class))).thenReturn(0);

        assertThatThrownBy(() -> serviceOn(EXECUTION_DATE).auditNextRegistrationDay())
                .isInstanceOf(IllegalStateException.class);
        verify(progress, never()).completeDay(any(), anyLong());
    }

    @Test
    void synchronousWorkRejectsOverlappingSyncAndAsyncCalls() {
        NicknameAuditServiceImpl service = serviceOn(EXECUTION_DATE);
        firstPage(List.of(user(1)));
        when(textAuditService.auditText(anyString(), anyString())).thenAnswer(invocation -> {
            assertThat(service.auditNextRegistrationDay()).isZero();
            assertThat(service.triggerHistoricalAudit()).isFalse();
            return result(TextAuditDecision.PASS);
        });

        assertThat(service.auditNextRegistrationDay()).isEqualTo(1);

        verifyNoInteractions(executor);
        verify(auditMapper).insert(any(UserNicknameAudit.class));
        verify(progress).completeDay(eq(FIRST_DATE), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void progressFailureIsReportedAndAllowsRetry() {
        NicknameAuditServiceImpl service = serviceOn(EXECUTION_DATE);
        doThrow(new DataAccessResourceFailureException("redis unavailable")).doNothing()
                .when(progress).completeDay(any(), anyLong());

        assertThatThrownBy(service::auditNextRegistrationDay)
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(service.auditNextRegistrationDay()).isZero();
        verify(progress, times(2)).completeDay(eq(FIRST_DATE), eq(executionSlot(EXECUTION_DATE)));
    }

    @Test
    void invalidCursorDoesNotSilentlyRestartFromBeginning() {
        when(progress.nextRegistrationDate()).thenReturn(EXECUTION_DATE);

        assertThatThrownBy(() -> serviceOn(EXECUTION_DATE).auditNextRegistrationDay())
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(userMapper, textAuditService, auditMapper);
        verify(progress, never()).completeDay(any(), anyLong());
    }

    @Test
    void asyncTriggerDoesNotBlockSchedulerAndIgnoresOverlappingRuns() {
        NicknameAuditServiceImpl service = serviceOn(EXECUTION_DATE);

        assertThat(service.triggerHistoricalAudit()).isTrue();
        assertThat(service.triggerHistoricalAudit()).isFalse();
        verifyNoInteractions(progress);

        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(work.capture());
        work.getValue().run();
        assertThat(service.triggerHistoricalAudit()).isTrue();
    }

    @Test
    void rejectedExecutorSubmissionDoesNotPermanentlyBlockLaterTriggers() {
        NicknameAuditServiceImpl service = serviceOn(EXECUTION_DATE);
        doThrow(new IllegalStateException("executor unavailable")).doNothing().when(executor).execute(any());

        assertThatThrownBy(service::triggerHistoricalAudit).isInstanceOf(IllegalStateException.class);
        assertThat(service.triggerHistoricalAudit()).isTrue();
    }

    @Test
    void asyncFailureDoesNotPermanentlyBlockLaterTriggers() {
        NicknameAuditServiceImpl service = serviceOn(EXECUTION_DATE);
        when(progress.lastRunSlot()).thenThrow(new DataAccessResourceFailureException("redis unavailable"));
        assertThat(service.triggerHistoricalAudit()).isTrue();
        ArgumentCaptor<Runnable> work = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(work.capture());

        work.getValue().run();

        assertThat(service.triggerHistoricalAudit()).isTrue();
    }

    private NicknameAuditServiceImpl serviceOn(LocalDate executionDate) {
        return serviceAt(executionDate.atTime(2, 0));
    }

    private NicknameAuditServiceImpl serviceAt(LocalDateTime executionTime) {
        Clock clock = Clock.fixed(executionTime.atZone(NicknameAuditServiceImpl.ZONE).toInstant(), ZoneOffset.UTC);
        return new NicknameAuditServiceImpl(userMapper, auditMapper, textAuditService, progress, executor, clock);
    }

    private long executionSlot(LocalDate executionDate) {
        return Math.floorDiv(executionDate.atTime(2, 0).atZone(NicknameAuditServiceImpl.ZONE).toEpochSecond(), 300);
    }

    private void firstPage(List<UserInfo> users) {
        when(userMapper.selectUnauditedNicknames(any(), any(), isNull(), anyInt())).thenReturn(users);
    }

    private UserInfo user(long uid) {
        UserInfo user = new UserInfo();
        user.setUid(uid);
        user.setNickname("nickname-" + uid);
        user.setRegisterTime(LocalDateTime.of(2026, 11, 14, 12, 0));
        return user;
    }

    private TextAuditResult result(TextAuditDecision decision) {
        return new TextAuditResult(decision, "Normal", "job-123", "request-123", null);
    }

    private List<UserNicknameAudit> savedRows(int count) {
        ArgumentCaptor<UserNicknameAudit> captor = ArgumentCaptor.forClass(UserNicknameAudit.class);
        verify(auditMapper, times(count)).insert(captor.capture());
        return captor.getAllValues();
    }
}
