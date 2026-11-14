package com.orange.common.enums;

/** 文本审核结论；审核调用失败通过异常表达，不属于任何一种内容结论。 */
public enum TextAuditDecision {
    /** 未识别到违规内容，对应数据万象 Result=0。 */
    PASS,
    /** 疑似违规，建议人工复核，对应 Result=2。 */
    REVIEW,
    /** 判定为违规，对应 Result=1。 */
    BLOCK
}
