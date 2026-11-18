package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.util.LogUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.pat.PatClientRevokeRequest;
import com.orange.entity.dto.pat.PatIssueByRefreshRequest;
import com.orange.entity.vo.pat.PatIssueVO;
import com.orange.entity.vo.pat.PatVO;
import com.orange.service.PatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人访问令牌的应用视角接口（需 access_token）
 *
 * <p>{@code /list} 与 {@code /revoke} 的认证由 {@code OAuthAuthInterceptor} 完成，
 * uid 与 client_id 均取自 access_token 记录，因此调用方只能看到/吊销自己应用名下的记录。
 * 服务层还会校验本应用已开通直连认证能力（{@code direct_auth_enabled=1}），
 * 避免普通第三方应用凭 access_token 作废用户令牌。</p>
 *
 * <p><strong>根路径 {@code POST /oauth2/pat} 是协议级例外</strong>：它凭请求体里的
 * refresh_token 签发 PAT，不经过 access_token 拦截器（已在 {@code WebConfig} 单独排除），
 * 属「自带协议级认证」的端点。uid 与 client_id
 * 取自 refresh_token 记录，签发准入同样是 {@code direct_auth_enabled=1}。</p>
 *
 * <p><strong>路径必须在 {@code WebConfig} 显式注册</strong>：漏配的表现是「接口能访问但
 * 没校验过」，验证时要特意测「不带 token」。</p>
 *
 * @author UserCenter
 */
@Tag(name = "个人访问令牌（应用视角）")
@RestController
@RequestMapping("/oauth2/pat")
public class OAuthPatController {

    private final PatService patService;

    /**
     * 构造器注入服务
     *
     * @param patService PAT 服务
     */
    public OAuthPatController(PatService patService) {
        this.patService = patService;
    }

    /**
     * 凭 refresh_token 签发一枚 PAT（明文原样入库，列表可回看）
     *
     * <p>本端点不经过 access_token 拦截器；凭证由请求体携带，不消费、不轮转。
     * 参数与响应结构与 {@code POST /user/pat} 一致，client_id 取凭证所属应用。</p>
     *
     * @param request 签发参数（含 refresh_token）
     * @return 含明文令牌的签发结果
     */
    @Operation(summary = "凭 refresh_token 签发个人访问令牌（仅限已开通直连认证的客户端）")
    @PostMapping
    public Result<PatIssueVO> issue(@Valid @RequestBody PatIssueByRefreshRequest request) {
        return Result.success(patService.issueByRefreshToken(request));
    }

    /**
     * 查询本应用为该用户签发的 PAT 列表
     *
     * @return 令牌列表（含明文）
     */
    @Operation(summary = "查询本应用的用户个人访问令牌列表（仅限已开通直连认证的客户端）")
    @GetMapping("/list")
    public Result<List<PatVO>> list() {
        List<PatVO> tokens = patService.listByClient(UserContext.requireUid(), UserContext.getClientId());
        LogUtil.debug(OAuthPatController.class, "[PAT] 应用视角查询: clientId={}, size={}",
                UserContext.getClientId(), tokens.size());
        return Result.success(tokens);
    }

    /**
     * 按明文吊销本应用为该用户签发的一条 PAT
     *
     * @param request 吊销请求（PAT 明文）
     * @return 空结果
     */
    @Operation(summary = "吊销本应用签发的一条个人访问令牌（仅限已开通直连认证的客户端）")
    @PostMapping("/revoke")
    public Result<Void> revoke(@Valid @RequestBody PatClientRevokeRequest request) {
        // 入口统一归一化：请求体里的明文可能带粘贴残留的空白，服务层只做精确匹配
        String token = request.getToken() == null ? null : request.getToken().trim();
        patService.revokeByClient(UserContext.requireUid(), UserContext.getClientId(), token);
        return Result.success();
    }
}
