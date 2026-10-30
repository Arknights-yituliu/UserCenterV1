package com.orange.entity.dto.efplayer;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 终末地玩家数据保存请求参数
 *
 * <p>玩家数据整份以 JSON 对象上传，服务端不做拆分，序列化后直接作为文本落库。
 * 目标角色由 {@code roleId} + {@code serverId} 定位，当前用户首次上传该角色时直接建立绑定。</p>
 *
 * @author UserCenter
 */
public class EfPlayerDataSaveRequest {

    /** 终末地游戏内角色 UID（card/detail 的 roleId），不超过 32 位可见 ASCII 字符 */
    @NotBlank(message = "roleId不能为空")
    @Size(max = 32, message = "roleId长度不能超过32")
    private String roleId;

    /** 服务器 ID，可省略；省略时按空串处理，长度不超过 16 */
    @Size(max = 16, message = "serverId长度不能超过16")
    private String serverId;

    /** 玩家数据 JSON 对象，不能为空 */
    @NotNull(message = "playerData不能为空")
    private JsonNode playerData;

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public JsonNode getPlayerData() {
        return playerData;
    }

    public void setPlayerData(JsonNode playerData) {
        this.playerData = playerData;
    }
}
