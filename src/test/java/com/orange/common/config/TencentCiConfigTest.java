package com.orange.common.config;

import com.orange.service.TextAuditService;
import com.orange.service.impl.TencentCiTextAuditServiceImpl;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.http.HttpProtocol;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class TencentCiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TencentCiProperties.class, TencentCiConfig.class, TencentCiTextAuditServiceImpl.class);

    @Test
    void disabledByDefaultWithoutRequiringCredentialsOrBucket() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TextAuditService.class).doesNotHaveBean(COSClient.class);
        });
    }

    @Test
    void enabledClientReusesSharedCredentialsAndUsesHttpsBoundedTimeoutsWithoutRetries() {
        contextRunner.withPropertyValues(
                "tencent.ci.enabled=true", "tencent.secretId=shared-id", "tencent.secretKey=shared-key",
                "tencent.ci.region=ap-guangzhou", "tencent.ci.bucket=audit-1250000000",
                "tencent.ci.connection-timeout-ms=1000", "tencent.ci.socket-timeout-ms=2000",
                "tencent.ci.request-timeout-ms=3000"
        ).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(COSClient.class).hasSingleBean(TextAuditService.class);
            TencentCiProperties properties = context.getBean(TencentCiProperties.class);
            assertThat(properties.getSecretId()).isEqualTo("shared-id");
            assertThat(properties.getSecretKey()).isEqualTo("shared-key");
            ClientConfig config = context.getBean("tencentCiClient", COSClient.class).getClientConfig();
            assertThat(config.getHttpProtocol()).isEqualTo(HttpProtocol.https);
            assertThat(config.getConnectionTimeout()).isEqualTo(1000);
            assertThat(config.getConnectionRequestTimeout()).isEqualTo(1000);
            assertThat(config.getSocketTimeout()).isEqualTo(2000);
            assertThat(config.getRequestTimeOutEnable()).isTrue();
            assertThat(config.getRequestTimeout()).isEqualTo(3000);
            assertThat(config.getMaxErrorRetry()).isZero();
            assertThat(config.isChangeEndpointRetry()).isFalse();
        });
    }

    @Test
    void enabledClientPrefersIndependentCredentials() {
        contextRunner.withPropertyValues(
                "tencent.ci.enabled=true", "tencent.secretId=shared-id", "tencent.secretKey=shared-key",
                "tencent.ci.secret-id=ci-id", "tencent.ci.secret-key=ci-key",
                "tencent.ci.region=ap-guangzhou", "tencent.ci.bucket=audit-1250000000"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(TencentCiProperties.class).getSecretId()).isEqualTo("ci-id");
            assertThat(context.getBean(TencentCiProperties.class).getSecretKey()).isEqualTo("ci-key");
        });
    }

    @Test
    void failsFastWhenEnabledButRequiredConfigurationIsMissing() {
        contextRunner.withPropertyValues("tencent.ci.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void rejectsNonPositiveTimeouts() {
        contextRunner.withPropertyValues(
                "tencent.ci.enabled=true", "tencent.secretId=id", "tencent.secretKey=key",
                "tencent.ci.region=ap-guangzhou", "tencent.ci.bucket=audit-1250000000",
                "tencent.ci.request-timeout-ms=0"
        ).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseMessage("数据万象超时配置必须大于 0 毫秒");
        });
    }
}
