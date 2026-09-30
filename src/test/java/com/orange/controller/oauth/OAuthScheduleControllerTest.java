package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.schedule.UserScheduleDeleteRequest;
import com.orange.entity.dto.schedule.UserScheduleSaveRequest;
import com.orange.service.UserScheduleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OAuth 排班接口权限边界测试。 */
class OAuthScheduleControllerTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void scheduleSaveRequiresConfigWriteScope() {
        UserScheduleService scheduleService = mock(UserScheduleService.class);
        OAuthScheduleController controller = new OAuthScheduleController(scheduleService);
        UserContext.setUid(7L);
        UserContext.setScope("config.write");
        when(scheduleService.saveSchedule(eq(7L), any(UserScheduleSaveRequest.class))).thenReturn(123L);

        assertDoesNotThrow(() -> controller.save(new UserScheduleSaveRequest()));

        verify(scheduleService).saveSchedule(eq(7L), any(UserScheduleSaveRequest.class));
    }

    @Test
    void scheduleDeleteRejectsTokenWithoutConfigWriteScope() {
        UserScheduleService scheduleService = mock(UserScheduleService.class);
        OAuthScheduleController controller = new OAuthScheduleController(scheduleService);
        UserContext.setUid(7L);
        UserContext.setScope("gama-data.read");
        UserScheduleDeleteRequest request = new UserScheduleDeleteRequest();
        request.setId(123L);

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.delete(request));

        assertEquals(80008, exception.getCode());
        verify(scheduleService, never()).deleteSchedule(any(), any());
    }
}
