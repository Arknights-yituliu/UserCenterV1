package com.orange.entity.vo.oauth;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前用户授权的第三方应用分组视图对象（按 client 分组返回）
 *
 * <p>组与组之间、组内条目之间均按授权时间倒序（该应用最近一次授权决定组序）。</p>
 *
 * @author UserCenter
 */
public class OAuthClientGrantGroupVO {

    /** 应用（OAuth 客户端）ID */
    private String clientId;

    /** 应用名称；应用已被删除/停用时为空 */
    private String clientName;

    /** 该应用下的授权条目列表（一条 = 一次授权，即一个有效 refresh_token） */
    private List<OAuthGrantItemVO> grants;

    /**
     * 默认构造：初始化空列表，便于服务端按 client 聚合时直接 add
     */
    public OAuthClientGrantGroupVO() {
        this.grants = new ArrayList<>();
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public List<OAuthGrantItemVO> getGrants() {
        return grants;
    }

    public void setGrants(List<OAuthGrantItemVO> grants) {
        this.grants = grants;
    }
}
