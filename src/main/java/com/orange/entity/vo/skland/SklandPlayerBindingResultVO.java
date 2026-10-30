package com.orange.entity.vo.skland;

import java.util.List;

/**
 * 鹰角 token 换取的森空岛凭证与明日方舟角色绑定列表
 *
 * @author UserCenter
 */
public class SklandPlayerBindingResultVO {

    /** 森空岛登录凭据 */
    private String cred;

    /** 签名密钥 */
    private String token;

    /** 已绑定的明日方舟角色列表，无绑定时为空列表 */
    private List<SklandPlayerBindingItemVO> playerBindingList;

    public String getCred() {
        return cred;
    }

    public void setCred(String cred) {
        this.cred = cred;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public List<SklandPlayerBindingItemVO> getPlayerBindingList() {
        return playerBindingList;
    }

    public void setPlayerBindingList(List<SklandPlayerBindingItemVO> playerBindingList) {
        this.playerBindingList = playerBindingList;
    }
}
