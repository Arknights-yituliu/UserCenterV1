package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 游戏角色信息请求参数（保存接口每次必须携带）
 *
 * <p>服务端把本次提交的角色信息当作完整状态与旧值比较：{@code channelName}、
 * {@code channelMasterId} 传 null 表示清空该可空字段，而不是沿用旧值。
 * 数据库行 id、delete_flag、update_time 不由客户端指定。</p>
 *
 * @author UserCenter
 */
public class AkPlayerInfoRequest {

    /** 游戏账号 UID，必须与请求路径中的 akUid 一致 */
    @NotBlank(message = "游戏账号UID不能为空")
    @Pattern(regexp = "^[\\x21-\\x7E]{1,32}$", message = "游戏账号UID必须为不超过32位的可见ASCII字符")
    private String akUid;

    /** 游戏角色昵称 */
    @NotBlank(message = "游戏角色昵称不能为空")
    @Size(max = 255, message = "游戏角色昵称长度不能超过255")
    private String akNickName;

    /** 渠道名称，可为空（null 表示清空） */
    @Size(max = 255, message = "渠道名称长度不能超过255")
    private String channelName;

    /** 渠道主 ID，可为空（null 表示清空） */
    private Integer channelMasterId;

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    public String getAkNickName() {
        return akNickName;
    }

    public void setAkNickName(String akNickName) {
        this.akNickName = akNickName;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Integer getChannelMasterId() {
        return channelMasterId;
    }

    public void setChannelMasterId(Integer channelMasterId) {
        this.channelMasterId = channelMasterId;
    }
}
