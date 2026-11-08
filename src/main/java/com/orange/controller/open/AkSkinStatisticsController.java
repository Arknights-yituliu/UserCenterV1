package com.orange.controller.open;

import com.orange.common.util.Result;
import com.orange.entity.vo.akoperator.SkinStatisticsVO;
import com.orange.service.AkSkinStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 皮肤持有数据统计查询接口（无需登录），路径 {@code /open/ak-skin-statistics/result}
 *
 * <p>只读结果表 ak_skin_statistics，不触发全表扫描，接口耗时与源表数据量无关。</p>
 *
 * <p>统计结果由 {@link com.orange.service.scheduler.AkSkinStatisticsScheduler} 定时异步刷新，
 * 每小时 10 分一轮；跑一轮要扫描 ak_skin_state 全表，属离线跑批，因此不提供手动触发入口，
 * 避免反复触发把数据库压满。</p>
 *
 * @author UserCenter
 */
@Tag(name = "皮肤数据统计")
@RestController
@RequestMapping("/open")
public class AkSkinStatisticsController {

    private final AkSkinStatisticsService statisticsService;

    /**
     * 构造器注入统计服务
     *
     * @param statisticsService 皮肤数据统计服务
     */
    public AkSkinStatisticsController(AkSkinStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 查询最近一轮皮肤统计结果
     *
     * @return 统一响应，data 为按皮肤编码升序排列的统计结果；尚未跑过统计时 data 为空列表
     */
    @Operation(summary = "查询皮肤持有统计结果")
    @GetMapping("/ak-skin-statistics/result")
    public Result<List<SkinStatisticsVO>> list() {
        return Result.success(statisticsService.getSkinStatistics());
    }
}
