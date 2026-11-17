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
 * <p>{@code serverType / serverName} 是客户端上报的服务器展示信息，均为可选：
 * 未上报（缺省或 null）时沿用绑定关系中的已有值，显式传空串表示清空；首次建立绑定
 * 且未上报时落空串。它们只用于角色列表展示，不参与角色定位与归属校验。</p>
 *
 * @author UserCenter
 */
public class EfPlayerDataSaveRequest {

    /** 账号展示字段最大长度，与 ef_account_binding 对应列的 VARCHAR(64) 一致 */
    private static final int PROFILE_MAX_LENGTH = 64;

    /** 终末地游戏内角色 UID（card/detail 的 roleId），不超过 32 位可见 ASCII 字符 */
    @NotBlank(message = "roleId不能为空")
    @Size(max = 32, message = "roleId长度不能超过32")
    private String roleId;

    /** 服务器 ID，可省略；省略时按空串处理，长度不超过 16 */
    @Size(max = 16, message = "serverId长度不能超过16")
    private String serverId;

    /** 服务器类型，可选；取自客户端的服务器信息，最长 64 字符 */
    @Size(max = PROFILE_MAX_LENGTH, message = "serverType长度不能超过64字符")
    private String serverType;

    /** 服务器名称，可选；取自客户端的服务器信息，最长 64 字符 */
    @Size(max = PROFILE_MAX_LENGTH, message = "serverName长度不能超过64字符")
    private String serverName;

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

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public JsonNode getPlayerData() {
        return playerData;
    }

    public void setPlayerData(JsonNode playerData) {
        this.playerData = playerData;
    }
}
