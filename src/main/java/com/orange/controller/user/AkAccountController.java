package com.orange.controller.user;

import com.orange.common.context.UserContext;
import com.orange.common.util.Result;
import com.orange.entity.dto.akoperator.OperatorStateManualSaveRequest;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.service.AkAccountService;
import com.orange.service.AkOperatorStateManualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 游戏账号与干员数据接口（需登录态，路径由 /user/** 会话拦截器保护）
 *
 * <p>本接口由应用处理的成功与失败统一返回 HTTP 200，客户端只看 JSON 的 code 字段；
 * 参数校验与登录鉴权失败的转换由
 * {@link com.orange.common.exception.AkAccountExceptionHandler} 限定在本控制器内完成。</p>
 *
 * @author UserCenter
 */
@Tag(name = "游戏账号与干员数据接口")
@RestController
@RequestMapping("/user/ak-accounts")
public class AkAccountController {

    /** 干员全量读取响应的缓存策略：仅允许私人缓存且每次都要回源校验，不得被公共缓存共享 */
    private static final String OPERATOR_CACHE_CONTROL = "private, no-cache";

    private final AkAccountService akAccountService;

    private final AkOperatorStateManualService akOperatorStateManualService;

    /**
     * 构造器注入服务
     *
     * @param akAccountService        游戏账号与干员数据服务
     * @param akOperatorStateManualService 用户手动修改版干员数据服务
     */
    public AkAccountController(AkAccountService akAccountService,
                               AkOperatorStateManualService akOperatorStateManualService) {
        this.akAccountService = akAccountService;
        this.akOperatorStateManualService = akOperatorStateManualService;
    }

    /**
     * 查询当前用户已绑定的游戏账号列表（不含干员正文）
     *
     * @return 已绑定游戏账号列表
     */
    @Operation(summary = "查询已绑定的游戏账号列表")
    @GetMapping
    public Result<List<AkAccountVO>> listAccounts() {
        return Result.success(akAccountService.listBoundAccounts(UserContext.requireUid()));
    }

    /**
     * 全量读取干员数据：始终返回该条目全部干员，星级筛选由调用方完成
     *
     * <p>数据来源可由 {@code source} 显式指定；不传时后端按 {@code akUid}
     * 是否等于当前用户中心 UID 自动识别手动条目。</p>
     *
     * @param akUid  数据条目 UID：自动数据为游戏账号 UID，手动数据复用用户中心 UID
     * @param source 数据来源，可选：manual 手动版，skland 森空岛自动导入
     * @return 干员全量数据，响应仅允许私人缓存
     */
    @Operation(summary = "全量读取干员数据")
    @GetMapping("/operators")
    public ResponseEntity<Result<OperatorListVO>> listOperators(
            @RequestParam("akUid") String akUid,
            @RequestParam(value = "source", required = false) String source) {
        OperatorListVO data = akAccountService.listOperators(UserContext.requireUid(), akUid, source);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, OPERATOR_CACHE_CONTROL)
                .body(Result.success(data));
    }

    /**
     * 批量保存某游戏账号的角色信息与干员数据：传入的干员 ID 有则按属性比较后更新，无则新增，未传入的记录不处理
     *
     * @param request 保存参数（角色信息 + 非空干员数组），目标游戏账号取自 playerInfo.akUid
     * @return 新增/更新/未变更条数统计
     */
    @Operation(summary = "批量保存角色信息与干员数据")
    @PostMapping("/operators/save")
    public Result<OperatorSaveResultVO> saveOperators(@Valid @RequestBody OperatorSaveRequest request) {
        return Result.success(akAccountService.saveOperators(UserContext.requireUid(), request));
    }

    /**
     * 保存当前用户本人的手动修改版干员数据：传入的干员 ID 有则按属性比较后更新，无则新增，未传入的记录不处理
     *
     * @param request 保存参数（非空干员数组）
     * @return 新增/更新/未变更条数统计
     */
    @Operation(summary = "保存本人的手动修改版干员数据")
    @PostMapping("/manual-operators/save")
    public Result<OperatorSaveResultVO> saveManualOperators(@Valid @RequestBody OperatorStateManualSaveRequest request) {
        return Result.success(akOperatorStateManualService.saveManualOperators(UserContext.requireUid(), request));
    }
}
