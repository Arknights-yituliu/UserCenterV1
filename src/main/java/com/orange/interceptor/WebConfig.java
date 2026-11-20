package com.orange.interceptor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册拦截器
 *
 * <ul>
 *   <li>UserAuthInterceptor：仅校验用户会话（/user/** 用户自助面）</li>
 *   <li>OAuthAuthInterceptor：仅校验 OAuth access_token（/oauth2 下受令牌保护的资源端点）</li>
 *   <li>PatAuthInterceptor：仅校验个人访问令牌 PAT（/pat/** 消费面）</li>
 * </ul>
 *
 * <p>鉴权链路完全分离，互不纠缠：用户自助面（含配置、OAuth 客户端自助管理、
 * 第三方应用授权管理）只认用户会话并统一收口在 /user/** 下；
 * OAuth 资源接口只认 access_token，
 * client_id 均由各自登录上下文提供。
 * /oauth2 下其余端点为 OAuth 协议端点（authorize/consent/token/ticket/revoke），
 * 自带协议级认证，不在此注册；/oauth2/account/send-delete-code、/oauth2/account/delete
 * 凭请求体 refresh_token 发码及配合邮箱验证码注销账户，
 * POST /oauth2/pat 同理凭请求体 refresh_token 认证，
 * 因此单用 excludePathPatterns 排除根路径，子路径 /list、/revoke、/revoke-all 仍走 access_token。</p>
 *
 * @author UserCenter
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final UserAuthInterceptor userAuthInterceptor;
    private final OAuthAuthInterceptor oauthAuthInterceptor;
    private final PatAuthInterceptor patAuthInterceptor;

    /**
     * 构造器注入拦截器
     *
     * @param userAuthInterceptor  用户认证拦截器
     * @param oauthAuthInterceptor OAuth access_token 认证拦截器
     * @param patAuthInterceptor   PAT 认证拦截器
     */
    public WebConfig(UserAuthInterceptor userAuthInterceptor, OAuthAuthInterceptor oauthAuthInterceptor,
                     PatAuthInterceptor patAuthInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
        this.oauthAuthInterceptor = oauthAuthInterceptor;
        this.patAuthInterceptor = patAuthInterceptor;
    }

    /**
     * 注册拦截器
     *
     * @param registry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户侧接口：仅校验用户会话
        // （用户自助面统一收口到 /user/**：配置、OAuth 客户端自助管理 /user/oauth/client/**、
        //  第三方应用授权列表与吊销 /user/oauth/grants，均由 /user/** 通配兜住）
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/user/**", "/auth/logout");
        // OAuth 资源接口：需要 access_token 的接口统一走此拦截器
        // （/oauth2/userinfo 为 OAuthController 的用户信息端点，/oauth2/profile/** 为用户资料修改，
        //  /oauth2/config/** 为 OAuth 令牌版用户配置，
        //  /oauth2/schedule/** 为独立的排班接口，/oauth2/ak-accounts/** 为 OAuth 明日方舟游戏数据接口，
        //  /oauth2/ef-accounts/** 为 OAuth 终末地玩家数据接口，
        //  /oauth2/ef-pool-records/** 为 OAuth 终末地寻访记录接口）
        //  /oauth2/pat/list、/revoke、/revoke-all 为直连应用的 PAT 管理入口（direct_auth_enabled=1）；
        //  根路径 POST /oauth2/pat 凭请求体 refresh_token 签发，属协议级端点，单独从本拦截器排除
        registry.addInterceptor(oauthAuthInterceptor)
                .addPathPatterns("/oauth2/userinfo", "/oauth2/profile/**", "/oauth2/config/**",
                        "/oauth2/schedule/**", "/oauth2/ak-accounts/**",
                        "/oauth2/ef-accounts/**", "/oauth2/ef-pool-records/**",
                        "/oauth2/pat", "/oauth2/pat/**")
                .excludePathPatterns("/oauth2/pat");
        // PAT 消费面：只认个人访问令牌，按明文查 tlc_pat 后注入上下文
        // （/pat/** 不在上面两条通配范围内，漏配的表现是「接口能访问但没校验过」，
        //  验证时必须特意测「不带 PAT」这一场景）
        registry.addInterceptor(patAuthInterceptor)
                .addPathPatterns("/pat/**");
    }
}
