package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 游戏角色信息请求参数（保存接口每次必须携带）
 *
 * <p>其中的 {@code akUid} 即保存接口的目标游戏账号；数据库行 id 与 create_time 不由客户端指定。
 * {@code channelName / channelMasterId / nickName} 是森空岛绑定列表里的账号展示信息，均为可选：
 * 未上报（缺省或 null）时沿用绑定关系中的已有值，显式传空串表示清空；首次建立绑定且未上报时落空串。
 * 它们只用于账号列表展示，不参与归属校验与去重。</p>
 *
 * @author UserCenter
 */
public class AkPlayerInfoRequest {

    /** 账号展示字段最大长度，与 ak_account_binding 对应列的 VARCHAR(64) 一致 */
    private static final int PROFILE_MAX_LENGTH = 64;

    /** 游戏账号 UID，保存接口据此确定目标账号 */
    @NotBlank(message = "游戏账号UID不能为空")
    @Pattern(regexp = "^[\\x21-\\x7E]{1,32}$", message = "游戏账号UID必须为不超过32位的可见ASCII字符")
    private String akUid;

    /** 渠道名称（森空岛 bindingList.channelName，如官服、B服），可选 */
    @Size(max = PROFILE_MAX_LENGTH, message = "channelName长度不能超过64字符")
    private String channelName;

    /** 渠道主账号标识（森空岛 bindingList.channelMasterId），可选 */
    @Size(max = PROFILE_MAX_LENGTH, message = "channelMasterId长度不能超过64字符")
    private String channelMasterId;

    /** 游戏内角色昵称（森空岛 bindingList.nickName），可选 */
    @Size(max = PROFILE_MAX_LENGTH, message = "nickName长度不能超过64字符")
    private String nickName;

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelMasterId() {
        return channelMasterId;
    }

    public void setChannelMasterId(String channelMasterId) {
        this.channelMasterId = channelMasterId;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }
}
