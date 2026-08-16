package com.orange.controller;

import com.orange.common.util.RequestUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.dto.auth.ResetCodeRequest;
import com.orange.entity.dto.auth.ResetPasswordRequest;
import com.orange.entity.dto.auth.SendCodeRequest;
import com.orange.entity.vo.auth.LoginVO;
import com.orange.service.AuthService;
import com.orange.service.EmailCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：发送验证码、注册、登录、登出
 *
 * @author UserCenter
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    /**
     * 构造器注入服务
     *
     * @param authService      认证服务
     * @param emailCodeService 验证码服务
     */
    public AuthController(AuthService authService, EmailCodeService emailCodeService) {
        this.authService = authService;
        this.emailCodeService = emailCodeService;
    }

    /**
     * 发送邮箱验证码（带限流）
     *
     * @param request     发送验证码参数
     * @param httpRequest HTTP 请求（取 IP）
     * @return 统一返回结果
     */
    @Operation(summary = "发送邮箱验证码")
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody SendCodeRequest request, HttpServletRequest httpRequest) {
        emailCodeService.sendCode(request.getEmail(), request.getUsage(), RequestUtil.getIp(httpRequest));
        return Result.success();
    }

    /**
     * 注册（密码注册 / 邮箱验证码注册），成功后自动登录
     *
     * @param request      注册参数
     * @param httpRequest  HTTP 请求（取 IP / 来源应用）
     * @return 登录响应（含 token）
     */
    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<LoginVO> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        String appId = httpRequest.getHeader("UC-App-Id");
        return Result.success(authService.register(request, RequestUtil.getIp(httpRequest), appId));
    }

    /**
     * 登录（密码 / 邮箱验证码），成功后返回会话 token（由前端自行携带：Authorization / UC-Token）
     *
     * @param request      登录参数
     * @param httpRequest  HTTP 请求（取 IP / UA / 来源应用）
     * @return 登录响应（含 token）
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String appId = httpRequest.getHeader("UC-App-Id");
        LoginVO vo = authService.login(request, RequestUtil.getIp(httpRequest),
                httpRequest.getHeader("User-Agent"), appId);
        return Result.success(vo);
    }

    /**
     * 发送重设密码验证码（发到账号绑定的邮箱，无需登录）
     *
     * @param request     发送参数（账号 = 邮箱或用户名）
     * @param httpRequest HTTP 请求（取 IP）
     * @return 统一返回结果
     */
    @Operation(summary = "发送重设密码验证码")
    @PostMapping("/reset-code")
    public Result<Void> resetCode(@Valid @RequestBody ResetCodeRequest request, HttpServletRequest httpRequest) {
        authService.sendResetCode(request.getAccount(), RequestUtil.getIp(httpRequest));
        return Result.success();
    }

    /**
     * 通过邮箱验证码重设密码（无需登录），成功后踢出全部会话
     *
     * @param request 重设参数
     * @return 统一返回结果
     */
    @Operation(summary = "重设密码")
    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.success();
    }

    /**
     * 登出（需登录态，删除 Redis 会话）
     *
     * @param httpRequest HTTP 请求（取 token）
     * @return 统一返回结果
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(RequestUtil.resolveToken(httpRequest));
        return Result.success();
    }
}
