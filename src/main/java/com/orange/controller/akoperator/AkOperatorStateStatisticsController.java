package com.orange.controller.akoperator;

import com.orange.common.util.Result;
import com.orange.entity.vo.akoperator.OperatorStatisticsVO;
import com.orange.service.AkOperatorStateStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 干员养成数据统计查询接口（无需登录）
 *
 * <p>只读结果表 ak_operator_state_statistics，不触发全表扫描，接口耗时与源表数据量无关。</p>
 *
 * <p>重新统计入口：{@code GET /refresh} 供手动触发。跑一轮统计要扫描约 4000 万行，
 * 属离线跑批，因此该入口立即返回、统计在后台异步执行，同一时刻只允许一轮，
 * 已在执行时本次触发被忽略，避免外部反复调用把数据库压满。</p>
 *
 * @author UserCenter
 */
@Tag(name = "干员数据统计")
@RestController
@RequestMapping("/ak-operator-statistics")
public class AkOperatorStateStatisticsController {

    private final AkOperatorStateStatisticsService statisticsService;

    /**
     * 构造器注入统计服务
     *
     * @param statisticsService 干员数据统计服务
     */
    public AkOperatorStateStatisticsController(AkOperatorStateStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 查询最近一轮干员统计结果
     *
     * @return 统一响应，data 为按干员编码升序排列的统计结果；尚未跑过统计时 data 为空列表
     */
    @Operation(summary = "查询干员数据统计结果")
    @GetMapping
    public Result<List<OperatorStatisticsVO>> list() {
        return Result.success(statisticsService.getOperatorStatistics());
    }

    /**
     * 手动触发一轮干员数据统计
     *
     * <p>立即返回，统计在后台异步执行；同一时刻只允许一轮，已在执行时不会重复触发。
     * 统计完成后结果表被整表替换，随后的查询接口即可读到新结果。</p>
     *
     * @return 统一响应，data 为本次触发结果说明
     */
    @Operation(summary = "手动触发干员数据统计")
    @GetMapping("/refresh")
    public Result<String> refresh() {
        boolean triggered = statisticsService.triggerOperatorStatisticsRefresh();
        return Result.success(triggered ? "统计已触发，将在后台执行" : "已有统计任务在执行，本次未重复触发");
    }
}
