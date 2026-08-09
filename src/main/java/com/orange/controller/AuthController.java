package com.orange.controller;

import com.orange.common.util.RequestUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.auth.LoginRequest;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.dto.auth.SendCodeRequest;
import com.orange.entity.vo.auth.LoginVO;
import com.orange.service.AuthService;
import com.orange.service.EmailCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
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

    /** 会话 Cookie 名（与 RequestUtil 解析保持一致） */
    private static final String SESSION_COOKIE = "uc_token";

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    /** 会话有效期（秒）：Cookie 存活时间与会话一致 */
    @Value("${uc.session-ttl-seconds:15552000}")
    private long sessionTtlSeconds;

    /** Cookie 所属域名（可空：不设置则绑定当前主机） */
    @Value("${uc.oauth.cookie-domain:}")
    private String cookieDomain;

    /** Cookie 是否仅 HTTPS 传输（本地 HTTP 开发可设为 false） */
    @Value("${uc.oauth.cookie-secure:true}")
    private boolean cookieSecure;

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
     * 登录（密码 / 邮箱验证码），成功后写会话 Cookie（供纯前端浏览器跳转场景）
     *
     * @param request      登录参数
     * @param httpRequest  HTTP 请求（取 IP / UA / 来源应用）
     * @param response     HTTP 响应（写会话 Cookie）
     * @return 登录响应（含 token）
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
                                 HttpServletResponse response) {
        String appId = httpRequest.getHeader("X-App-Id");
        LoginVO vo = authService.login(request, RequestUtil.getIp(httpRequest),
                httpRequest.getHeader("User-Agent"), appId);
        writeSessionCookie(vo.getToken(), response);
        return Result.success(vo);
    }

    /**
     * 登出（需登录态，删除 Redis 会话），并清除会话 Cookie
     *
     * @param httpRequest HTTP 请求（取 token）
     * @param response    HTTP 响应（清 Cookie）
     * @return 统一返回结果
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest, HttpServletResponse response) {
        authService.logout(RequestUtil.resolveToken(httpRequest));
        clearSessionCookie(response);
        return Result.success();
    }

    /**
     * 写入会话 Cookie（HttpOnly + Secure + SameSite=Lax，浏览器跳转自动携带）
     *
     * @param token    会话 token
     * @param response HTTP 响应
     */
    private void writeSessionCookie(String token, HttpServletResponse response) {
        if (token == null || token.isBlank()) {
            return;
        }
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(sessionTtlSeconds);
        if (StringUtils.hasText(cookieDomain)) {
            builder.domain(cookieDomain);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    /**
     * 清除会话 Cookie（登出时调用）
     *
     * @param response HTTP 响应
     */
    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0);
        if (StringUtils.hasText(cookieDomain)) {
            builder.domain(cookieDomain);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}
