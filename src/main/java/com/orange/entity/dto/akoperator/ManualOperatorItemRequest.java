package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 手动修改版单条干员数据请求参数
 *
 * <p>沿用自动导入保存的 {@link OperatorItemRequest} 全部字段与校验规则，额外增加
 * 「是否持有」。数值字段允许缺省/JSON null/空字符串，服务端统一归一化为整数 0。</p>
 *
 * @author UserCenter
 */
public class ManualOperatorItemRequest extends OperatorItemRequest {

    /** 是否持有：0 否，1 是；缺省按 0 处理 */
    @Min(value = 0, message = "owned不能为负数")
    @Max(value = 1, message = "owned取值范围为0~1")
    private Integer owned;

    public Integer getOwned() {
        return owned;
    }

    public void setOwned(Integer owned) {
        this.owned = owned;
    }
}
