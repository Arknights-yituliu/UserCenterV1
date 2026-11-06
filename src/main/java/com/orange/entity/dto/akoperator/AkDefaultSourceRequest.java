package com.orange.entity.dto.akoperator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 设置默认数据来源请求参数
 *
 * <p>{@code source} 与读取接口一致：manual 表示本人手动修改版，此时 {@code akUid}
 * 必须为当前用户中心 UID；skland 表示森空岛自动导入，此时 {@code akUid} 必须是
 * 当前用户已绑定的游戏账号。来源与归属键的归属校验由服务层完成。</p>
 *
 * @author UserCenter
 */
public class AkDefaultSourceRequest {

    /** 数据条目的归属键：skland 为游戏账号 UID，manual 为当前用户中心 UID */
    @NotBlank(message = "游戏账号UID不能为空")
    @Pattern(regexp = "^[\\x21-\\x7E]{1,32}$", message = "游戏账号UID必须为不超过32位的可见ASCII字符")
    private String akUid;

    /** 数据来源：skland 森空岛自动导入，manual 用户手动上传 */
    @NotBlank(message = "数据来源不能为空")
    private String source;

    /**
     * 获取数据条目的归属键
     *
     * @return skland 为游戏账号 UID，manual 为当前用户中心 UID
     */
    public String getAkUid() {
        return akUid;
    }

    /**
     * 设置数据条目的归属键
     *
     * @param akUid skland 为游戏账号 UID，manual 为当前用户中心 UID
     */
    public void setAkUid(String akUid) {
        this.akUid = akUid;
    }

    /**
     * 获取数据来源
     *
     * @return skland 或 manual
     */
    public String getSource() {
        return source;
    }

    /**
     * 设置数据来源
     *
     * @param source skland 或 manual
     */
    public void setSource(String source) {
        this.source = source;
    }
}
