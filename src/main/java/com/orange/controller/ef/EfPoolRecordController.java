package com.orange.controller.ef;

import com.orange.common.util.Result;
import com.orange.entity.po.EfEndministratorInfo;
import com.orange.entity.vo.efpool.EfPoolRecordVO;
import com.orange.service.EfPoolRecordTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 终末地寻访记录接口（当前暂不鉴权）
 *
 * <p>流程：创建导入任务 → 轮询任务状态 → 读取该角色的干员与武器寻访记录。</p>
 *
 * @author UserCenter
 */
@Tag(name = "终末地寻访记录接口")
@RestController
@RequestMapping("/ef/pool-record")
public class EfPoolRecordController {

    private final EfPoolRecordTaskService poolRecordTaskService;

    /**
     * 构造器注入服务
     *
     * @param poolRecordTaskService 寻访记录任务服务
     */
    public EfPoolRecordController(EfPoolRecordTaskService poolRecordTaskService) {
        this.poolRecordTaskService = poolRecordTaskService;
    }

    /**
     * 创建寻访记录导入任务
     *
     * @param hgToken 鹰角通行证 token
     * @return 任务 ID
     */
    @Operation(summary = "创建寻访记录导入任务")
    @PostMapping("/create-task")
    public Result<String> createTask(@RequestParam String hgToken) {
        return Result.success(poolRecordTaskService.createTask(hgToken));
    }

    /**
     * 查询任务状态
     *
     * @param taskId 任务 ID
     * @return 任务完成时返回玩家角色信息，未完成时返回业务错误码
     */
    @Operation(summary = "查询寻访记录导入任务状态")
    @GetMapping("/check-task")
    public Result<EfEndministratorInfo> checkTask(@RequestParam String taskId) {
        return Result.success(poolRecordTaskService.checkTask(taskId));
    }

    /**
     * 读取寻访记录
     *
     * @param taskId 任务 ID
     * @return 该角色的干员与武器寻访记录
     */
    @Operation(summary = "读取干员与武器寻访记录")
    @GetMapping("/character/list")
    public Result<EfPoolRecordVO> getPoolRecordData(@RequestParam String taskId) {
        return Result.success(poolRecordTaskService.getPoolRecordData(taskId));
    }
}
