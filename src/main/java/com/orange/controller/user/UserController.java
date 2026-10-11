package com.orange.controller.user;

import com.orange.common.context.UserContext;
import com.orange.common.util.LogUtil;
import com.orange.common.util.RequestUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.user.BindEmailRequest;
import com.orange.entity.dto.user.ChangeEmailRequest;
import com.orange.entity.dto.user.ClientAuthorizationRevokeRequest;
import com.orange.entity.dto.user.UpdatePasswordRequest;
import com.orange.entity.dto.user.UpdateProfileRequest;
import com.orange.entity.dto.user.UserCustomTokenRevokeRequest;
import com.orange.entity.dto.user.UserScopeRequest;
import com.orange.entity.vo.SessionVO;
import com.orange.entity.vo.UserInfoVO;
import com.orange.entity.vo.oauth.OAuthClientGrantGroupVO;
import com.orange.entity.vo.oauth.OAuthCustomTokenVO;
import com.orange.entity.vo.oauth.OAuthTokenVO;
import com.orange.entity.vo.oauth.OAuthUserScopeVO;
import com.orange.service.OAuthTokenService;
import com.orange.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户自助接口（需登录态）
 *
 * @author UserCenter
 */
@Tag(name = "用户自助接口")
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    private final OAuthTokenService oauthTokenService;

    /**
     * 构造器注入服务
     *
     * @param userService       用户服务
     * @param oauthTokenService OAuth 令牌服务（查询我的第三方应用授权）
     */
    public UserController(UserService userService, OAuthTokenService oauthTokenService) {
        this.userService = userService;
        this.oauthTokenService = oauthTokenService;
    }

    /**
     * 获取当前用户资料
     *
     * @return 用户信息
     */
    @Operation(summary = "获取当前用户资料")
    @GetMapping("/profile")
    public Result<UserInfoVO> getProfile() {
        return Result.success(userService.getProfile(UserContext.requireUid()));
    }

    /**
     * 修改个人资料（昵称/头像）
     *
     * @param request 修改参数
     * @return 统一返回结果
     */
    @Operation(summary = "修改个人资料")
    @PostMapping("/profile/update")
    public Result<Void> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(UserContext.requireUid(), request);
        return Result.success();
    }

    /**
     * 修改密码（需旧密码）
     *
     * @param request 修改参数
     * @return 统一返回结果
     */
    @Operation(summary = "修改密码")
    @PostMapping("/password")
    public Result<Void> updatePassword(@Valid @RequestBody UpdatePasswordRequest request) {
        userService.updatePassword(UserContext.requireUid(), request);
        return Result.success();
    }

    /**
     * 绑定邮箱（面向无邮箱用户，验证新邮箱所有权）
     *
     * @param request 绑定参数
     * @return 统一返回结果
     */
    @Operation(summary = "绑定邮箱")
    @PostMapping("/email/bind")
    public Result<Void> bindEmail(@Valid @RequestBody BindEmailRequest request) {
        userService.bindEmail(UserContext.requireUid(), request);
        return Result.success();
    }

    /**
     * 发送换绑邮箱验证码（登录态，发到当前绑定邮箱，前端无需传邮箱）
     *
     * @param httpRequest HTTP 请求（取 IP 限流）
     * @return 统一返回结果
     */
    @Operation(summary = "发送换绑邮箱验证码")
    @PostMapping("/email/send-change-code")
    public Result<Void> sendChangeEmailCode(HttpServletRequest httpRequest) {
        userService.sendChangeEmailCode(UserContext.requireUid(), RequestUtil.getIp(httpRequest));
        return Result.success();
    }

    /**
     * 换绑邮箱（需同时验证旧邮箱与新邮箱）
     *
     * @param request 换绑参数
     * @return 统一返回结果
     */
    @Operation(summary = "换绑邮箱")
    @PostMapping("/email/change")
    public Result<Void> changeEmail(@Valid @RequestBody ChangeEmailRequest request) {
        userService.changeEmail(UserContext.requireUid(), request);
        return Result.success();
    }

    /**
     * 查看当前用户的登录设备列表
     *
     * @return 会话列表
     */
    @Operation(summary = "查看登录设备列表")
    @GetMapping("/sessions")
    public Result<List<SessionVO>> listSessions() {
        return Result.success(userService.listSessions(UserContext.requireUid()));
    }

    /**
     * 查看我授权过的第三方应用列表（按应用分组：一组一条应用，组内为逐次授权）
     *
     * @return 按应用分组的授权列表
     */
    @Operation(summary = "查看我的第三方应用授权列表")
    @GetMapping("/oauth/grants")
    public Result<List<OAuthClientGrantGroupVO>> listRefreshTokens() {
        return Result.success(oauthTokenService.listUserRefreshTokens(UserContext.requireUid()));
    }

    /**
     * 查看我生成的自定义有效期 access_token 列表（含已过期记录）
     *
     * <p>对应「直签令牌」接口（POST /user/oauth/custom-tokens、POST /oauth2/token/from-refresh）签发的令牌；返回 token 明文，
     * 过期与否由 {@code expiresInSeconds}（剩余秒数，≤0 即已过期）体现，不依赖定时任务。</p>
     *
     * @return 自定义令牌列表，按签发时间倒序
     */
    @Operation(summary = "查看我的自定义有效期令牌列表")
    @GetMapping("/oauth/custom-tokens")
    public Result<List<OAuthCustomTokenVO>> listCustomTokens() {
        return Result.success(oauthTokenService.listUserCustomTokens(UserContext.requireUid()));
    }

    /**
     * 使用当前登录会话直接签发指定有效期的自定义 access_token
     *
     * <p>面向本系统自有前端：登录态由 UserAuthInterceptor 从请求头会话解析并注入 UserContext，
     * 因此无需在请求体回传会话令牌。权限基准为用户对目标应用的有效授权（受用户自定义授权范围约束）；
     * 可选传 scope 做权限收敛，只能指定「凭证已有权限」的子集换取更小权限的短时令牌
     * （最小权限实践），超出凭证范围会被拒绝，本接口不能用于扩权。</p>
     *
     * <p>name 为必填：签发成功后落一条自定义令牌记录，供用户在「我的自定义令牌」列表查看。</p>
     *
     * @param clientId  目标客户端 ID（会话来源客户端缺失时才需要传）
     * @param expiresIn 期望的 access_token 有效期（秒，必须大于 0）
     * @param scope     期望收敛到的权限范围（英文逗号分隔，可空表示沿用凭证原范围）
     * @param name      本次签发的名称（必填，不超过 50 字符）
     * @return 新签发的 access_token
     */
    @Operation(summary = "使用当前登录会话签发自定义有效期令牌")
    @PostMapping("/oauth/custom-tokens")
    public Result<OAuthTokenVO> createCustomToken(
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "expires_in", required = false) Long expiresIn,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "name", required = false) String name) {
        OAuthTokenVO vo = oauthTokenService.issueAccessTokenFromSession(
                UserContext.requireUid(), clientId, expiresIn, scope, name);
        LogUtil.debug(UserController.class, "[OAuth] 会话直签 access_token: uid={}, expiresIn={}s",
                UserContext.getUid(), vo.getExpiresIn());
        return Result.success(vo);
    }

    /**
     * 吊销（删除并失效）我的一条自定义有效期令牌
     *
     * <p>token 明文放在请求体中。令牌会立即从 Redis 失效，并删除对应签发记录使其从
     * 「我的自定义令牌」列表中移除。归属只按当前登录用户 uid 限定，可跨应用吊销自己
     * 名下任意一条令牌；幂等，令牌不存在或不属于自己都视为成功。</p>
     *
     * @param request 吊销请求（token 明文）
     * @return 空结果
     */
    @Operation(summary = "吊销我的自定义有效期令牌")
    @PostMapping("/oauth/custom-tokens/revoke")
    public Result<Void> revokeCustomToken(@Valid @RequestBody UserCustomTokenRevokeRequest request) {
        oauthTokenService.revokeUserCustomToken(UserContext.requireUid(), request.getToken());
        return Result.success(null);
    }

    /**
     * 撤销我对某第三方应用的授权（删除该应用下的全部令牌并按应用置台账为已吊销）
     *
     * @param request 撤销请求（应用客户端 ID）
     * @return 空结果
     */
    @Operation(summary = "撤销我对某第三方应用的授权")
    @PostMapping("/oauth/grants/revoke")
    public Result<Void> revokeClientAuthorization(
            @Valid @RequestBody ClientAuthorizationRevokeRequest request) {
        oauthTokenService.revokeClientAuthorization(
                UserContext.requireUid(), request.getClientId());
        return Result.success(null);
    }

    /**
     * 查看我对某第三方应用的授权范围（当前已授予权限 + 该应用可选权限）
     *
     * @param clientId 应用客户端 ID
     * @return 已授予权限与可选权限，供前端渲染勾选状态
     */
    @Operation(summary = "查看我对某第三方应用的授权范围")
    @GetMapping("/oauth/grants/scopes")
    public Result<OAuthUserScopeVO> listGrantScopes(@RequestParam("client_id") String clientId) {
        return Result.success(oauthTokenService.getUserScopeInfo(UserContext.requireUid(), clientId));
    }

    /**
     * 为我授权的第三方应用追加权限（只增不减）
     *
     * <p>追加的权限必须是系统可授予的权限（不受该应用登记范围限制）；结果会写入用户自定义
     * 授权范围表，并同步到已签发令牌，因此立即生效且下次授权自动延续。</p>
     *
     * @param request 追加请求（应用客户端 ID + 权限标识列表）
     * @return 空结果
     */
    @Operation(summary = "追加第三方应用授权权限")
    @PostMapping("/oauth/grants/scopes/grant")
    public Result<Void> grantScopes(@Valid @RequestBody UserScopeRequest request) {
        oauthTokenService.grantUserScopes(
                UserContext.requireUid(), request.getClientId(), request.getScopes());
        return Result.success(null);
    }

    /**
     * 取消我对某第三方应用的已授权权限（只减不增）
     *
     * <p>取消后剩余权限不可为空；若要彻底收回该应用，请改用整体撤销授权。</p>
     *
     * @param request 取消请求（应用客户端 ID + 权限标识列表）
     * @return 空结果
     */
    @Operation(summary = "取消第三方应用授权权限")
    @PostMapping("/oauth/grants/scopes/revoke")
    public Result<Void> revokeScopes(@Valid @RequestBody UserScopeRequest request) {
        oauthTokenService.revokeUserScopes(
                UserContext.requireUid(), request.getClientId(), request.getScopes());
        return Result.success(null);
    }

    /**
     * 踢指定设备下线
     *
     * @param token 要踢出的会话 token
     * @return 统一返回结果
     */
    @Operation(summary = "踢指定设备下线")
    @PostMapping("/sessions/{token}/kick")
    public Result<Void> kickSession(@PathVariable("token") String token) {
        userService.kickSession(UserContext.requireUid(), token);
        return Result.success();
    }
}
