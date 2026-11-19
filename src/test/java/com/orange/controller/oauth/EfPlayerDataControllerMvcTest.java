package com.orange.controller.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.UserContext;
import com.orange.common.exception.BusinessException;
import com.orange.controller.user.EfPlayerDataController;
import com.orange.entity.vo.efplayer.EfPlayerDataVO;
import com.orange.service.EfPlayerDataService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 玩家数据接口省略查询参数时的 HTTP 绑定与权限回归测试。 */
class EfPlayerDataControllerMvcTest {

    private EfPlayerDataService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(EfPlayerDataService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new EfPlayerDataController(service), new OAuthEfPlayerDataController(service)).build();
        UserContext.setUid(1001L);
        UserContext.setScope("endfield-data.read");
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/ef-accounts/player-data", "/oauth2/ef-accounts/player-data"})
    void acceptsRequestWithoutQueryParameters(String path) throws Exception {
        EfPlayerDataVO latest = new EfPlayerDataVO("latest-role", "2",
                new ObjectMapper().readTree("{\"level\":60}"), null);
        when(service.getPlayerData(1001L, null, null)).thenReturn(latest);

        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-cache"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.roleId").value("latest-role"))
                .andExpect(jsonPath("$.data.serverId").value("2"))
                .andExpect(jsonPath("$.data.playerData.level").value(60));

        verify(service).getPlayerData(1001L, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/ef-accounts/player-data", "/oauth2/ef-accounts/player-data"})
    void forwardsExplicitRoleAndServer(String path) throws Exception {
        mockMvc.perform(get(path).param("roleId", "role-1").param("serverId", "1"))
                .andExpect(status().isOk());

        verify(service).getPlayerData(1001L, "role-1", "1");
    }

    @Test
    void defaultOauthReadStillRequiresEndfieldReadScope() {
        UserContext.setScope("arknights-data.read");

        assertThatThrownBy(() -> new OAuthEfPlayerDataController(service).getPlayerData(null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 80008);

        verify(service, never()).getPlayerData(any(), any(), any());
    }
}
