package com.orange.service;

import com.orange.entity.vo.oauth.ConsentInfoVO;
import com.orange.entity.vo.oauth.LoginTicketVO;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.OAuthClientGrantGroupVO;
import com.orange.entity.vo.oauth.UserInfoVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

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
     * 签发跨站登录票据：供登录页（与授权服务器跨站）登录成功后换取短时一次性凭证，
     * 随 authorize 请求携带，替代跨站不可用的会话 Cookie
     *
     * @param request HTTP 请求（须携带本系统会话 token：Authorization / UC-Token）
     * @return 一次性登录票据
     */
    LoginTicketVO createLoginTicket(HttpServletRequest request);

    /**
     * 生成授权确认单并返回确认页跳转地址：requireAuthConsent=1 的客户端授权时，
     * 暂不签发授权码，先把待确认参数存入 Redis 一次性确认单，由确认页同意后再签发
     *
     * @param clientId            客户端 ID
     * @param redirectUri         回调地址（须在白名单内）
     * @param scope               申请的权限范围（可空）
     * @param state               防 CSRF 随机串（原样回传）
     * @param codeChallenge       PKCE code_challenge（可空）
     * @param codeChallengeMethod PKCE 算法（S256）
     * @param uid                 授权用户 uid
     * @return 确认页跳转地址（含一次性 pending_id）
     */
    String buildConsentRedirectUrl(String clientId, String redirectUri, String scope,
                                   String state, String codeChallenge, String codeChallengeMethod, Long uid);

    /**
     * 查询授权确认信息：确认页加载时调用，校验登录态与确认单归属后返回客户端与权限信息
     *
     * @param pendingId 确认单 ID
     * @param request   HTTP 请求（解析登录会话）
     * @return 确认页展示信息
     */
    ConsentInfoVO getConsentInfo(String pendingId, HttpServletRequest request);

    /**
     * 确认/拒绝授权：同意则签发一次性授权码并返回回跳地址，
     * 拒绝则返回 redirect_uri?error=access_denied（确认单一次性消费）
     *
     * @param pendingId 确认单 ID
     * @param approve   是否同意授权
     * @param request   HTTP 请求（解析登录会话）
     * @return 302 回跳地址（含 code 或 error）
     */
    String confirmAuthorization(String pendingId, boolean approve, HttpServletRequest request);

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
     * 令牌交换：authorization_code → access_token；登记 refresh_token grant 时同时返回 refresh_token
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
     * 刷新令牌：refresh_token 为固定凭证，有效期内可反复使用；每次刷新只签发新的
     * access_token，不删除也不换发 refresh_token（响应仅含 access_token，不含
     * refresh_token / scope）
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端传空）
     * @param refreshToken 刷新令牌
     * @return 新的令牌响应
     */
    OAuthTokenVO refreshToken(String clientId, String clientSecret, String refreshToken);

    /**
     * 令牌签发统一入口：按授权类型分发
     * （authorization_code → 授权码换令牌，refresh_token → 刷新令牌），
     * 不支持的授权类型直接拒绝
     *
     * @param grantType    授权类型：authorization_code / refresh_token
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端传空）
     * @param code         授权码（authorization_code 时必填）
     * @param redirectUri  回调地址（authorization_code 时必填，须与授权时一致）
     * @param codeVerifier PKCE code_verifier
     * @param refreshToken 刷新令牌（refresh_token 时必填）
     * @return 令牌响应
     */
    OAuthTokenVO issueToken(String grantType, String clientId, String clientSecret,
                            String code, String redirectUri, String codeVerifier, String refreshToken);

    /**
     * 吊销令牌（RFC 7009）：客户端携带自己名下的 access_token / refresh_token 调用，
     * 使其立即失效。吊销 refresh_token 只使其本身失效（派生 access 由各自 TTL 自然过期，
     * 反向索引概率性惰性清理收敛）；令牌不存在或已失效同样视为成功（幂等，不泄露令牌是否有效）
     *
     * @param clientId     客户端 ID
     * @param clientSecret 客户端密钥（公共客户端传空）
     * @param token        要吊销的令牌（access_token / refresh_token 均可，自动识别）
     */
    void revokeToken(String clientId, String clientSecret, String token);

    /**
     * 解析访问令牌，供用户信息等资源端点使用
     *
     * @param accessToken 访问令牌
     * @return 令牌主体信息
     */
    OAuthTokenPrincipal resolveAccessToken(String accessToken);

    /**
     * 查询 OAuth 用户信息：根据令牌主体（uid + 客户端 + 授权范围）
     * 查库补齐用户基础资料并按 scope 组装响应（邮箱仅授权 user.email 时返回）
     *
     * @param uid      用户 uid
     * @param clientId 签发令牌的客户端 ID
     * @param scope    授权范围
     * @return 用户信息（uid、邮箱、用户名、昵称、头像）
     */
    UserInfoVO getUserInfo(Long uid, String clientId, String scope);

    /**
     * 查询指定用户名下仍有效的第三方应用授权，按应用（OAuth 客户端）分组返回。
     *
     * <p>一组对应一个授权过的应用：组头携带应用 ID 与名称，组内条目为逐次授权
     * （一条 = 一个有效 refresh_token）。组与组之间按该应用最近一次授权时间倒序。</p>
     *
     * @param uid 用户 uid
     * @return 按应用分组的授权列表
     */
    List<OAuthClientGrantGroupVO> listUserRefreshTokens(Long uid);

    /**
     * 撤销指定用户对某应用（OAuth 客户端）的授权（用户自助，按应用整体撤销）。
     *
     * <p>删除该用户在 clientId 下的全部 access/refresh token 及其反向索引成员，
     * 并把授权台账按 uid+clientId 置为已吊销；操作幂等，重复调用返回成功。</p>
     *
     * @param uid      用户 uid
     * @param clientId 要撤销授权的应用客户端 ID
     */
    void revokeClientAuthorization(Long uid, String clientId);

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
