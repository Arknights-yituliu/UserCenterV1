package com.orange.controller.user;

import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.service.AuthService;
import com.orange.service.EmailCodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AuthControllerTest {
    @ParameterizedTest
    @ValueSource(strings = {"TCL-Client-Id", "UC-Client-Id"})
    void loginAndRegisterPassOriginalClientValueThroughEitherHeader(String header) {
        AuthService auth = mock(AuthService.class);
        AuthController controller = new AuthController(auth, mock(EmailCodeService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(header, "original-client");
        request.addHeader("User-Agent", "test-agent");
        request.setRemoteAddr("127.0.0.1");
        LoginRequest login = new LoginRequest();
        RegisterRequest register = new RegisterRequest();

        controller.login(login, request);
        controller.register(register, request);

        verify(auth).login(login, "127.0.0.1", "test-agent", "original-client");
        verify(auth).register(register, "127.0.0.1", "original-client");
    }

    @Test
    void conflictingClientsDoNotReachLoginOrRegisterService() {
        AuthService auth = mock(AuthService.class);
        AuthController controller = new AuthController(auth, mock(EmailCodeService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("TCL-Client-Id", "first");
        request.addHeader("UC-Client-Id", "second");
        assertThrows(BusinessException.class, () -> controller.login(new LoginRequest(), request));
        assertThrows(BusinessException.class, () -> controller.register(new RegisterRequest(), request));
        verifyNoInteractions(auth);
    }
}
