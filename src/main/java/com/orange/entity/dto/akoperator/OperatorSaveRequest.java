package com.orange.entity.dto.akoperator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 干员数据批量保存请求参数
 *
 * <p>每次请求都必须包含 {@code playerInfo} 与非空干员数组；传入的干员 ID 有则按属性比较后更新，
 * 无则新增，没传的 ID 不处理也不删除。可同时携带物品存量数组 {@code items}，语义与干员一致：
 * 已存在的物品按数量比较后更新，未传入的物品不处理也不删除。当前用户首次上传该游戏账号时直接建立绑定关系。</p>
 *
 * @author UserCenter
 */
public class OperatorSaveRequest {

    /** 本次上传对应的游戏角色信息（完整状态） */
    @NotNull(message = "playerInfo不能为空")
    @Valid
    private AkPlayerInfoRequest playerInfo;

    /** 干员数组，空数组拒绝 */
    @NotEmpty(message = "干员数据不能为空")
    @Valid
    private List<OperatorItemRequest> operators;

    /** 物品存量数组，可选；缺省或空数组表示本次不上报物品，已有物品数据保持不变 */
    @Valid
    private List<ItemEntryRequest> items;

    public AkPlayerInfoRequest getPlayerInfo() {
        return playerInfo;
    }

    public void setPlayerInfo(AkPlayerInfoRequest playerInfo) {
        this.playerInfo = playerInfo;
    }

    public List<OperatorItemRequest> getOperators() {
        return operators;
    }

    public void setOperators(List<OperatorItemRequest> operators) {
        this.operators = operators;
    }

    public List<ItemEntryRequest> getItems() {
        return items;
    }

    public void setItems(List<ItemEntryRequest> items) {
        this.items = items;
    }
}
