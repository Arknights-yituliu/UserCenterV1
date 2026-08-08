package com.lhs.uc.controller;

import com.lhs.uc.common.context.UserContext;
import com.lhs.uc.common.util.Result;
import com.lhs.uc.entity.dto.user.UpdatePasswordRequest;
import com.lhs.uc.entity.dto.user.UpdateProfileRequest;
import com.lhs.uc.entity.vo.SessionVO;
import com.lhs.uc.entity.vo.UserInfoVO;
import com.lhs.uc.service.UserService;
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
 * 用户自助接口（需登录态）
 *
 * @author UserCenter
 */
@Tag(name = "用户自助接口")
@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    /**
     * 构造器注入服务
     *
     * @param userService 用户服务
     */
    public UserController(UserService userService) {
        this.userService = userService;
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
    @PostMapping("/profile")
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
