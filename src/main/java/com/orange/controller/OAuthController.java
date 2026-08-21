package com.orange.controller;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RequestUtil;
import com.orange.common.util.Result;
import com.orange.entity.po.UserInfo;
import com.orange.entity.vo.oauth.LoginTicketVO;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.UserInfoVO;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.OAuthTokenService;
import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * OAuth2 授权服务器端点
 *
 * <ul>
 *   <li>GET /oauth2/authorize：授权码签发，第三方 Web 网站引导浏览器跳转至此，校验通过后 302 跳回回调地址</li>
 *   <li>POST /oauth2/token：授权码换令牌、刷新令牌</li>
 * </ul>
 *
 * <p>登录态说明：本系统为纯 API 服务，授权请求需携带本系统登录会话
 * （Authorization: Bearer {token} 或 UC-Token），由网站后端在跳转前引导用户完成登录。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 授权接口")
@RestController
@RequestMapping("/oauth2")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);

    private final OAuthTokenService oauthTokenService;

    private final UserInfoMapper userInfoMapper;

    /**
     * 构造器注入依赖
     *
     * @param oauthTokenService OAuth 令牌服务
     * @param userInfoMapper    用户表 Mapper（userinfo 组装用户资料）
     */
    public OAuthController(OAuthTokenService oauthTokenService, UserInfoMapper userInfoMapper) {
        this.oauthTokenService = oauthTokenService;
        this.userInfoMapper = userInfoMapper;
    }

    /**
     * 授权码签发：参数校验与流程编排在 Service 完成，此处仅输出 302 跳转
     *
     * @param responseType        固定为 code
     * @param clientId            客户端 ID
     * @param redirectUri         回调地址
     * @param scope               申请的权限范围（可空）
     * @param state               防 CSRF 随机串（原样回传）
     * @param codeChallenge       PKCE code_challenge（可空）
     * @param codeChallengeMethod PKCE 算法（S256）
     * @param request             HTTP 请求（解析登录会话）
     * @param response            HTTP 响应（302 跳转）
     * @throws IOException 跳转失败时抛出
     */
    @Operation(summary = "OAuth 授权码签发（GET，302 跳回回调地址）")
    @GetMapping("/authorize")
    public void authorize(@RequestParam("response_type") String responseType,
                          @RequestParam("client_id") String clientId,
                          @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                          @RequestParam(required = false) String scope,
                          @RequestParam(required = false) String state,
                          @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                          @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        sendAuthorizeRedirect(responseType, clientId, redirectUri, scope, state,
                codeChallenge, codeChallengeMethod, request, response);
    }

    /**
     * OAuth 授权码签发（POST 变体）：跨站登录页可用表单 POST 携带 uc_ticket 于请求体，
     * 避免登录票据出现在 URL/日志中；参数与 GET 完全一致
     */
    @Operation(summary = "OAuth 授权码签发（POST，跨站登录页表单提交）")
    @PostMapping("/authorize")
    public void authorizePost(@RequestParam("response_type") String responseType,
                              @RequestParam("client_id") String clientId,
                              @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                              @RequestParam(required = false) String scope,
                              @RequestParam(required = false) String state,
                              @RequestParam(value = "code_challenge", required = false) String codeChallenge,
                              @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
                              HttpServletRequest request,
                              HttpServletResponse response) throws IOException {
        sendAuthorizeRedirect(responseType, clientId, redirectUri, scope, state,
                codeChallenge, codeChallengeMethod, request, response);
    }

    /**
     * 签发跨站登录票据：登录页登录成功后携带本系统会话 token 调用，换取短时一次性凭证，
     * 供跳转 authorize 时携带（替代跨站不可用的会话 Cookie）
     *
     * @param request HTTP 请求（Authorization: Bearer {token} 或 UC-Token）
     * @return 一次性登录票据
     */
    @Operation(summary = "签发跨站登录票据")
    @PostMapping("/ticket")
    public Result<LoginTicketVO> ticket(HttpServletRequest request) {
        LoginTicketVO vo = oauthTokenService.createLoginTicket(request);
        log.debug("[OAuth] 签发跨站登录票据成功: ticket={}, expiresIn={}s", mask(vo.getTicket()), vo.getExpiresIn());
        return Result.success(vo);
    }

    /**
     * 授权码签发流程编排：调用服务签发 302 跳转地址并输出
     *
     * @param responseType        固定为 code
     * @param clientId            客户端 ID
     * @param redirectUri         回调地址
     * @param scope               申请的权限范围（可空）
     * @param state               防 CSRF 随机串（原样回传）
     * @param codeChallenge       PKCE code_challenge（可空）
     * @param codeChallengeMethod PKCE 算法（S256）
     * @param request             HTTP 请求（解析登录会话/uc_ticket）
     * @param response            HTTP 响应（302 跳转）
     * @throws IOException 跳转失败时抛出
     */
    private void sendAuthorizeRedirect(String responseType, String clientId, String redirectUri, String scope,
                                       String state, String codeChallenge, String codeChallengeMethod,
                                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        String ucTicket = request.getParameter("uc_ticket");
        log.debug("[OAuth] authorize 请求: clientId={}, redirectUri={}, scope={}, state={}, pkce={}({})",
                clientId, redirectUri, scope, state,
                StringUtils.hasText(codeChallenge) ? "yes" : "no", codeChallengeMethod);
        if (StringUtils.hasText(ucTicket)) {
            log.debug("[OAuth] authorize 携带跨站登录票据: uc_ticket={}", mask(ucTicket));
        }
        String redirectUrl = oauthTokenService.buildAuthorizeRedirectUrl(
                responseType, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, request);
        log.debug("[OAuth] authorize 302 跳转: {}", redirectUrl);
        response.sendRedirect(redirectUrl);
    }

    /**
     * 令牌交换 / 刷新
     *
     * @param grantType    授权类型：authorization_code / refresh_token
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端可空）
     * @param code         授权码（authorization_code 时必填）
     * @param redirectUri  回调地址（authorization_code 时必填）
     * @param codeVerifier PKCE code_verifier
     * @param refreshToken 刷新令牌（refresh_token 时必填）
     * @return 令牌响应
     */
    @Operation(summary = "OAuth 令牌交换/刷新")
    @PostMapping("/token")
    public Result<OAuthTokenVO> token(@RequestParam("grant_type") String grantType,
                                      @RequestParam("client_id") String clientId,
                                      @RequestParam(value = "client_secret", required = false) String clientSecret,
                                      @RequestParam(value = "code", required = false) String code,
                                      @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                                      @RequestParam(value = "code_verifier", required = false) String codeVerifier,
                                      @RequestParam(value = "refresh_token", required = false) String refreshToken) {
        OAuthTokenVO vo;
        if ("authorization_code".equals(grantType)) {
            log.debug("[OAuth] 授权码换令牌: clientId={}, redirectUri={}, code={}, codeVerifier={}",
                    clientId, redirectUri, mask(code), StringUtils.hasText(codeVerifier) ? "yes" : "no");
            vo = oauthTokenService.exchangeToken(clientId, clientSecret, code, redirectUri, codeVerifier);
            log.debug("[OAuth] 授权码换令牌成功: clientId={}, accessToken={}, expiresIn={}s",
                    clientId, mask(vo.getAccessToken()), vo.getExpiresIn());
        } else if ("refresh_token".equals(grantType)) {
            log.debug("[OAuth] 刷新令牌: clientId={}, refreshToken={}", clientId, mask(refreshToken));
            vo = oauthTokenService.refreshToken(clientId, clientSecret, refreshToken);
            log.debug("[OAuth] 刷新令牌成功: clientId={}, accessToken={}, refreshToken={}",
                    clientId, mask(vo.getAccessToken()), mask(vo.getRefreshToken()));
        } else {
            log.warn("[OAuth] 不支持的 grant_type: {}", grantType);
            throw new BusinessException(ResultCode.OAUTH_GRANT_INVALID);
        }
        return Result.success(vo);
    }

    /**
     * 获取当前授权用户信息：携带 access_token 调用
     *
     * <p>令牌解析出 uid 后查库补齐用户基础资料（邮箱/用户名/昵称/头像），
     * 便于无自有账户体系的接入方直接以 UC 用户作为登录账号。</p>
     *
     * @param request HTTP 请求（Authorization: Bearer access_token）
     * @return 用户信息（uid、邮箱、用户名、昵称、头像）
     */
    @Operation(summary = "OAuth 用户信息")
    @GetMapping("/userinfo")
    public Result<UserInfoVO> userinfo(HttpServletRequest request) {
        OAuthTokenPrincipal principal = oauthTokenService.resolveAccessToken(RequestUtil.resolveToken(request));
        UserInfo user = userInfoMapper.selectById(principal.getUid());
        log.debug("[OAuth] 用户信息: uid={}, clientId={}, scope={}",
                principal.getUid(), principal.getClientId(), principal.getScope());
        return Result.success(UserInfoVO.of(principal, user));
    }

    /**
     * 敏感令牌脱敏：仅保留前 8 位用于日志关联，避免完整令牌泄漏到日志
     *
     * @param token 原始令牌
     * @return 脱敏后的令牌
     */
    private static String mask(String token) {
        if (token == null || token.isEmpty()) {
            return token;
        }
        return token.length() <= 8 ? token : token.substring(0, 8) + "***";
    }
}
