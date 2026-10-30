package com.orange.entity.dto.akoperator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 手动修改版干员数据保存请求参数
 *
 * <p>手动修改版按用户归属，与具体游戏账号无关，因此请求体只需要干员数组。
 * 传入的干员 ID 已存在则按属性比较后更新，不存在则新增；没传的 ID 不处理也不删除。</p>
 *
 * @author UserCenter
 */
public class ManualOperatorSaveRequest {

    /** 干员数组，空数组拒绝 */
    @NotEmpty(message = "干员数据不能为空")
    @Valid
    private List<ManualOperatorItemRequest> operators;

    public List<ManualOperatorItemRequest> getOperators() {
        return operators;
    }

    public void setOperators(List<ManualOperatorItemRequest> operators) {
        this.operators = operators;
    }
}
