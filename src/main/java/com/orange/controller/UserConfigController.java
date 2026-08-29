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
 * 用户配置接口（需登录态）
 *
 * @author UserCenter
 */
@Tag(name = "用户配置接口")
@RestController
@RequestMapping("/user/config")
public class UserConfigController {

    private final UserConfigService userConfigService;

    /**
     * 构造器注入服务
     *
     * @param userConfigService 用户配置服务
     */
    public UserConfigController(UserConfigService userConfigService) {
        this.userConfigService = userConfigService;
    }

    /**
     * 保存用户配置（同用户+项目+分类+版本已存在则覆盖更新）
     *
     * @param request 保存参数
     * @return 配置 id
     */
    @Operation(summary = "保存用户配置")
    @PostMapping("/save")
    public Result<Long> saveConfig(@Valid @RequestBody UserConfigSaveRequest request) {
        return Result.success(userConfigService.saveConfig(UserContext.requireUid(), request));
    }

    /**
     * 查询用户在当前登录客户端下某分类的全部配置（client_id 取自登录上下文，前端不可指定）
     *
     * @param category 配置分类
     * @param version  配置版本（可空，空则返回该分类下全部版本）
     * @param name     配置名称（可空，空则返回该版本下全部命名配置）
     * @return 配置列表
     */
    @Operation(summary = "查询用户配置")
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
    @Operation(summary = "删除用户配置")
    @PostMapping("/delete")
    public Result<Void> deleteConfig(@Valid @RequestBody UserConfigDeleteRequest request) {
        userConfigService.deleteConfig(UserContext.requireUid(), request.getId());
        return Result.success();
    }
}
