package com.orange.service;

import com.orange.entity.dto.audit.TextAuditResult;

/**
 * 文本内容同步审核服务，目前仅提供能力，不接入注册、资料修改或 Controller。
 * 不执行自动替换、打码或用户数据写入。
 */
public interface TextAuditService {

    default TextAuditResult auditText(String text) {
        return auditText(text, null);
    }

    /**
     * 审核原始文本；内部负责 UTF-8 和 Base64 编码，调用方无需编码。
     *
     * @param text 非空文本，最多 10000 个 Unicode 码点
     * @param dataId 可选业务标识，非空时最多 512 个 UTF-8 字节，不应包含敏感信息
     * @return 通过、疑似违规或违规；疑似违规不会自动视为通过
     * @throws com.orange.common.exception.BusinessException 参数错误、服务未启用或审核调用失败
     */
    TextAuditResult auditText(String text, String dataId);
}
