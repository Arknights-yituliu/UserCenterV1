package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.LogUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.oauth.UpdateAvatarRequest;
import com.orange.entity.dto.oauth.UpdateNicknameRequest;
import com.orange.entity.dto.user.UpdateProfileRequest;
import com.orange.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth 授权访问的用户资料修改接口
 *
 * <p>需要 {@code user.profile} 授权范围；用户身份取自 access_token，请求参数不能指定或覆盖它。
 * 昵称与头像的写库逻辑复用用户侧 {@link UserService#updateProfile}，本控制器只做 scope 校验与参数转换，
 * 避免 OAuth 侧与用户侧出现两套业务规则。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 用户资料修改接口")
@RestController
@RequestMapping("/oauth2/profile")
public class OAuthProfileController {

    private final UserService userService;

    /**
     * 构造器注入服务
     *
     * @param userService 用户服务（复用用户侧资料修改逻辑）
     */
    public OAuthProfileController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 修改当前令牌用户的昵称
     *
     * <p>昵称支持中文，不做字符集限制；仅校验非空与长度上限 64 字符。</p>
     *
     * @param request 昵称修改参数
     * @return 统一返回结果
     */
    @Operation(summary = "修改昵称（OAuth，支持中文）")
    @PostMapping("/nickname")
    public Result<Void> updateNickname(@Valid @RequestBody UpdateNicknameRequest request) {
        UserContext.requireScope(OAuthScope.USER_PROFILE);
        UpdateProfileRequest profileRequest = new UpdateProfileRequest();
        profileRequest.setNickname(request.getNickname());
        userService.updateProfile(UserContext.requireUid(), profileRequest);
        LogUtil.debug(OAuthProfileController.class, "[OAuth] 修改昵称: uid={}", UserContext.getUid());
        return Result.success();
    }

    /**
     * 修改当前令牌用户的头像
     *
     * <p>avatar 传空串表示清除头像，具体清空规则沿用用户侧实现。</p>
     *
     * @param request 头像修改参数
     * @return 统一返回结果
     */
    @Operation(summary = "修改头像（OAuth）")
    @PostMapping("/avatar")
    public Result<Void> updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        UserContext.requireScope(OAuthScope.USER_PROFILE);
        UpdateProfileRequest profileRequest = new UpdateProfileRequest();
        profileRequest.setAvatar(request.getAvatar());
        userService.updateProfile(UserContext.requireUid(), profileRequest);
        LogUtil.debug(OAuthProfileController.class, "[OAuth] 修改头像: uid={}", UserContext.getUid());
        return Result.success();
    }
}
