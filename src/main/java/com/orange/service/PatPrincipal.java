package com.orange.service;

import com.orange.entity.dto.pat.PatPayload;

/**
 * PAT 令牌主体：消费端 {@code /pat/**} 的解析结果
 *
 * <p>承载一枚令牌在请求期需要的全部属性：身份（uid / clientId / scope）、凭证形态
 * （{@link #isPrefixed()}）与自定义 payload（{@link #getPayload()}）。</p>
 *
 * <p>后续新增的令牌级字段都加在这里，不再往 {@link com.orange.common.context.UserContext}
 * 里堆——后者只保留三个认证面共有的身份字段。这样字段增长时改动集中在
 * 「{@code tlc_pat} 加列 → {@code TlcPat} 加属性 → 本类加属性」，上下文类不必跟着改，
 * 也不会有「线程复用时漏清理某个字段」的风险。</p>
 *
 * <p>由 {@link PatService#resolve(String)} 构造，{@link com.orange.common.context.PatContext} 持有。</p>
 *
 * @author UserCenter
 */
public class PatPrincipal {

    /** 所属用户 */
    private final Long uid;

    /** 签发时记录的客户端标识 */
    private final String clientId;

    /** 签发时的权限快照（英文逗号分隔） */
    private final String scope;

    /** 自定义 payload，解析失败或未配置时为空对象（不为 null） */
    private final PatPayload payload;

    /** 明文是否带 {@code tlc_pat_} 前缀：false 表示迁移进来的旧令牌 */
    private final boolean prefixed;

    /**
     * 构造令牌主体
     *
     * @param uid      所属用户
     * @param clientId 客户端标识
     * @param scope    权限快照
     * @param payload  自定义 payload，未配置时传空对象
     * @param prefixed 明文是否带 {@code tlc_pat_} 前缀
     */
    public PatPrincipal(Long uid, String clientId, String scope, PatPayload payload,
                        boolean prefixed) {
        this.uid = uid;
        this.clientId = clientId;
        this.scope = scope;
        this.payload = payload;
        this.prefixed = prefixed;
    }

    public Long getUid() {
        return uid;
    }

    public String getClientId() {
        return clientId;
    }

    public String getScope() {
        return scope;
    }

    public PatPayload getPayload() {
        return payload;
    }

    /**
     * 明文是否带 {@code tlc_pat_} 前缀
     *
     * <p>消费端据此区分两代令牌：带前缀的新令牌按 payload 约定取账号，
     * 无前缀的旧令牌沿用「未指定账号」的默认来源语义。</p>
     *
     * @return true=新令牌；false=迁移进来的旧令牌
     */
    public boolean isPrefixed() {
        return prefixed;
    }
}
