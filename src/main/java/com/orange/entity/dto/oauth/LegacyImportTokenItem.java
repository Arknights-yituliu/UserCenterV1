package com.orange.entity.dto.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 旧系统只读令牌导入单条记录（列表元素）
 *
 * <p>字段原始值统一以字符串承载，由服务层负责格式校验与错误码转换，
 * 避免参数解析异常绕过统一的错误码体系（否则非法 uid 会得到框架默认错误码而非 10001）。</p>
 *
 * @author UserCenter
 */
public class LegacyImportTokenItem {

    /** 用户名下 uid */
    private String uid;

    /** 旧系统只读令牌明文 */
    private String token;

    /** 令牌名称 */
    private String name;

    /** 令牌授权范围 */
    private String scope;

    /** 有效期（秒） */
    @JsonProperty("expires_in")
    private String expiresIn;

    /** 到期时间（yyyy-MM-dd HH:mm:ss 或 ISO 格式） */
    @JsonProperty("expire_time")
    private String expireTime;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(String expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }
}
