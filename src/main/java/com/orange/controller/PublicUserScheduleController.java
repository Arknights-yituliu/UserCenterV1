package com.orange.controller;

import com.orange.common.util.Result;
import com.orange.entity.vo.UserScheduleVO;
import com.orange.service.UserScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 不需要登录的排班表查询接口。 */
@Tag(name = "排班表查询接口")
@RestController
@RequestMapping("/schedules")
public class PublicUserScheduleController {

    private final UserScheduleService userScheduleService;

    public PublicUserScheduleController(UserScheduleService userScheduleService) {
        this.userScheduleService = userScheduleService;
    }

    @Operation(summary = "按 ID 查询完整排班表")
    @GetMapping("/{id}")
    public Result<UserScheduleVO> get(@PathVariable Long id) {
        return Result.success(userScheduleService.getSchedule(id));
    }
}
