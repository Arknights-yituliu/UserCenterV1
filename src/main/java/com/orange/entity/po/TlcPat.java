package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 个人访问令牌实体（tlc_pat）
 *
 * <p>PAT 是与 OAuth access_token 并列的独立凭证类：不进 {@code /oauth2/**} 的令牌解析链路，
 * 不写 {@code uc:oauth:*} 任何键，消费端由 {@code /pat/**} 独立校验。</p>
 *
 * <p><strong>全链路明文（业务取舍）</strong>：{@code token} 直接保存令牌明文，不计算摘要；
 * 新签发的 72 字符令牌与迁移进来的 64 位 hex 旧令牌都原样入库，列表接口也回传明文。
 * 因此本表是敏感数据源，需配套库表权限、备份加密与日志脱敏。{@code clientId} 在用户自助
 * 签发时为 {@code this_land_cloud}，直连应用签发时为 refresh_token 所属应用。{@code payload}
 * 存的是自定义结构 {@code PatPayload} 序列化后的 JSON 文本，服务端默认不解读内容、唯一
 * 例外是消费端约定的 {@code game_uid}；反序列化时忽略未定义字段，缺失字段为 null。</p>
 *
 * @author UserCenter
 */
@TableName("tlc_pat")
public class TlcPat {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 uid */
    private Long uid;

    /** 签发来源：用户自助为 this_land_cloud，直连应用为 refresh_token 所属应用 */
    private String clientId;

    /** 用户命名的用途 */
    private String name;

    /** 令牌明文（新令牌 72 字符 / 迁移旧令牌 64 位 hex） */
    private String token;

    /** 令牌前 12 位，列表展示 */
    private String tokenPrefix;

    /** 签发时的权限快照（英文逗号分隔） */
    private String scope;

    /** 签发时指定的有效期（秒） */
    private Long expiresIn;

    /** 到期时间 */
    private LocalDateTime expireTime;

    /** 自定义 payload 结构的 JSON 文本（PatPayload 序列化结果），不参与鉴权 */
    private String payload;

    /** 最近一次使用时间（阶段二启用） */
    private LocalDateTime lastUsedAt;

    /** 记录创建时间（本表无 UPDATE 路径，故不设 update_time） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUid() {
        return uid;
    }

    public void setUid(Long uid) {
        this.uid = uid;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenPrefix() {
        return tokenPrefix;
    }

    public void setTokenPrefix(String tokenPrefix) {
        this.tokenPrefix = tokenPrefix;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
