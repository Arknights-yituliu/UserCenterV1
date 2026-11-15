package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.Result;
import com.orange.entity.vo.efpool.EfPoolRecordAccountVO;
import com.orange.entity.vo.efpool.EfPoolRecordIdentityVO;
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
 * OAuth 授权访问的终末地寻访记录接口
 *
 * <p>流程与用户侧一致：创建导入任务（归属取 access_token 中的 uid）→ 轮询任务状态 →
 * 从「导入过的账户」列表中选择角色读取寻访记录。查询类接口需要 {@code gama-data.read}，
 * 创建任务会写入数据，需要 {@code gama-data.write}。任务归属与角色绑定校验均按令牌用户完成，
 * 请求参数不能指定或覆盖用户身份。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 终末地寻访记录接口")
@RestController
@RequestMapping("/oauth2/ef-pool-records")
public class OAuthEfPoolRecordController {

    /** 寻访记录读取响应的缓存策略：仅允许私人缓存且每次回源校验，不得被公共缓存共享 */
    private static final String POOL_RECORD_CACHE_CONTROL = "private, no-cache";

    private final EfPoolRecordTaskService poolRecordTaskService;

    /**
     * 构造器注入服务
     *
     * @param poolRecordTaskService 寻访记录任务服务
     */
    public OAuthEfPoolRecordController(EfPoolRecordTaskService poolRecordTaskService) {
        this.poolRecordTaskService = poolRecordTaskService;
    }

    /**
     * 创建寻访记录导入任务，任务归属当前令牌用户
     *
     * @param hgToken 鹰角通行证 token
     * @return 任务 ID，供调用方轮询任务状态
     */
    @Operation(summary = "创建寻访记录导入任务（OAuth）")
    @PostMapping("/create-task")
    public Result<String> createTask(@RequestParam String hgToken) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_WRITE);
        return Result.success(poolRecordTaskService.createTask(hgToken, UserContext.requireUid()));
    }

    /**
     * 查询任务状态，仅任务创建者可见
     *
     * @param taskId 任务 ID
     * @return 任务完成时返回角色信息，未完成返回业务错误
     */
    @Operation(summary = "查询寻访记录任务状态（OAuth）")
    @GetMapping("/check-task")
    public Result<EfPoolRecordIdentityVO> checkTask(@RequestParam String taskId) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_READ);
        return Result.success(poolRecordTaskService.checkTask(taskId, UserContext.requireUid()));
    }

    /**
     * 查询当前令牌用户导入过寻访记录的终末地账户列表
     *
     * @return 已导入账户列表，按导入时间倒序
     */
    @Operation(summary = "查询导入过的终末地账户（OAuth）")
    @GetMapping("/accounts")
    public Result<List<EfPoolRecordAccountVO>> listAccounts() {
        UserContext.requireScope(OAuthScope.GAMA_DATA_READ);
        return Result.success(poolRecordTaskService.listImportedAccounts(UserContext.requireUid()));
    }

    /**
     * 读取指定角色的干员与武器寻访记录，角色必须已归属当前令牌用户
     *
     * @param roleId 终末地游戏内角色 UID，取自账户列表
     * @return 干员与武器寻访记录，响应仅允许私人缓存
     */
    @Operation(summary = "按角色读取寻访记录（OAuth）")
    @GetMapping("/record")
    public ResponseEntity<Result<EfPoolRecordVO>> getPoolRecordData(@RequestParam("roleId") String roleId) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_READ);
        EfPoolRecordVO data = poolRecordTaskService.getPoolRecordData(UserContext.requireUid(), roleId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, POOL_RECORD_CACHE_CONTROL)
                .body(Result.success(data));
    }
}
