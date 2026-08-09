package com.orange.service;

import com.orange.entity.vo.oauth.OAuthTokenVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * OAuth2 授权服务器核心服务：授权码签发、令牌交换、令牌刷新、令牌解析
 *
 * @author UserCenter
 */
public interface OAuthTokenService {

    /**
     * 完整授权流程：校验模式/登录态/客户端/回调白名单/scope/PKCE，签发授权码并拼装跳转地址
     *
     * @param responseType        响应类型（固定 code）
     * @param clientId            客户端 ID
     * @param redirectUri         回调地址（须在白名单内）
     * @param scope               申请的权限范围（可空）
     * @param state               防 CSRF 随机串（原样回传）
     * @param codeChallenge       PKCE code_challenge（可空）
     * @param codeChallengeMethod PKCE 算法（S256）
     * @param request             HTTP 请求（解析登录会话）
     * @return 302 跳转地址（含 code 与 state）
     */
    String buildAuthorizeRedirectUrl(String responseType, String clientId, String redirectUri, String scope,
                                     String state, String codeChallenge, String codeChallengeMethod,
                                     HttpServletRequest request);

    /**
     * 授权码签发：校验客户端/回调白名单/scope，生成一次性授权码并存储
     *
     * @param clientId            客户端 ID
     * @param redirectUri         回调地址（须在白名单内）
     * @param scope               申请的权限范围（可空，空则按客户端全部范围）
     * @param codeChallenge       PKCE code_challenge（可空）
     * @param codeChallengeMethod PKCE 算法，仅支持 S256
     * @param uid                 授权用户 uid
     * @return 一次性授权码
     */
    String createAuthorizationCode(String clientId, String redirectUri, String scope,
                                   String codeChallenge, String codeChallengeMethod, Long uid);

    /**
     * 令牌交换：authorization_code → access_token + refresh_token
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端传空）
     * @param code         授权码
     * @param redirectUri  回调地址（须与授权时一致）
     * @param codeVerifier PKCE code_verifier
     * @return 令牌响应
     */
    OAuthTokenVO exchangeToken(String clientId, String clientSecret, String code,
                               String redirectUri, String codeVerifier);

    /**
     * 刷新令牌：refresh_token 一次性轮换，返回新的 access_token + refresh_token
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端传空）
     * @param refreshToken 刷新令牌
     * @return 新的令牌响应
     */
    OAuthTokenVO refreshToken(String clientId, String clientSecret, String refreshToken);

    /**
     * 解析访问令牌，供用户信息等资源端点使用
     *
     * @param accessToken 访问令牌
     * @return 令牌主体信息
     */
    OAuthTokenPrincipal resolveAccessToken(String accessToken);

    /**
     * 访问令牌主体信息（uid + 客户端 + 范围）
     */
    class OAuthTokenPrincipal {

        /** 用户 uid */
        private final Long uid;

        /** 签发令牌的客户端 ID */
        private final String clientId;

        /** 授权范围 */
        private final String scope;

        /**
         * 构造令牌主体
         *
         * @param uid      用户 uid
         * @param clientId 客户端 ID
         * @param scope    授权范围
         */
        public OAuthTokenPrincipal(Long uid, String clientId, String scope) {
            this.uid = uid;
            this.clientId = clientId;
            this.scope = scope;
        }

        public Long getUid() {
            return uid;
        }

        public String getClientId() {
            return clientId;
        }

        public String getScope() {
            return scope;
        }
    }
}
