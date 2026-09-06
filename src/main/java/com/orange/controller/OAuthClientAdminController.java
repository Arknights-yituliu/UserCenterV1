package com.orange.controller;

import com.orange.common.context.UserContext;
import com.orange.common.util.Result;
import com.orange.entity.dto.oauthclient.OAuthClientRegisterRequest;
import com.orange.entity.dto.oauthclient.OAuthClientUpdateRequest;
import com.orange.entity.vo.oauth.OAuthClientCredentialVO;
import com.orange.entity.vo.oauth.OAuthClientVO;
import com.orange.service.OAuthClientAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OAuth 客户端自助管理接口（需 UC 会话 token，/user/** 由 UserAuthInterceptor 统一校验）
 *
 * <p>第三方开发者登录后维护自己名下的 OAuth 客户端：注册、查看、更新、密钥轮换、停用、删除，
 * 所有权按 owner_uid 隔离，只能操作自己名下的客户端。
 * 归入 /user/oauth/client/** 与用户自助面一致，不再占用 OAuth 协议前缀 /oauth2。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 客户端自助管理")
@RestController
@RequestMapping("/user/oauth/client")
public class OAuthClientAdminController {

    private final OAuthClientAdminService oauthClientAdminService;

    /**
     * 构造器注入服务
     *
     * @param oauthClientAdminService 客户端自助管理服务
     */
    public OAuthClientAdminController(OAuthClientAdminService oauthClientAdminService) {
        this.oauthClientAdminService = oauthClientAdminService;
    }

    /**
     * 注册客户端（默认待管理员审批；公共客户端不生成 secret；加密客户端明文 secret 仅此一次返回）
     *
     * @param request 注册参数
     * @return 客户端凭证（公共客户端 secret 为 null）
     */
    @Operation(summary = "注册 OAuth 客户端")
    @PostMapping("/register")
    public Result<OAuthClientCredentialVO> register(@Valid @RequestBody OAuthClientRegisterRequest request) {
        return Result.success(oauthClientAdminService.register(UserContext.requireUid(), request));
    }

    /**
     * 查询我的客户端列表（不返回 secret）
     *
     * @return 客户端列表
     */
    @Operation(summary = "查询我的客户端列表")
    @GetMapping("/list")
    public Result<List<OAuthClientVO>> list() {
        return Result.success(oauthClientAdminService.listClients(UserContext.requireUid()));
    }

    /**
     * 查询客户端详情（不返回 secret）
     *
     * @param clientId 客户端 ID
     * @return 客户端详情
     */
    @Operation(summary = "查询客户端详情")
    @GetMapping("/{clientId}")
    public Result<OAuthClientVO> get(@PathVariable("clientId") String clientId) {
        return Result.success(oauthClientAdminService.getClient(UserContext.requireUid(), clientId));
    }

    /**
     * 更新客户端（client_id/authMethod/grantTypes 不可改）
     *
     * @param clientId 客户端 ID
     * @param request  更新参数
     * @return 统一成功响应
     */
    @Operation(summary = "更新客户端")
    @PostMapping("/{clientId}/update")
    public Result<Void> update(@PathVariable("clientId") String clientId,
                               @Valid @RequestBody OAuthClientUpdateRequest request) {
        oauthClientAdminService.updateClient(UserContext.requireUid(), clientId, request);
        return Result.success();
    }

    /**
     * 轮换客户端密钥（新 secret 明文仅此一次返回，旧值立即失效）
     *
     * @param clientId 客户端 ID
     * @return 客户端凭证（含新明文 secret）
     */
    @Operation(summary = "轮换客户端密钥")
    @PostMapping("/{clientId}/rotate-secret")
    public Result<OAuthClientCredentialVO> rotateSecret(@PathVariable("clientId") String clientId) {
        return Result.success(oauthClientAdminService.rotateSecret(UserContext.requireUid(), clientId));
    }

    /**
     * 停用客户端（停用后授权/换 token 返回 90001）
     *
     * @param clientId 客户端 ID
     * @return 统一成功响应
     */
    @Operation(summary = "停用客户端")
    @PostMapping("/{clientId}/disable")
    public Result<Void> disable(@PathVariable("clientId") String clientId) {
        oauthClientAdminService.setOwnerEnabled(UserContext.requireUid(), clientId, false);
        return Result.success();
    }

    /**
     * 启用客户端
     *
     * @param clientId 客户端 ID
     * @return 统一成功响应
     */
    @Operation(summary = "启用客户端")
    @PostMapping("/{clientId}/enable")
    public Result<Void> enable(@PathVariable("clientId") String clientId) {
        oauthClientAdminService.setOwnerEnabled(UserContext.requireUid(), clientId, true);
        return Result.success();
    }

    /**
     * 删除客户端（级联吊销其名下全部令牌，不可恢复）
     *
     * @param clientId 客户端 ID
     * @return 统一成功响应
     */
    @Operation(summary = "删除客户端")
    @PostMapping("/{clientId}/delete")
    public Result<Void> delete(@PathVariable("clientId") String clientId) {
        oauthClientAdminService.deleteClient(UserContext.requireUid(), clientId);
        return Result.success();
    }
}
