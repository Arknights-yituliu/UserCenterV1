package com.lhs.uc.controller;

import com.lhs.uc.common.util.RequestUtil;
import com.lhs.uc.common.util.Result;
import com.lhs.uc.entity.dto.auth.LoginRequest;
import com.lhs.uc.entity.dto.auth.RegisterRequest;
import com.lhs.uc.entity.dto.auth.SendCodeRequest;
import com.lhs.uc.entity.vo.auth.LoginVO;
import com.lhs.uc.service.AuthService;
import com.lhs.uc.service.EmailCodeService;
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
        String appId = httpRequest.getHeader("X-App-Id");
        return Result.success(authService.register(request, RequestUtil.getIp(httpRequest), appId));
    }

    /**
     * 登录（密码 / 邮箱验证码）
     *
     * @param request      登录参数
     * @param httpRequest  HTTP 请求（取 IP / UA / 来源应用）
     * @return 登录响应（含 token）
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String appId = httpRequest.getHeader("X-App-Id");
        return Result.success(authService.login(request, RequestUtil.getIp(httpRequest),
                httpRequest.getHeader("User-Agent"), appId));
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
