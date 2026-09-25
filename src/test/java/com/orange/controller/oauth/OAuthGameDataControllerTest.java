package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.exception.BusinessException;
import com.orange.controller.user.AkAccountController;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.UserScheduleVO;
import com.orange.service.AkAccountService;
import com.orange.service.UserScheduleService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;

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

        assertDoesNotThrow(() -> controller.saveOperators(new OperatorSaveRequest()));

        verify(accountService).saveOperators(eq(7L), any(OperatorSaveRequest.class));
    }

    @Test
    void userAndOauthOperatorReadRoutesUseAkUidQueryParameter() throws NoSuchMethodException {
        assertAccountRoutes(AkAccountController.class);
        assertAccountRoutes(OAuthAkAccountController.class);
    }

    private void assertAccountRoutes(Class<?> controllerClass) throws NoSuchMethodException {
        Method read = controllerClass.getDeclaredMethod("listOperators", String.class);
        Method save = controllerClass.getDeclaredMethod("saveOperators", OperatorSaveRequest.class);

        assertEquals("/operators", read.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/operators/save", save.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("akUid", read.getParameters()[0].getAnnotation(RequestParam.class).value());
    }
}
