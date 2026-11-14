package com.orange.service.impl;

import com.orange.common.config.TencentCiProperties;
import com.orange.common.enums.ResultCode;
import com.orange.common.enums.TextAuditDecision;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.entity.dto.audit.TextAuditResult;
import com.orange.service.TextAuditService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ciModel.auditing.AuditingJobsDetail;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingRequest;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/** 使用 COS SDK 调用数据万象 POST /text/auditing，直接送审文本而非上传文件。 */
@Service
public class TencentCiTextAuditServiceImpl implements TextAuditService {

    private static final int MAX_TEXT_CODE_POINTS = 10000;
    private static final int MAX_DATA_ID_BYTES = 512;

    private final COSClient client;
    private final TencentCiProperties properties;

    public TencentCiTextAuditServiceImpl(@Qualifier("tencentCiClient") Optional<COSClient> client,
                                         TencentCiProperties properties) {
        this.client = client.orElse(null);
        this.properties = properties;
    }

    @Override
    public TextAuditResult auditText(String text, String dataId) {
        validateInput(text, dataId);
        if (client == null) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "数据万象文本审核未启用");
        }

        TextAuditingRequest request = new TextAuditingRequest();
        request.setBucketName(properties.getBucket());
        request.getInput().setContent(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        if (dataId != null) {
            request.getInput().setDataId(dataId);
        }
        if (StringUtils.hasText(properties.getBizType())) {
            request.getConf().setBizType(properties.getBizType());
        }

        TextAuditingResponse response;
        try {
            response = client.createAuditingTextJobs(request);
        } catch (CosServiceException e) {
            // 不记录异常原文/堆栈：SDK 异常可能包含响应正文或请求信息。
            LogUtil.warn(TencentCiTextAuditServiceImpl.class,
                    "[CI] 文本审核请求失败: status={}, code={}, requestId={}",
                    e.getStatusCode(), e.getErrorCode(), e.getRequestId());
            throw new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "数据万象文本审核暂不可用，请稍后重试");
        } catch (CosClientException e) {
            LogUtil.warn(TencentCiTextAuditServiceImpl.class, "[CI] 文本审核连接失败或超时");
            throw new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "数据万象文本审核暂不可用，请稍后重试");
        }

        AuditingJobsDetail detail = response == null ? null : response.getJobsDetail();
        if (detail == null || !"Success".equals(detail.getState())) {
            LogUtil.warn(TencentCiTextAuditServiceImpl.class,
                    "[CI] 文本审核未成功完成: requestId={}, code={}",
                    response == null ? null : response.getRequestId(), detail == null ? null : detail.getCode());
            throw invalidResponse();
        }
        TextAuditDecision decision = switch (detail.getResult() == null ? "" : detail.getResult()) {
            case "0" -> TextAuditDecision.PASS;
            case "1" -> TextAuditDecision.BLOCK;
            case "2" -> TextAuditDecision.REVIEW;
            default -> throw invalidResponse();
        };
        return new TextAuditResult(decision, detail.getLabel(), detail.getJobId(),
                response.getRequestId(), detail.getDataId());
    }

    private void validateInput(String text, String dataId) {
        if (text == null || text.isBlank() || !StandardCharsets.UTF_8.newEncoder().canEncode(text)
                || text.codePointCount(0, text.length()) > MAX_TEXT_CODE_POINTS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核文本不能为空，须为有效 Unicode 且不超过 10000 个字符");
        }
        if (dataId != null && (dataId.isBlank() || !StandardCharsets.UTF_8.newEncoder().canEncode(dataId)
                || dataId.getBytes(StandardCharsets.UTF_8).length > MAX_DATA_ID_BYTES)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "审核业务标识不能为空白且不能超过 512 个 UTF-8 字节");
        }
    }

    private BusinessException invalidResponse() {
        return new BusinessException(ResultCode.OUTER_API_INVOKE_ERROR, "数据万象未返回有效的文本审核结果，请稍后重试");
    }
}
