package com.orange.controller.app;

import com.orange.common.util.Result;
import com.orange.entity.dto.app.UserInfoRequest;
import com.orange.entity.dto.app.UserKickRequest;
import com.orange.entity.dto.app.VerifyTokenRequest;
import com.orange.entity.vo.app.AppUserVO;
import com.orange.service.AppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接入方 API（需 AppId/AppSecret 签名认证）
 *
 * <p>接入方网站通过本组接口完成登录态校验、用户信息拉取与踢下线</p>
 *
 * @author UserCenter
 */
@Tag(name = "接入方 API")
@RestController
@RequestMapping("/api/app")
public class AppController {

    private final AppService appService;

    /**
     * 构造器注入服务
     *
     * @param appService 接入方服务
     */
    public AppController(AppService appService) {
        this.appService = appService;
    }

    /**
     * 接入方健康检查
     *
     * @return 统一返回结果
     */
    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong");
    }

    /**
     * 校验用户 token，返回 uid / 用户信息
     *
     * @param request     校验请求
     * @param httpRequest HTTP 请求（取签名校验通过后的 AppId）
     * @return 用户信息
     */
    @Operation(summary = "校验用户 token")
    @PostMapping("/verify_token")
    public Result<AppUserVO> verifyToken(@Valid @RequestBody VerifyTokenRequest request, HttpServletRequest httpRequest) {
        String appId = (String) httpRequest.getAttribute("appId");
        return Result.success(appService.verifyToken(request.getToken(), appId));
    }

    /**
     * 按 uid 拉取用户全局 + 站点扩展资料
     *
     * @param request     查询请求
     * @param httpRequest HTTP 请求（取签名校验通过后的 AppId）
     * @return 用户信息
     */
    @Operation(summary = "按 uid 拉取用户信息")
    @PostMapping("/user/info")
    public Result<AppUserVO> userInfo(@Valid @RequestBody UserInfoRequest request, HttpServletRequest httpRequest) {
        String appId = (String) httpRequest.getAttribute("appId");
        return Result.success(appService.getUserInfo(request.getUid(), appId));
    }

    /**
     * 踢指定用户下线（删除该用户全部会话）
     *
     * @param request 踢下线请求
     * @return 统一返回结果
     */
    @Operation(summary = "踢用户下线")
    @PostMapping("/user/logout")
    public Result<Void> kickUser(@Valid @RequestBody UserKickRequest request) {
        appService.kickUser(request.getUid());
        return Result.success();
    }
}
