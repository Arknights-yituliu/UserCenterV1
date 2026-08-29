package com.orange.controller;

import com.orange.common.context.UserContext;
import com.orange.common.util.Result;
import com.orange.entity.dto.userconfig.UserConfigDeleteRequest;
import com.orange.entity.dto.userconfig.UserConfigSaveRequest;
import com.orange.entity.vo.UserConfigVO;
import com.orange.service.UserConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OAuth 用户配置接口（需 OAuth access_token，由 OAuthAuthInterceptor 统一校验）
 *
 * <p>client_id 取自 OAuth token（由服务端签发，前端不可伪造），
 * 第三方站点只能读写自己站点（client_id）下的用户配置，无法伪造获取其他站点的配置。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 用户配置接口")
@RestController
@RequestMapping("/oauth/config")
public class OAuthConfigController {

    private final UserConfigService userConfigService;

    /**
     * 构造器注入服务
     *
     * @param userConfigService 用户配置服务
     */
    public OAuthConfigController(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    /**
     * 保存用户配置（client_id 取自 OAuth token）
     *
     * @param request 保存参数
     * @return 配置 id
     */
    @Operation(summary = "保存用户配置（OAuth）")
    @PostMapping("/save")
    public Result<Long> saveConfig(@Valid @RequestBody UserConfigSaveRequest request) {
        return Result.success(userConfigService.saveConfig(UserContext.requireUid(), request));
    }

    /**
     * 查询用户配置（client_id 取自 OAuth token）
     *
     * @param category 配置分类
     * @param version  配置版本（可空）
     * @param name     配置名称（可空）
     * @return 配置列表
     */
    @Operation(summary = "查询用户配置（OAuth）")
    @GetMapping("/list")
    public Result<List<UserConfigVO>> listConfigs(@RequestParam("category") String category,
                                                  @RequestParam(value = "version", required = false) String version,
                                                  @RequestParam(value = "name", required = false) String name) {
        return Result.success(userConfigService.listConfigs(
                UserContext.requireUid(), UserContext.getClientId(), category, version, name));
    }

    /**
     * 删除用户配置（逻辑删除）
     *
     * @param request 删除参数
     * @return 统一返回结果
     */
    @Operation(summary = "删除用户配置（OAuth）")
    @PostMapping("/delete")
    public Result<Void> deleteConfig(@Valid @RequestBody UserConfigDeleteRequest request) {
        userConfigService.deleteConfig(UserContext.requireUid(), request.getId());
        return Result.success();
    }
}
