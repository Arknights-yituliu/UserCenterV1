package com.orange.controller.user;

import com.orange.common.context.UserContext;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 需用户会话的排班表管理接口。 */
@Tag(name = "用户排班表接口")
@RestController
@RequestMapping("/user/schedules")
public class UserScheduleController {

    private final UserScheduleService userScheduleService;

    public UserScheduleController(UserScheduleService userScheduleService) {
        this.userScheduleService = userScheduleService;
    }

    @Operation(summary = "保存排班表（创建 / 覆盖更新）")
    @PostMapping("/save")
    public Result<Long> save(@Valid @RequestBody UserScheduleSaveRequest request) {
        return Result.success(userScheduleService.saveSchedule(UserContext.requireUid(), request));
    }

    @Operation(summary = "按用户名查询自己的排班表列表")
    @GetMapping("/list")
    public Result<List<UserScheduleVO>> list(@RequestParam("username") String userName) {
        return Result.success(userScheduleService.listOwnSchedules(UserContext.requireUid(), userName));
    }

    @Operation(summary = "删除自己的排班表")
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody UserScheduleDeleteRequest request) {
        userScheduleService.deleteSchedule(UserContext.requireUid(), request.getId());
        return Result.success();
    }
}
