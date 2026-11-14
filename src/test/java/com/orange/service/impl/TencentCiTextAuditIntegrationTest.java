package com.orange.service.impl;

import com.orange.common.config.TencentCiConfig;
import com.orange.common.config.TencentCiProperties;
import com.orange.entity.dto.audit.TextAuditResult;
import com.orange.service.TextAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 直接注入 Service 调用数据万象真实接口，在 IDE 中运行测试即可。
 * Spring 自动加载 test 配置，仅启动审核组件。
 */
@SpringBootTest(
        classes = {TencentCiProperties.class, TencentCiConfig.class, TencentCiTextAuditServiceImpl.class},
        properties = {"spring.config.location=classpath:application-dev.yml", "tencent.ci.enabled=true"},
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TencentCiTextAuditIntegrationTest {

    @Autowired
    private TextAuditService textAuditService;

    @Test
    void auditText() {
        TextAuditResult result = textAuditService.auditText("小橘子");
        System.out.println("CI 文本审核结果: " + result);
        assertThat(result.decision()).isNotNull();
        assertThat(result.requestId()).isNotBlank();
    }
}
