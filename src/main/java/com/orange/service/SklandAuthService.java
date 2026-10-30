package com.orange.service;

import com.orange.entity.vo.skland.SklandCredTokenVO;
import com.orange.entity.vo.skland.SklandPlayerBindingResultVO;
import com.orange.entity.vo.skland.SklandQrCheckVO;
import com.orange.entity.vo.skland.SklandQrCreateVO;

/**
 * 森空岛鉴权服务：鹰角 token 换凭证、扫码登录、玩家绑定查询
 *
 * <p>两条链路：</p>
 * <ol>
 *   <li>hg token 直接换取：grant 换一次性 code → generate_cred_by_code 换 cred/token；</li>
 *   <li>扫码登录：申请二维码 → 轮询状态 → scanCode 换通行证 token → 走链路 1。</li>
 * </ol>
 *
 * <p>cred 与 token 均具备账号操作权限，服务内部不记录明文，调用方也不得写日志。</p>
 *
 * @author UserCenter
 */
public interface SklandAuthService {

    /**
     * 通过鹰角官网 token 换取森空岛 cred 与签名 token
     *
     * @param hgToken 鹰角通行证 token
     * @return 森空岛凭证与签名密钥
     */
    SklandCredTokenVO getCredAndTokenByHgToken(String hgToken);

    /**
     * 申请扫码登录二维码
     *
     * @return 扫码会话（scanId + 二维码内容）
     */
    SklandQrCreateVO createQrLogin();

    /**
     * 查询扫码状态，用户确认后自动换取森空岛凭证
     *
     * @param scanId 扫码会话 ID
     * @return 扫码状态（非 0 继续轮询，0 时携带 cred/token）
     */
    SklandQrCheckVO checkQrLogin(String scanId);

    /**
     * 通过鹰角官网 token 换取森空岛凭证并查询明日方舟角色绑定
     *
     * @param hgToken 鹰角通行证 token
     * @return 森空岛凭证与角色绑定列表
     */
    SklandPlayerBindingResultVO getPlayerBindingsByHgToken(String hgToken);
}
