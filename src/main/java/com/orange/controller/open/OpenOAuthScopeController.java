package com.orange.controller.open;

import com.orange.common.enums.OAuthScope;
import com.orange.common.util.Result;
import com.orange.entity.vo.oauth.ScopeItemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * OAuth 可配置授权范围公共查询接口，路径 {@code /open/oauth/scopes}。
 *
 * <p>无需登录：只读输出 scope 枚举的静态数据，不含任何用户数据。</p>
 *
 * <p>响应口径与《用户授权权限管理接口文档》中的 {@code selectableScopes} 保持一致：
 * 每项为 {@code {code, desc}}，{@code desc} 取自 {@link OAuthScope} 的中文描述；
 * 仅返回可自助配置的范围，管理员手工授予的元范围 {@code all} 不在其中。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 授权范围查询")
@RestController
@RequestMapping("/open/oauth/scopes")
public class OpenOAuthScopeController {

    /**
     * 查询系统全部可选权限（公开，无需登录）。
     *
     * @return 权限条目列表，每项含权限标识与中文描述，顺序与枚举声明一致
     */
    @Operation(summary = "查询系统全部可选权限（公开）")
    @GetMapping
    public Result<List<ScopeItemVO>> scopes() {
        return Result.success(OAuthScope.selectable().stream()
                .map(scope -> new ScopeItemVO(scope.getCode(), scope.getDescription()))
                .collect(Collectors.toList()));
    }
}
