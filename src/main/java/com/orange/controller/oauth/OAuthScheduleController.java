package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.Result;
import com.orange.entity.dto.schedule.UserScheduleDeleteRequest;
import com.orange.entity.dto.schedule.UserScheduleSaveRequest;
import com.orange.entity.vo.UserScheduleVO;
import com.orange.service.UserScheduleService;
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
 * OAuth 授权访问的排班表接口
 *
 * <p>查询列表需要 {@code config.read}，保存与删除需要 {@code config.write}；
 * 用户身份取自 access_token，请求参数不能指定或覆盖它。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 排班表接口")
@RestController
@RequestMapping("/oauth2/schedule")
public class OAuthScheduleController {

    private final UserScheduleService userScheduleService;

    public OAuthScheduleController(UserScheduleService userScheduleService) {
        this.userScheduleService = userScheduleService;
    }

    /**
     * 查询当前令牌用户自己的排班表列表，用户取自 access_token。
     *
     * @return 排班表列表，按更新时间倒序
     */
    @Operation(summary = "查询排班表列表（OAuth）")
    @GetMapping("/list")
    public Result<List<UserScheduleVO>> list() {
        UserContext.requireScope(OAuthScope.CONFIG_READ);
        return Result.success(userScheduleService.listOwnSchedules(UserContext.requireUid()));
    }

    /**
     * 保存排班表（创建 / 覆盖更新），用户取自 access_token。
     *
     * @param request 保存参数
     * @return 排班表 ID
     */
    @Operation(summary = "保存排班表（OAuth，创建 / 覆盖更新）")
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody UserScheduleSaveRequest request) {
        UserContext.requireScope(OAuthScope.CONFIG_WRITE);
        return Result.success(userScheduleService.saveSchedule(UserContext.requireUid(), request));
    }

    /**
     * 删除自己的排班表，归属按 access_token 中的用户校验。
     *
     * @param request 删除参数
     * @return 统一返回结果
     */
    @Operation(summary = "删除排班表（OAuth）")
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody UserScheduleDeleteRequest request) {
        UserContext.requireScope(OAuthScope.CONFIG_WRITE);
        userScheduleService.deleteSchedule(UserContext.requireUid(), request.getId());
        return Result.success();
    }
}
