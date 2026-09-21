package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.UserScheduleVO;
import com.orange.service.AkAccountService;
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

/** OAuth 游戏数据控制器权限边界测试。 */
class OAuthGameDataControllerTest {

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void scheduleReadRequiresGameDataReadScope() {
        UserScheduleService scheduleService = mock(UserScheduleService.class);
        OAuthScheduleController controller = new OAuthScheduleController(scheduleService);
        UserContext.setScope("gama-data.read");
        when(scheduleService.getSchedule(8L)).thenReturn(new UserScheduleVO());

        assertDoesNotThrow(() -> controller.get(8L));

        verify(scheduleService).getSchedule(8L);
    }

    @Test
    void accountReadRejectsTokenWithoutGameDataReadScope() {
        AkAccountService accountService = mock(AkAccountService.class);
        OAuthAkAccountController controller = new OAuthAkAccountController(accountService);
        UserContext.setUid(7L);
        UserContext.setScope("gama-data.write");

        BusinessException exception = assertThrows(BusinessException.class, controller::listAccounts);

        assertEquals(80008, exception.getCode());
        verify(accountService, never()).listBoundAccounts(any());
    }

    @Test
    void accountWriteRequiresGameDataWriteScope() {
        AkAccountService accountService = mock(AkAccountService.class);
        OAuthAkAccountController controller = new OAuthAkAccountController(accountService);
        UserContext.setUid(7L);
        UserContext.setScope("gama-data.write");

        assertDoesNotThrow(() -> controller.saveOperators("ak-1", new OperatorSaveRequest()));

        verify(accountService).saveOperators(eq(7L), eq("ak-1"), any(OperatorSaveRequest.class));
    }
}
