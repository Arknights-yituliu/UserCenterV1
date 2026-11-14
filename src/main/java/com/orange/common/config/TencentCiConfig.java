package com.orange.common.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** 数据万象专用客户端，共享连接池并在应用关闭时释放资源。 */
@Configuration(proxyBeanMethods = false)
public class TencentCiConfig {

    @Bean(name = "tencentCiClient", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "tencent.ci", name = "enabled", havingValue = "true")
    public COSClient tencentCiClient(TencentCiProperties properties) {
        requireText(properties.getSecretId(), "tencent.ci.secret-id / tencent.secretId");
        requireText(properties.getSecretKey(), "tencent.ci.secret-key / tencent.secretKey");
        requireText(properties.getRegion(), "tencent.ci.region");
        requireText(properties.getBucket(), "tencent.ci.bucket");
        if (properties.getConnectionTimeoutMs() <= 0 || properties.getSocketTimeoutMs() <= 0
                || properties.getRequestTimeoutMs() <= 0) {
            throw new IllegalArgumentException("数据万象超时配置必须大于 0 毫秒");
        }

        ClientConfig config = new ClientConfig(new Region(properties.getRegion()));
        config.setHttpProtocol(HttpProtocol.https);
        config.setConnectionTimeout(properties.getConnectionTimeoutMs());
        config.setConnectionRequestTimeout(properties.getConnectionTimeoutMs());
        config.setSocketTimeout(properties.getSocketTimeoutMs());
        config.setRequestTimeout(properties.getRequestTimeoutMs());
        config.setRequestTimeOutEnable(true);
        // 审核请求可能计费，不自动重试或切换域名重发。
        config.setMaxErrorRetry(0);
        config.setChangeEndpointRetry(false);
        config.setPrintShutdownStackTrace(false);
        return new COSClient(new BasicCOSCredentials(properties.getSecretId(), properties.getSecretKey()), config);
    }

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("启用数据万象文本审核时必须配置 " + name);
        }
    }
}
