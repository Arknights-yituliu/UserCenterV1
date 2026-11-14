package com.orange.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 数据万象文本审核参数。独立密钥未配置时，复用现有腾讯云公共密钥。
 * 启用开关 tencent.ci.enabled 默认关闭，由 {@link TencentCiConfig} 控制客户端创建。
 */
@Component
public class TencentCiProperties {

    private final String secretId;
    private final String secretKey;
    private final String region;
    private final String bucket;
    private final String bizType;
    private final int connectionTimeoutMs;
    private final int socketTimeoutMs;
    private final int requestTimeoutMs;

    public TencentCiProperties(
            @Value("${tencent.ci.secret-id:${tencent.secretId:}}") String secretId,
            @Value("${tencent.ci.secret-key:${tencent.secretKey:}}") String secretKey,
            @Value("${tencent.ci.region:}") String region,
            @Value("${tencent.ci.bucket:}") String bucket,
            @Value("${tencent.ci.biz-type:}") String bizType,
            @Value("${tencent.ci.connection-timeout-ms:3000}") int connectionTimeoutMs,
            @Value("${tencent.ci.socket-timeout-ms:5000}") int socketTimeoutMs,
            @Value("${tencent.ci.request-timeout-ms:8000}") int requestTimeoutMs) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.region = region;
        this.bucket = bucket;
        this.bizType = bizType;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.socketTimeoutMs = socketTimeoutMs;
        this.requestTimeoutMs = requestTimeoutMs;
    }

    public String getSecretId() {
        return secretId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getRegion() {
        return region;
    }

    public String getBucket() {
        return bucket;
    }

    public String getBizType() {
        return bizType;
    }

    public int getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    public int getSocketTimeoutMs() {
        return socketTimeoutMs;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }
}
