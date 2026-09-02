package com.orange.controller;

import com.orange.common.util.LogUtil;
import com.orange.common.util.RequestUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.auth.RegisterRequest;
import com.orange.entity.vo.auth.ServerLoginVO;
import com.orange.entity.vo.oauth.DirectLoginSessionVO;
import com.orange.entity.vo.oauth.DirectLoginTicketVO;
import com.orange.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 旧系统登录对接端点（基于 OAuth client 认证体系）
 *
 * <ul>
 *   <li>POST /oauth2/direct-*：直连登录四接口（发起会话/提交凭证/注册/兑换用户信息），
 *       登录/注册凭证由浏览器直接提交 UC，不经过旧系统后端</li>
 * </ul>
 *
 * @author UserCenter
 */
@Tag(name = "旧系统登录对接")
@RestController
@RequestMapping("/oauth2")
public class OAuthLegacyLoginController {

    private final AuthService authService;

    /**
     * 构造器注入依赖
     *
     * @param authService 认证服务（服务端登录/直连登录）
     */
    public OAuthLegacyLoginController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 直连登录-发起会话（旧系统后端调用）：以 client_id + client_secret 换取短时发起会话凭证，
     * 前端持凭证才能调直连登录接口，避免 client_secret 暴露给浏览器
     *
     * @param clientId     OAuth 客户端 ID
     * @param clientSecret 客户端密钥
     * @return 发起会话凭证（channel）及有效期
     */
    @Operation(summary = "直连登录-发起会话（旧系统后端调用）")
    @PostMapping("/direct-session")
    public Result<DirectLoginSessionVO> directSession(@RequestParam("client_id") String clientId,
                                                      @RequestParam("client_secret") String clientSecret,
                                                      @RequestParam(value = "source_ip", required = false) String sourceIp,
                                                      HttpServletRequest request) {
        String rateLimitIp = sourceIp == null || sourceIp.isBlank() ? RequestUtil.getIp(request) : sourceIp.trim();
        DirectLoginSessionVO vo = authService.createDirectSession(clientId, clientSecret, rateLimitIp);
        LogUtil.debug(OAuthLegacyLoginController.class, "[OAuth] 直连登录发起会话: clientId={}", clientId);
        return Result.success(vo);
    }

    /**
     * 直连登录-提交凭证（前端直接调用）：旧系统保持自家登录页，登录凭证（密码或邮箱验证码）
     * 由浏览器直接提交到 UC，校验通过后返回一次性登录票据（凭证不经过旧系统后端），
     * 前端将票据交给旧系统后端
     *
     * @param channel     发起会话凭证（旧系统后端签发）
     * @param accountType 登录方式：password=账号密码（默认）/ email=邮箱验证码
     * @param account     登录账号（密码方式为邮箱或用户名；邮箱方式为邮箱）
     * @param password    明文密码（密码方式必填）
     * @param code        邮箱验证码（邮箱方式必填）
     * @return 一次性登录票据及有效期
     */
    @Operation(summary = "直连登录-提交凭证（前端直接调用，凭证不经旧系统后端）")
    @PostMapping("/direct-login")
    public Result<DirectLoginTicketVO> directLogin(@RequestParam("channel") String channel,
                                                   @RequestParam(value = "account_type", required = false) String accountType,
                                                   @RequestParam("account") String account,
                                                   @RequestParam(value = "password", required = false) String password,
                                                   @RequestParam(value = "code", required = false) String code,
                                                   HttpServletRequest request) {
        DirectLoginTicketVO vo = authService.directLogin(channel, accountType, account, password, code,
                RequestUtil.getIp(request));
        LogUtil.debug(OAuthLegacyLoginController.class, "[OAuth] 直连登录成功: accountType={}", accountType);
        return Result.success(vo);
    }

    /**
     * 直连注册（前端直接调用）：旧系统保持自家注册页，注册信息（含密码/验证码）由浏览器
     * 直接提交到 UC，创建用户后返回一次性登录票据（注册信息不经过旧系统后端），
     * 前端将票据交给旧系统后端，由后端凭票据兑换用户信息（复用 /oauth2/direct-user）
     *
     * @param channel      发起会话凭证（旧系统后端签发）
     * @param registerType 注册方式：password=密码注册 / email_code=邮箱验证码注册
     * @param email        邮箱（与用户名至少一个；填了邮箱需提供验证码）
     * @param userName     用户名（可选，3-20 位字母数字下划线）
     * @param password     密码（必填，6-32 位）
     * @param code         邮箱验证码（填邮箱时必填）
     * @param nickname     昵称（可选）
     * @param request      HTTP 请求（取注册 IP）
     * @return 一次性登录票据及有效期
     */
    @Operation(summary = "直连注册（前端直接调用，注册信息不经旧系统后端）")
    @PostMapping("/direct-register")
    public Result<DirectLoginTicketVO> directRegister(@RequestParam("channel") String channel,
                                                      @RequestParam("register_type") String registerType,
                                                      @RequestParam(value = "email", required = false) String email,
                                                      @RequestParam(value = "user_name", required = false) String userName,
                                                      @RequestParam(value = "password", required = false) String password,
                                                      @RequestParam(value = "code", required = false) String code,
                                                      @RequestParam(value = "nickname", required = false) String nickname,
                                                      HttpServletRequest request) {
        RegisterRequest req = new RegisterRequest();
        req.setRegisterType(registerType);
        req.setEmail(email);
        req.setUserName(userName);
        req.setPassword(password);
        req.setVerificationCode(code);
        req.setNickname(nickname);
        DirectLoginTicketVO vo = authService.directRegister(channel, req, RequestUtil.getIp(request));
        LogUtil.debug(OAuthLegacyLoginController.class, "[OAuth] 直连注册成功: registerType={}", registerType);
        return Result.success(vo);
    }

    /**
     * 直连登录-兑换用户信息（旧系统后端调用）：凭一次性登录票据兑换用户信息，
     * 校验票据归属该 client 且未被消费
     *
     * @param clientId     OAuth 客户端 ID
     * @param clientSecret 客户端密钥
     * @param ticket       一次性登录票据
     * @return 用户信息（uid/昵称/头像/脱敏邮箱/状态）
     */
    @Operation(summary = "直连登录-兑换用户信息（旧系统后端调用）")
    @PostMapping("/direct-user")
    public Result<ServerLoginVO> directUser(@RequestParam("client_id") String clientId,
                                            @RequestParam("client_secret") String clientSecret,
                                            @RequestParam("ticket") String ticket) {
        ServerLoginVO vo = authService.directUser(clientId, clientSecret, ticket);
        LogUtil.debug(OAuthLegacyLoginController.class, "[OAuth] 直连登录兑换成功: clientId={}, uid={}", clientId, vo.getUid());
        return Result.success(vo);
    }
}
