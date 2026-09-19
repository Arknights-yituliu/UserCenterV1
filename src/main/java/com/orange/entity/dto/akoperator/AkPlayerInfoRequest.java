package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 游戏角色信息请求参数（保存接口每次必须携带）
 *
 * <p>服务端用 {@code akUid} 与请求路径做一致性校验；数据库行 id 与 create_time 不由客户端指定。</p>
 *
 * @author UserCenter
 */
public class AkPlayerInfoRequest {

    /** 游戏账号 UID，必须与请求路径中的 akUid 一致 */
    @NotBlank(message = "游戏账号UID不能为空")
    @Pattern(regexp = "^[\\x21-\\x7E]{1,32}$", message = "游戏账号UID必须为不超过32位的可见ASCII字符")
    private String akUid;

    public String getAkUid() {
        return akUid;
    }

    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }
}
