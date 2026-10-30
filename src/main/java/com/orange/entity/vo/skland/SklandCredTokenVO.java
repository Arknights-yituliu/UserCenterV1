package com.orange.entity.vo.skland;

/**
 * 森空岛凭证与签名密钥
 *
 * <p>cred 用于请求头 {@code cred}，token 用于签名，均具备账号操作权限，前端不得写入日志。</p>
 *
 * @author UserCenter
 */
public class SklandCredTokenVO {

    /** 森空岛登录凭据 */
    private String cred;

    /** 签名密钥 */
    private String token;

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
}
