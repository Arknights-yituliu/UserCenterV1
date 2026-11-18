package com.orange.entity.dto.pat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 应用侧凭 OAuth refresh_token 签发个人访问令牌（PAT）的请求参数
 *
 * <p>字段与 {@link PatIssueRequest} 基本一致，额外必填 {@code refresh_token}；<strong>不含
 * {@code client_id}</strong>——目标应用由凭证自身决定，不接受调用方指定，避免跨应用冒签。
 * 校验口径、响应结构与 {@code POST /user/pat} 保持一致。</p>
 *
 * @author UserCenter
 */
public class PatIssueByRefreshRequest {

    /** OAuth refresh_token 明文（本接口的唯一凭证，不消费、不轮转） */
    @NotBlank(message = "refresh_token 不能为空")
    @JsonProperty("refresh_token")
    @JsonAlias("refreshToken")
    private String refreshToken;

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

    /** 期望的权限范围（英文逗号分隔，可空表示取「凭证范围 ∩ 可自助范围」） */
    @Size(max = 256, message = "scope 长度不能超过 256")
    private String scope;

    /** 自定义 payload 结构（可空，允许全空或部分为空） */
    private PatPayload payload;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

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

    public PatPayload getPayload() {
        return payload;
    }

    public void setPayload(PatPayload payload) {
        this.payload = payload;
    }
}
