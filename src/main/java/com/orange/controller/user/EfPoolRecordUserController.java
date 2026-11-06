package com.orange.controller.user;

import com.orange.common.context.UserContext;
import com.orange.common.util.Result;
import com.orange.entity.vo.efpool.EfPoolRecordIdentityVO;
import com.orange.entity.vo.efpool.EfPoolRecordAccountVO;
import com.orange.entity.vo.efpool.EfPoolRecordVO;
import com.orange.service.EfPoolRecordTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 终末地寻访记录接口（需登录态，路径由 /user/** 会话拦截器保护）
 *
 * <p>归属取自会话中的 uid，客户端不能通过请求参数指定。流程：创建导入任务（记录归属）→
 * 轮询任务状态 → 从「我导入过的账户」列表中选择角色读取寻访记录。</p>
 *
 * @author UserCenter
 */
@Tag(name = "终末地寻访记录接口（用户会话）")
@RestController
@RequestMapping("/user/ef-pool-records")
public class EfPoolRecordUserController {

    /** 寻访记录读取响应的缓存策略：仅允许私人缓存且每次回源校验，不得被公共缓存共享 */
    private static final String POOL_RECORD_CACHE_CONTROL = "private, no-cache";

    private final EfPoolRecordTaskService poolRecordTaskService;

    /**
     * 构造器注入服务
     *
     * @param poolRecordTaskService 寻访记录任务服务
     */
    public EfPoolRecordUserController(EfPoolRecordTaskService poolRecordTaskService) {
        this.poolRecordTaskService = poolRecordTaskService;
    }

    /**
     * 创建寻访记录导入任务，任务归属当前登录用户
     *
     * @param hgToken 鹰角通行证 token
     * @return 任务 ID，供前端轮询任务状态
     */
    @Operation(summary = "创建寻访记录导入任务（记录归属用户）")
    @PostMapping("/create-task")
    public Result<String> createTask(@RequestParam String hgToken) {
        return Result.success(poolRecordTaskService.createTask(hgToken, UserContext.requireUid()));
    }

    /**
     * 查询任务状态，仅任务创建者可见
     *
     * @param taskId 任务 ID
     * @return 任务完成时返回角色信息，未完成返回业务错误
     */
    @Operation(summary = "查询我的寻访记录任务状态")
    @GetMapping("/check-task")
    public Result<EfPoolRecordIdentityVO> checkTask(@RequestParam String taskId) {
        return Result.success(poolRecordTaskService.checkTask(taskId, UserContext.requireUid()));
    }

    /**
     * 查询当前用户导入过寻访记录的终末地账户列表
     *
     * @return 已导入账户列表，按导入时间倒序
     */
    @Operation(summary = "查询我导入过的终末地账户")
    @GetMapping("/accounts")
    public Result<List<EfPoolRecordAccountVO>> listAccounts() {
        return Result.success(poolRecordTaskService.listImportedAccounts(UserContext.requireUid()));
    }

    /**
     * 读取指定角色的干员与武器寻访记录，角色必须已归属当前用户
     *
     * @param roleId 终末地游戏内角色 UID，取自账户列表
     * @return 干员与武器寻访记录，响应仅允许私人缓存
     */
    @Operation(summary = "按角色读取我的寻访记录")
    @GetMapping("/record")
    public ResponseEntity<Result<EfPoolRecordVO>> getPoolRecordData(@RequestParam("roleId") String roleId) {
        EfPoolRecordVO data = poolRecordTaskService.getPoolRecordData(UserContext.requireUid(), roleId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, POOL_RECORD_CACHE_CONTROL)
                .body(Result.success(data));
    }
}
