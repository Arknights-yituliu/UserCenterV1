package com.orange.service.impl;

import com.orange.common.config.TencentCiProperties;
import com.orange.common.enums.ResultCode;
import com.orange.common.enums.TextAuditDecision;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.audit.TextAuditResult;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.model.ciModel.auditing.AuditingJobsDetail;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingRequest;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TencentCiTextAuditServiceImplTest {

    @Mock
    private COSClient client;

    @Test
    void sendsExactUtf8TextAndConfiguredStrategyWithoutUploadingAFile() {
        when(client.createAuditingTextJobs(any())).thenReturn(response("Success", "0"));

        TextAuditResult result = service("nickname-strategy").auditText(" 小橘子🍊 ", "register-123");

        ArgumentCaptor<TextAuditingRequest> captor = ArgumentCaptor.forClass(TextAuditingRequest.class);
        verify(client).createAuditingTextJobs(captor.capture());
        TextAuditingRequest request = captor.getValue();
        assertThat(request.getBucketName()).isEqualTo("audit-1250000000");
        assertThat(new String(Base64.getDecoder().decode(request.getInput().getContent()), StandardCharsets.UTF_8))
                .isEqualTo(" 小橘子🍊 ");
        assertThat(request.getInput().getDataId()).isEqualTo("register-123");
        assertThat(request.getInput().getObject()).isNullOrEmpty();
        assertThat(request.getInput().getUrl()).isNullOrEmpty();
        assertThat(request.getConf().getBizType()).isEqualTo("nickname-strategy");
        assertThat(result.requestId()).isEqualTo("request-123");
        assertThat(result.jobId()).isEqualTo("job-123");
        assertThat(result.label()).isEqualTo("Normal");
        assertThat(result.dataId()).isEqualTo("register-123");
    }

    @ParameterizedTest
    @CsvSource({"0,PASS", "1,BLOCK", "2,REVIEW"})
    void preservesAllThreeContentDecisions(String code, TextAuditDecision decision) {
        when(client.createAuditingTextJobs(any())).thenReturn(response("Success", code));

        assertThat(service("").auditText("test").decision()).isEqualTo(decision);
    }

    @Test
    void leavesOptionalStrategyAndBusinessIdUnset() {
        when(client.createAuditingTextJobs(any())).thenReturn(response("Success", "0"));

        service("").auditText("test");

        ArgumentCaptor<TextAuditingRequest> captor = ArgumentCaptor.forClass(TextAuditingRequest.class);
        verify(client).createAuditingTextJobs(captor.capture());
        assertThat(captor.getValue().getConf().getBizType()).isNullOrEmpty();
        assertThat(captor.getValue().getInput().getDataId()).isNullOrEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t", "\uD800"})
    void rejectsBlankOrMalformedUnicodeBeforeCallingCloud(String text) {
        assertParameterError(() -> service("").auditText(text));
        verifyNoInteractions(client);
    }

    @Test
    void checksTextLengthBeforeCallingCloud() {
        assertParameterError(() -> service("").auditText("中".repeat(10001)));
        verifyNoInteractions(client);
    }

    @Test
    void acceptsCharacterBoundaryAndMeasuresBusinessIdInUtf8Bytes() {
        when(client.createAuditingTextJobs(any())).thenReturn(response("Success", "0"));
        assertThat(service("").auditText("中".repeat(10000), "a".repeat(512)).decision())
                .isEqualTo(TextAuditDecision.PASS);

        assertParameterError(() -> service("").auditText("test", "中".repeat(171)));
        assertParameterError(() -> service("").auditText("test", " "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Failed", "Submitted", "Running"})
    void rejectsUnsuccessfulOrIncompleteTasksEvenWhenResultSaysPass(String state) {
        when(client.createAuditingTextJobs(any())).thenReturn(response(state, "0"));

        assertProviderError(() -> service("").auditText("test"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"3", "unknown"})
    void neverTreatsMissingOrUnknownDecisionsAsPass(String result) {
        when(client.createAuditingTextJobs(any())).thenReturn(response("Success", result));

        assertProviderError(() -> service("").auditText("test"));
    }

    @Test
    void rejectsEmptyResponses() {
        when(client.createAuditingTextJobs(any())).thenReturn(null);
        assertProviderError(() -> service("").auditText("test"));

        TextAuditingResponse response = new TextAuditingResponse();
        response.setJobsDetail(null);
        when(client.createAuditingTextJobs(any())).thenReturn(response);
        assertProviderError(() -> service("").auditText("test"));
    }

    @Test
    void reportsDisabledServiceWithoutCallingCloud() {
        TencentCiTextAuditServiceImpl service = new TencentCiTextAuditServiceImpl(Optional.empty(), properties(""));

        assertThatThrownBy(() -> service.auditText("test"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.SYSTEM_ERROR.getCode()))
                .hasMessage("数据万象文本审核未启用");
        verifyNoInteractions(client);
    }

    @Test
    void separatesProviderErrorsFromContentRejectionAndHidesRawErrorMessages() {
        CosServiceException error = new CosServiceException("sensitive raw response");
        error.setStatusCode(403);
        error.setErrorCode("AccessDenied");
        error.setRequestId("request-denied");
        when(client.createAuditingTextJobs(any())).thenThrow(error);
        assertProviderError(() -> service("").auditText("test"));

        doThrow(new CosClientException("sensitive raw request")).when(client).createAuditingTextJobs(any());
        assertProviderError(() -> service("").auditText("test"));
    }

    private TencentCiTextAuditServiceImpl service(String bizType) {
        return new TencentCiTextAuditServiceImpl(Optional.of(client), properties(bizType));
    }

    private TencentCiProperties properties(String bizType) {
        return new TencentCiProperties("id", "key", "ap-guangzhou", "audit-1250000000", bizType,
                3000, 5000, 8000);
    }

    private TextAuditingResponse response(String state, String result) {
        AuditingJobsDetail detail = new AuditingJobsDetail();
        detail.setState(state);
        detail.setResult(result);
        detail.setLabel("Normal");
        detail.setJobId("job-123");
        detail.setDataId("register-123");
        TextAuditingResponse response = new TextAuditingResponse();
        response.setRequestId("request-123");
        response.setJobsDetail(detail);
        return response;
    }

    private void assertParameterError(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode()));
    }

    private void assertProviderError(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.OUTER_API_INVOKE_ERROR.getCode()))
                .hasMessageNotContaining("sensitive");
    }
}
