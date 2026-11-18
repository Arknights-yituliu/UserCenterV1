package com.orange.controller.pat;

import com.orange.common.context.PatContext;
import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.PatPayloadUtil;
import com.orange.common.util.Result;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.service.AkAccountService;
import com.orange.service.PatPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAT 干员数据接口，路径 {@code /pat/**}。
 *
 * <p>凭证由 {@link com.orange.interceptor.PatAuthInterceptor} 解析，本类只负责取数口径：
 * 目标账号不来自请求参数，而按令牌是否有 {@code tlc_pat_} 前缀分流——新令牌取 payload 的
 * {@code game_uid} 并由业务层核对绑定关系；无前缀的旧令牌没有 payload 约定，按「默认来源 →
 * 最近变更」取数，与 OAuth 版不传 {@code akUid} 一致。</p>
 *
 * <p>响应体与 {@code /oauth2/ak-accounts/operators} 完全一致，共用
 * {@link AkAccountService#listOperators(Long, String, String)}，不另立一套取数逻辑。</p>
 *
 * @author UserCenter
 */
@Tag(name = "PAT 干员数据接口")
@RestController
@RequestMapping("/pat/ak-accounts")
public class PatAkAccountController {

    /** 干员全量读取响应只允许私人缓存，且每次都必须回源校验。 */
    private static final String OPERATOR_CACHE_CONTROL = "private, no-cache";

    private final AkAccountService akAccountService;

    /**
     * 构造器注入服务
     *
     * @param akAccountService 游戏账号与干员数据服务
     */
    public PatAkAccountController(AkAccountService akAccountService) {
        this.akAccountService = akAccountService;
    }

    /**
     * 读取干员数据：账号来源按令牌是否有 {@code tlc_pat_} 前缀分流
     *
     * <p>带前缀的新令牌：账号取自 payload {@code game_uid}，缺失或空白报 10002；显式传
     * {@code source=skland} 可绕过「akUid 等于本人 uid 时按手动版读取」的撞号兼容分支，
     * 保证每次都核对绑定关系。</p>
     *
     * <p>无前缀的旧令牌（迁移进 {@code tlc_pat} 的存量令牌，没有 payload 约定）：不指定账号，
     * 交给业务层按「用户默认来源 → 最近变更」选择，与 OAuth 版不传 {@code akUid} 完全一致。</p>
     *
     * <p>接口不声明任何查询参数：账号完全由 PAT 决定，调用方多传的参数由框架忽略、
     * 不参与取数。因此不要依赖请求参数指定账号。</p>
     *
     * @return 干员全量数据，与 OAuth 版同结构
     */
    @Operation(summary = "读取 PAT 对应用户的游戏账号干员数据（PAT）")
    @GetMapping("/operators")
    public ResponseEntity<Result<OperatorListVO>> listOperators() {
        UserContext.requireScope(OAuthScope.AK_DATA_READ);
        String targetAkUid = null;
        String source = null;
        PatPrincipal principal = PatContext.require();
        if (principal.isPrefixed()) {
            targetAkUid = PatPayloadUtil.requireGameUid(principal.getPayload());
            // 传 skland 而非 null：业务层据此跳过手动版撞号分支，强制核对 ak_account_binding
            source = AkAccountVO.SOURCE_SKLAND;
        }
        OperatorListVO data = akAccountService.listOperators(
                UserContext.requireUid(), targetAkUid, source);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, OPERATOR_CACHE_CONTROL)
                .body(Result.success(data));
    }
}
