package com.orange.entity.vo.skland;

/**
 * 扫码状态查询结果：status 非 0 时前端继续轮询，0 表示已换取到凭证
 *
 * <p>森空岛状态约定：100/101/102 为等待、待确认等中间态，0 为用户已确认。</p>
 *
 * @author UserCenter
 */
public class SklandQrCheckVO {

    /** 森空岛返回的状态码 */
    private int status;

    /** 森空岛返回的提示信息 */
    private String msg;

    /** 用户确认后换取的森空岛登录凭据，未完成时为 null */
    private String cred;

    /** 用户确认后换取的签名密钥，未完成时为 null */
    private String token;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

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
