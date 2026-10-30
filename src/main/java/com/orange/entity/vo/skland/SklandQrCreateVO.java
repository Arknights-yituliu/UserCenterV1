package com.orange.entity.vo.skland;

/**
 * 扫码登录会话：由前端自行把 qrContent 渲染为二维码
 *
 * @author UserCenter
 */
public class SklandQrCreateVO {

    /** 扫码会话 ID，轮询状态时回传 */
    private String scanId;

    /** 二维码内容（鹰角 deep link） */
    private String qrContent;

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
    }

    public String getQrContent() {
        return qrContent;
    }

    public void setQrContent(String qrContent) {
        this.qrContent = qrContent;
    }
}
