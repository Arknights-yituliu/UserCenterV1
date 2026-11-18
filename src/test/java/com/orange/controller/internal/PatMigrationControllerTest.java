package com.orange.controller.internal;

import com.orange.entity.vo.pat.PatMigrationResultVO;
import com.orange.service.PatMigrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 一次性迁移端点的装配与契约测试
 *
 * <p>本端点已去掉 {@code @ConditionalOnProperty} 开关，随应用启动直接注册。第一个用例就是把这个
 * 决定钉住：容器里没有配置任何属性，Controller 仍必须是可取的 Bean；若有人把开关加回去，用例会
 * 在 {@code getBean} 处失败。</p>
 *
 * <p>第二个用例只验证「无参数 POST → 200 + 统计字段」这一层契约，迁移本身的行为在
 * {@code PatMigrationServiceImpl} 里，与 MVC 无关。</p>
 *
 * @author UserCenter
 */
class PatMigrationControllerTest {

    private AnnotationConfigWebApplicationContext context;

    private PatMigrationService patMigrationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestWebConfig.class);
        context.refresh();
        patMigrationService = context.getBean(PatMigrationService.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void controllerIsRegisteredWithoutAnyProperty() {
        assertNotNull(context.getBean(PatMigrationController.class));
    }

    @Test
    void runReturnsMigrationStatistics() throws Exception {
        PatMigrationResultVO vo = new PatMigrationResultVO();
        vo.setTotal(20413L);
        vo.setMigrated(20413L);
        vo.setSkipped(0L);
        vo.setFailed(0L);
        vo.setElapsedMs(8421L);
        when(patMigrationService.run()).thenReturn(vo);

        mockMvc.perform(post("/internal/pat-migration/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(20413))
                .andExpect(jsonPath("$.data.migrated").value(20413))
                .andExpect(jsonPath("$.data.skipped").value(0))
                .andExpect(jsonPath("$.data.failed").value(0))
                .andExpect(jsonPath("$.data.elapsedMs").value(8421));

        verify(patMigrationService).run();
    }

    @Configuration
    @EnableWebMvc
    static class TestWebConfig {

        @Bean
        PatMigrationService patMigrationService() {
            return mock(PatMigrationService.class);
        }

        @Bean
        PatMigrationController patMigrationController(PatMigrationService patMigrationService) {
            return new PatMigrationController(patMigrationService);
        }
    }
}
