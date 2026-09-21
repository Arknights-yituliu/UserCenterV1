package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BadRequestException;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.schedule.UserScheduleSaveRequest;
import com.orange.entity.po.UserInfo;
import com.orange.entity.po.UserSchedule;
import com.orange.entity.vo.UserScheduleVO;
import com.orange.mapper.UserInfoMapper;
import com.orange.mapper.UserScheduleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserScheduleServiceImplTest {

    private static final long UID = 1001L;

    @Mock
    private UserScheduleMapper userScheduleMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Test
    void createsScheduleWithSnowflakeIdAfterLockingUser() {
        when(userInfoMapper.selectByUidForUpdate(UID)).thenReturn(user(UID, "orange"));
        when(userScheduleMapper.countByUid(UID)).thenReturn(4L);
        when(userScheduleMapper.insert(any(UserSchedule.class))).thenReturn(1);

        long id = service().saveSchedule(UID, request(null, "[{\"date\":\"2026-09-21\"}]"));

        assertThat(id).isPositive();
        ArgumentCaptor<UserSchedule> captor = ArgumentCaptor.forClass(UserSchedule.class);
        verify(userScheduleMapper).insert(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getSchedule()).isEqualTo("[{\"date\":\"2026-09-21\"}]");
    }

    @Test
    void rejectsSixthSchedule() {
        when(userInfoMapper.selectByUidForUpdate(UID)).thenReturn(user(UID, "orange"));
        when(userScheduleMapper.countByUid(UID)).thenReturn(5L);

        assertThatThrownBy(() -> service().saveSchedule(UID, request(null, "[]")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("最多保存 5");
        verify(userScheduleMapper, never()).insert(any());
    }

    @Test
    void rejectsInvalidScheduleJsonBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service().saveSchedule(UID, request(null, "not-json")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("合法 JSON");
        verify(userInfoMapper, never()).selectByUidForUpdate(any());
    }

    @Test
    void rejectsScheduleOverByteLimit() {
        String tooLarge = "[\"" + "中".repeat(10_240) + "\"]";

        assertThatThrownBy(() -> service().saveSchedule(UID, request(null, tooLarge)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("30KB");
        verify(userInfoMapper, never()).selectByUidForUpdate(any());
    }

    @Test
    void queryByIdReturnsFullSchedule() {
        UserSchedule schedule = new UserSchedule();
        schedule.setId(9L);
        schedule.setSchedule("[{\"date\":\"2026-09-21\",\"time\":\"09:00\"}]");
        when(userScheduleMapper.selectById(9L)).thenReturn(schedule);

        UserScheduleVO result = service().getSchedule(9L);

        assertThat(result.getSchedule().get(0).get("date").asText()).isEqualTo("2026-09-21");
        assertThat(result.getSchedule().get(0).get("time").asText()).isEqualTo("09:00");
    }

    @Test
    void updateOfOtherUsersScheduleIsForbidden() {
        when(userScheduleMapper.selectOwnedByIdForUpdate(9L, UID)).thenReturn(null);

        assertThatThrownBy(() -> service().saveSchedule(UID, request(9L, "[]")))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    private UserScheduleServiceImpl service() {
        return new UserScheduleServiceImpl(userScheduleMapper, userInfoMapper, new ObjectMapper());
    }

    private UserScheduleSaveRequest request(Long id, String schedule) {
        UserScheduleSaveRequest request = new UserScheduleSaveRequest();
        request.setId(id);
        request.setSchedule(schedule);
        return request;
    }

    private UserInfo user(long uid, String userName) {
        UserInfo user = new UserInfo();
        user.setUid(uid);
        user.setUserName(userName);
        return user;
    }
}
