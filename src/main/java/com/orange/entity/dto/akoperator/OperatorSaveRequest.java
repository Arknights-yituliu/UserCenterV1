package com.orange.entity.dto.akoperator;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 干员数据批量保存请求参数
 *
 * <p>每次请求都必须包含 {@code playerInfo} 与非空干员数组；传入的干员 ID 有则按属性比较后更新，
 * 无则新增，没传的 ID 不处理也不删除。可同时携带物品存量数组 {@code items}，语义与干员一致：
 * 已存在的物品按数量比较后更新，未传入的物品不处理也不删除。可同时携带皮肤数组 {@code skins}，
 * 语义不同：皮肤是账号维度的完整集合，携带时按本次提交全量对齐（补新增、改时间、删未提交）。
 * 还可携带 player/info 文本 {@code playerData}，原样落库、不解析不校验结构，每次携带即整段覆盖。
 * 当前用户首次上传该游戏账号时直接建立绑定关系。</p>
 *
 * @author UserCenter
 */
public class OperatorSaveRequest {

    /** playerData 允许的最大字符数，约 2M，足够覆盖正常账号（样例约 17 万字符） */
    public static final int PLAYER_DATA_MAX_LENGTH = 2_000_000;

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

    /** 已拥有皮肤数组，可选；缺省或空数组表示本次不上报皮肤，已有皮肤数据保持不变 */
    @Valid
    private List<SkinEntryRequest> skins;

    /**
     * player/info 文本，可选；原样落库，服务端不解析也不要求是合法 JSON。
     * 缺省、null 或空串表示本次不上报，已有镜像保持不变
     */
    @Size(max = PLAYER_DATA_MAX_LENGTH, message = "playerData长度不能超过2000000字符")
    private String playerData;

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

    public List<SkinEntryRequest> getSkins() {
        return skins;
    }

    public void setSkins(List<SkinEntryRequest> skins) {
        this.skins = skins;
    }

    public String getPlayerData() {
        return playerData;
    }

    public void setPlayerData(String playerData) {
        this.playerData = playerData;
    }
}
