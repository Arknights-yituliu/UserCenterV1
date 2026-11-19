package com.orange.entity.dto.pat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

/**
 * 直连应用撤销个人访问令牌的请求参数
 *
 * <p>与用户侧一样支持按 id 撤销，也保留按明文撤销；两者必须且只能提供一个。
 * 服务端按 {@code uid + id} 或 {@code uid + token} 精确删除当前用户的 PAT（跨应用）。
 * 调用方应用必须开通直连认证能力。</p>
 *
 * @author UserCenter
 */
public class PatClientRevokeRequest {

    /** 待撤销的 PAT 主键，与用户侧一致 */
    private Long id;

    /** 待吊销的 PAT 明文 */
    @Size(max = 128, message = "token 长度不能超过 128")
    private String token;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @JsonIgnore
    @AssertTrue(message = "id 和 token 必须且只能提供一个，token 不能为空白")
    public boolean isTargetValid() {
        return id != null ? token == null : token != null && !token.isBlank();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
