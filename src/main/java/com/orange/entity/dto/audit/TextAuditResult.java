package com.orange.entity.dto.audit;

import com.orange.common.enums.TextAuditDecision;

/**
 * 文本审核结果，不携带送审原文或 SDK 响应，避免被无意记录或返回给前端。
 *
 * @param decision 审核结论，由未来的业务调用方决定处置方式
 * @param label 命中分类，例如 Normal、Porn、Ads，其他值保持厂商原样
 * @param jobId 审核任务 ID
 * @param requestId 腾讯云请求 ID，可用于排查权限、策略等问题
 * @param dataId 腾讯云回传的业务标识，未传时可为空
 */
public record TextAuditResult(TextAuditDecision decision, String label, String jobId,
                              String requestId, String dataId) {
}
