package com.orange.controller.oauth;

import com.orange.common.util.RequestUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.oauth.LegacyImportTokenItem;
import com.orange.entity.dto.oauth.MigrateTokenRequest;
import com.orange.entity.vo.oauth.MigrateTokenVO;
import com.orange.service.OAuthLegacyImportService;
import com.orange.service.OAuthMigrateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 内部迁移兑换端点（BackEndV3 服务端调用）
 *
 * <p>该端点跨公网可达，认证完全由端点自带（Ed25519 验签 + 时间窗 + nonce 防重放），
 * 因此不得在 WebConfig 中为其注册任何拦截器，也不得纳入 /oauth2 的令牌保护路径。</p>
 *
 * @author UserCenter
 */
@Tag(name = "内部迁移端点")
@RestController
@RequestMapping("/oauth2/internal")
public class OAuthInternalController {

    private final OAuthMigrateService oauthMigrateService;
    private final OAuthLegacyImportService oauthLegacyImportService;

    /**
     * 构造器注入依赖
     *
     * @param oauthMigrateService      迁移兑换服务
     * @param oauthLegacyImportService 旧系统只读令牌导入服务
     */
    public OAuthInternalController(OAuthMigrateService oauthMigrateService,
                                   OAuthLegacyImportService oauthLegacyImportService) {
        this.oauthMigrateService = oauthMigrateService;
        this.oauthLegacyImportService = oauthLegacyImportService;
    }

    /**
     * 迁移兑换：凭旧自签 token 对应的 uid 换取一对全新的 UC 令牌
     *
     * @param clientId        目标 OAuth 客户端 ID
     * @param uid             用户名下 uid
     * @param ts              请求时间戳（Unix 秒）
     * @param nonce           一次性随机串
     * @param kid             公钥标识（对应 UC 侧 kid → 公钥映射表）
     * @param sig             Ed25519 签名（Base64）
     * @param origin          来源标识（可选，仅审计）
     * @param legacyTokenHash 旧 token 的 sha256（可选，仅审计）
     * @param request         HTTP 请求（取来源 IP 与协议）
     * @return 令牌响应（access_token + refresh_token + scope）
     */
    @Operation(summary = "迁移兑换令牌（BackEndV3 服务端调用）")
    @PostMapping("/migrate-token")
    public Result<MigrateTokenVO> migrateToken(@RequestParam("client_id") String clientId,
                                               @RequestParam("uid") String uid,
                                               @RequestParam("ts") String ts,
                                               @RequestParam("nonce") String nonce,
                                               @RequestParam("kid") String kid,
                                               @RequestParam("sig") String sig,
                                               @RequestParam(value = "origin", required = false) String origin,
                                               @RequestParam(value = "legacy_token_hash", required = false) String legacyTokenHash,
                                               HttpServletRequest request) {
        MigrateTokenRequest migrateRequest = new MigrateTokenRequest(
                clientId, uid, kid, ts, nonce, sig, origin, legacyTokenHash,
                RequestUtil.getIp(request), isHttps(request));
        return Result.success(oauthMigrateService.migrateToken(migrateRequest));
    }

    /**
     * 批量导入旧系统只读令牌（临时接口）
     *
     * <p>请求体为令牌列表，每个元素含 uid、token、name、scope、expires_in、expire_time；client_id 统一
     * 固定为 {@code arknights-yituliu-web}。调用方通过请求头 {@code X-Import-Key} 提交密钥，
     * 服务层与配置中的固定值做字符串比对。该接口为存量迁移临时使用，导入结束后应关闭。</p>
     *
     * @param importKey 调用方密钥（请求头 X-Import-Key）
     * @param items     待导入的令牌列表
     * @return 导入结果（成功入库条数与跳过条数）
     */
    @Operation(summary = "批量导入旧系统只读令牌（临时接口）")
    @PostMapping("/import-legacy-tokens")
    public Result<OAuthLegacyImportService.LegacyImportResult> importLegacyTokens(
            @RequestHeader(value = "X-Import-Key", required = false) String importKey,
            @RequestBody List<LegacyImportTokenItem> items) {
        return Result.success(oauthLegacyImportService.importTokens(importKey, items));
    }

    /**
     * 判断请求是否经 HTTPS 到达
     *
     * <p>生产环境由反向代理终止 TLS，容器内看到的是明文 HTTP，因此除 isSecure() 之外
     * 还需识别代理透传的 X-Forwarded-Proto。</p>
     *
     * @param request HTTP 请求
     * @return true=经 HTTPS 到达
     */
    private static boolean isHttps(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }
}
