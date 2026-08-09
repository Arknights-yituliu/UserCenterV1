package com.orange.controller;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RequestUtil;
import com.orange.common.util.Result;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.UserInfoVO;
import com.orange.service.OAuthTokenService;
import com.orange.service.OAuthTokenService.OAuthTokenPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * （Authorization: Bearer {token} 或 X-Token），由网站后端在跳转前引导用户完成登录。</p>
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
    @Operation(summary = "OAuth 授权码签发（302 跳回回调地址）")
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
        response.sendRedirect(oauthTokenService.buildAuthorizeRedirectUrl(
                responseType, clientId, redirectUri, scope, state, codeChallenge, codeChallengeMethod, request));
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
            vo = oauthTokenService.exchangeToken(clientId, clientSecret, code, redirectUri, codeVerifier);
        } else if ("refresh_token".equals(grantType)) {
            vo = oauthTokenService.refreshToken(clientId, clientSecret, refreshToken);
        } else {
            throw new BusinessException(ResultCode.OAUTH_GRANT_INVALID);
        }
        return Result.success(vo);
    }

    /**
     * 获取当前授权用户信息：携带 access_token 调用
     *
     * @param request HTTP 请求（Authorization: Bearer access_token）
     * @return 用户信息（uid、clientId、scope）
     */
    @Operation(summary = "OAuth 用户信息")
    @GetMapping("/userinfo")
    public Result<UserInfoVO> userinfo(HttpServletRequest request) {
        OAuthTokenPrincipal principal = oauthTokenService.resolveAccessToken(RequestUtil.resolveToken(request));
        return Result.success(UserInfoVO.of(principal));
    }
}
