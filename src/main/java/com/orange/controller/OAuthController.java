package com.orange.controller;

import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.Result;
import com.orange.entity.vo.oauth.ConsentInfoVO;
import com.orange.entity.vo.oauth.LoginTicketVO;
import com.orange.entity.vo.oauth.OAuthConsentRequest;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.UserInfoVO;
import com.orange.service.OAuthTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final OAuthTokenService oauthTokenService;

    /**
     * 构造器注入依赖
     *
     * @param oauthTokenService OAuth 令牌服务
     */
    public OAuthController(OAuthTokenService oauthTokenService) {
        this.oauthTokenService = oauthTokenService;
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
        LogUtil.debug(OAuthController.class, "[OAuth] 签发跨站登录票据成功: expiresIn={}s", vo.getExpiresIn());
        return Result.success(vo);
    }

    /**
     * 授权确认信息查询：确认页加载时调用，校验登录态与确认单归属后返回客户端与权限信息
     *
     * @param pendingId 授权确认单 ID（authorize 302 携带）
     * @param request   HTTP 请求（Authorization: Bearer {token} 或 UC-Token）
     * @return 确认页展示信息（客户端名称、申请权限列表、回调地址）
     */
    @Operation(summary = "查询 OAuth 授权确认信息")
    @GetMapping("/consent/info")
    public Result<ConsentInfoVO> consentInfo(@RequestParam("pending_id") String pendingId,
                                             HttpServletRequest request) {
        ConsentInfoVO vo = oauthTokenService.getConsentInfo(pendingId, request);
        LogUtil.debug(OAuthController.class, "[OAuth] 查询授权确认信息: pendingId={}, clientId={}", mask(pendingId), vo.getClientId());
        return Result.success(vo);
    }

    /**
     * 授权确认/拒绝：同意则签发授权码并返回回跳地址，拒绝则回跳 error=access_denied
     * （确认单一次性消费，重复提交直接报错）
     *
     * <p>请求体为 JSON（Content-Type: application/json），
     * 如 {"pending_id":"xxx","approve":true}，由 OAuthConsentRequest 接收</p>
     *
     * @param body    授权确认请求体（确认单 ID + 是否同意）
     * @param request HTTP 请求（解析登录会话，Authorization: Bearer {token} 或 UC-Token）
     * @return 302 回跳地址（含 code 或 error），由前端执行跳转
     */
    @Operation(summary = "确认/拒绝 OAuth 授权")
    @PostMapping("/consent")
    public Result<String> consent(@RequestBody(required = false) OAuthConsentRequest body,
                                  HttpServletRequest request) {
        // 1. 校验请求体与必填参数（缺参时返回业务错误而非 500）
        if (body == null || !StringUtils.hasText(body.getPendingId()) || body.getApprove() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少确认单参数 pending_id 或 approve");
        }
        String pendingId = body.getPendingId();
        boolean approve = body.getApprove();
        // 2. 调用服务确认授权（同意签发授权码 / 拒绝回跳 access_denied）
        String redirectUrl = oauthTokenService.confirmAuthorization(pendingId, approve, request);
        LogUtil.debug(OAuthController.class, "[OAuth] 授权确认{}: pendingId={}", approve ? "同意" : "拒绝", mask(pendingId));
        return Result.success(redirectUrl);
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
        LogUtil.debug(OAuthController.class, "[OAuth] authorize 请求: clientId={}, redirectUri={}, scope={}, pkce={}({})",
                clientId, redirectUri, scope,
                StringUtils.hasText(codeChallenge) ? "yes" : "no", codeChallengeMethod);
        if (StringUtils.hasText(ucTicket)) {
            LogUtil.debug(OAuthController.class, "[OAuth] authorize 携带跨站登录票据");
        }
        String redirectUrl = oauthTokenService.buildAuthorizeRedirectUrl(
                responseType, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, request);
        LogUtil.debug(OAuthController.class, "[OAuth] authorize 302 跳转完成");
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
        // grant_type 分发与协议校验由 Service 统一入口完成
        OAuthTokenVO vo = oauthTokenService.issueToken(grantType, clientId, clientSecret,
                code, redirectUri, codeVerifier, refreshToken);
        LogUtil.debug(OAuthController.class, "[OAuth] 令牌签发成功: grantType={}, clientId={}, expiresIn={}s",
                grantType, clientId, vo.getExpiresIn());
        return Result.success(vo);
    }

    /**
     * 吊销令牌（RFC 7009）：客户端携带自己名下的 access_token / refresh_token 调用，
     * 使其立即失效（自动识别令牌类型）。吊销 refresh_token 只使其本身失效，此前派生的
     * access_token 按各自有效期自然过期；令牌不存在或已失效同样返回成功（幂等，不泄露令牌是否有效）
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端传空）
     * @param token        要吊销的令牌（access_token / refresh_token 均可）
     * @return 统一成功响应
     */
    @Operation(summary = "吊销 OAuth 令牌")
    @PostMapping("/revoke")
    public Result<Void> revoke(@RequestParam("client_id") String clientId,
                               @RequestParam(value = "client_secret", required = false) String clientSecret,
                               @RequestParam("token") String token) {
        oauthTokenService.revokeToken(clientId, clientSecret, token);
        LogUtil.debug(OAuthController.class, "[OAuth] 吊销令牌请求: clientId={}", clientId);
        return Result.success();
    }

    /**
     * 获取当前授权用户信息：access_token 由 OAuthAuthInterceptor 统一校验并注入上下文，
     * 查库与按 scope 组装由 Service 完成（便于无自有账户体系的接入方直接以 UC 用户作为登录账号）
     *
     * @return 用户信息（uid、邮箱、用户名、昵称、头像）
     */
    @Operation(summary = "OAuth 用户信息")
    @GetMapping("/userinfo")
    public Result<UserInfoVO> userinfo() {
        return Result.success(oauthTokenService.getUserInfo(
                UserContext.requireUid(), UserContext.getClientId(), UserContext.getScope()));
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
