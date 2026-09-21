package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.Result;
import com.orange.entity.vo.UserScheduleVO;
import com.orange.service.UserScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OAuth 授权访问的排班表查询接口。 */
@Tag(name = "OAuth 排班表接口")
@RestController
@RequestMapping("/oauth2/schedules")
public class OAuthScheduleController {

    private final UserScheduleService userScheduleService;

    public OAuthScheduleController(UserScheduleService userScheduleService) {
        this.userScheduleService = userScheduleService;
    }

    /**
     * 按 ID 查询完整排班表，复用公开查询逻辑，但要求 OAuth 游戏数据读取权限。
     *
     * @param id 排班表 ID
     * @return 排班表内容
     */
    @Operation(summary = "按 ID 查询完整排班表（OAuth）")
    @GetMapping("/{id}")
    public Result<UserScheduleVO> get(@PathVariable Long id) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_READ);
        return Result.success(userScheduleService.getSchedule(id));
    }
}
