package com.orange.entity.dto.pat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 应用侧按明文吊销个人访问令牌的请求参数
 *
 * <p>调用方手里只有令牌本身（拿不到用户侧的列表 id），因此按明文提交，服务端按
 * {@code uid + client_id + token} 三条件精确删除。</p>
 *
 * @author UserCenter
 */
public class PatClientRevokeRequest {

    /** 待吊销的 PAT 明文 */
    @NotBlank(message = "token 不能为空")
    @Size(max = 128, message = "token 长度不能超过 128")
    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
