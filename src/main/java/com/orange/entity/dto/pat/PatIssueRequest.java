package com.orange.entity.dto.pat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 签发个人访问令牌（PAT）的请求参数
 *
 * <p>{@code payload} 是结构固定的 {@link PatPayload}，服务端只校验「序列化后 ≤ 4KB」，
 * 不解读键含义、不据其分支；所有字段均可为空（可传 {@code {}} 或只填部分）。<strong>唯一
 * 例外是消费端约定的 {@code game_uid}</strong>——带 {@code tlc_pat_} 前缀的新令牌在读取
 * 干员数据时必须提供它（缺失或空白返回 10002），详见《PAT消费端接口设计.md》§3.2。</p>
 *
 * @author UserCenter
 */
public class PatIssueRequest {

    /** 用户命名的用途 */
    @NotBlank(message = "name 不能为空")
    @Size(max = 50, message = "name 长度不能超过 50")
    private String name;

    /** 有效期（秒） */
    @NotNull(message = "expires_in 不能为空")
    @Min(value = 1, message = "expires_in 必须为大于 0 的秒数")
    @JsonProperty("expires_in")
    @JsonAlias("expiresIn")
    private Long expiresIn;

    /** 期望的权限范围（英文逗号分隔，可空表示取系统可自助范围） */
    @Size(max = 256, message = "scope 长度不能超过 256")
    private String scope;

    /** 目标客户端标识（可空，个人令牌固定 this_land_cloud） */
    @Size(max = 128, message = "client_id 长度不能超过 128")
    @JsonProperty("client_id")
    @JsonAlias("clientId")
    private String clientId;

    /** 自定义 payload 结构（可空，允许全空或部分为空） */
    private PatPayload payload;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public PatPayload getPayload() {
        return payload;
    }

    public void setPayload(PatPayload payload) {
        this.payload = payload;
    }
}
