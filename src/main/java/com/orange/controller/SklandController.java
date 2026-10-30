package com.orange.controller;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.Result;
import com.orange.entity.dto.skland.HgTokenRequest;
import com.orange.entity.vo.skland.SklandCredTokenVO;
import com.orange.entity.vo.skland.SklandPlayerBindingResultVO;
import com.orange.entity.vo.skland.SklandQrCheckVO;
import com.orange.entity.vo.skland.SklandQrCreateVO;
import com.orange.service.SklandAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 森空岛鉴权接口（公开，无需登录）
 *
 * <p>仅做第三方接口中转，不落库、不绑定用户，返回的 cred/token 由调用方自行保管。
 * 因 as.hypergryph.com 不返回 CORS 头，浏览器无法直连，需经本服务中转。</p>
 *
 * @author UserCenter
 */
@Tag(name = "森空岛鉴权接口")
@RestController
@RequestMapping("/skland")
public class SklandController {

    private final SklandAuthService sklandAuthService;

    /**
     * 构造器注入服务
     *
     * @param sklandAuthService 森空岛鉴权服务
     */
    public SklandController(SklandAuthService sklandAuthService) {
        this.sklandAuthService = sklandAuthService;
    }

    /**
     * 通过鹰角官网 token 换取森空岛 cred 与签名 token
     *
     * @param request 请求体（token：鹰角官网 token）
     * @return 森空岛凭证与签名密钥
     */
    @Operation(summary = "通过鹰角官网 token 换取森空岛 cred 和 token")
    @PostMapping("/hg/cred-token")
    public Result<SklandCredTokenVO> getCredAndTokenByHgToken(@Valid @RequestBody HgTokenRequest request) {
        return Result.success(sklandAuthService.getCredAndTokenByHgToken(request.getToken()));
    }

    /**
     * 通过鹰角官网 token 换取森空岛凭证并查询明日方舟角色绑定
     *
     * @param request 请求体（token：鹰角官网 token）
     * @return 森空岛凭证与角色绑定列表
     */
    @Operation(summary = "通过鹰角网络通行证获取凭证、密匙与玩家绑定数据")
    @PostMapping("/hg/player-binding")
    public Result<SklandPlayerBindingResultVO> getPlayerBindingsByHgToken(
            @Valid @RequestBody HgTokenRequest request) {
        return Result.success(sklandAuthService.getPlayerBindingsByHgToken(request.getToken()));
    }

    /**
     * 申请森空岛扫码登录二维码：前端把 qrContent 渲染为二维码供鹰角 App 扫描
     *
     * @return 扫码会话（scanId + 二维码内容）
     */
    @Operation(summary = "申请森空岛扫码登录二维码")
    @PostMapping("/qr/create")
    public Result<SklandQrCreateVO> createQrLogin() {
        return Result.success(sklandAuthService.createQrLogin());
    }

    /**
     * 查询扫码状态：前端按 1.5~2 秒轮询，status=0 表示完成，可停止轮询
     *
     * @param scanId 扫码会话 ID
     * @return 扫码状态（非 0 继续轮询，0 携带 cred/token）
     */
    @Operation(summary = "查询扫码状态并换取森空岛凭证（status=0 即成功）")
    @PostMapping("/qr/check")
    public Result<SklandQrCheckVO> checkQrLogin(@RequestParam("scanId") String scanId) {
        if (scanId == null || scanId.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "scanId不能为空");
        }
        return Result.success(sklandAuthService.checkQrLogin(scanId.trim()));
    }
}
