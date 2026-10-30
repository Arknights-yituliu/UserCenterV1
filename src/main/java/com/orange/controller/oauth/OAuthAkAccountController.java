package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.Result;
import com.orange.entity.dto.akoperator.OperatorStateManualSaveRequest;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.entity.vo.akoperator.OperatorVO;
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

/** OAuth 授权访问的游戏账号与干员数据接口。 */
@Tag(name = "OAuth 游戏账号与干员数据接口")
@RestController
@RequestMapping("/oauth2/ak-accounts")
public class OAuthAkAccountController {

    /** 干员全量读取响应只允许私人缓存，且每次都必须回源校验。 */
    private static final String OPERATOR_CACHE_CONTROL = "private, no-cache";

    private final AkAccountService akAccountService;

    private final AkOperatorStateManualService akOperatorStateManualService;

    /**
     * 构造器注入服务
     *
     * @param akAccountService        游戏账号与干员数据服务
     * @param akOperatorStateManualService 用户手动修改版干员数据服务
     */
    public OAuthAkAccountController(AkAccountService akAccountService,
                                    AkOperatorStateManualService akOperatorStateManualService) {
        this.akAccountService = akAccountService;
        this.akOperatorStateManualService = akOperatorStateManualService;
    }

    /**
     * 查询当前 OAuth 用户在当前 OAuth 客户端下已绑定的游戏账号。
     *
     * @return 已绑定游戏账号列表
     */
    @Operation(summary = "查询已绑定的游戏账号列表（OAuth）")
    @GetMapping
    public Result<List<AkAccountVO>> listAccounts() {
        UserContext.requireScope(OAuthScope.GAMA_DATA_READ);
        return Result.success(akAccountService.listBoundAccounts(UserContext.requireUid()));
    }

    /**
     * 全量读取某游戏账号的干员数据。
     *
     * @param akUid 游戏账号 UID
     * @return 干员全量数据
     */
    @Operation(summary = "全量读取游戏账号的干员数据（OAuth）")
    @GetMapping("/operators")
    public ResponseEntity<Result<OperatorListVO>> listOperators(@RequestParam("akUid") String akUid) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_READ);
        OperatorListVO data = akAccountService.listOperators(UserContext.requireUid(), akUid);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, OPERATOR_CACHE_CONTROL)
                .body(Result.success(data));
    }

    /**
     * 批量保存某游戏账号的角色信息与干员数据，目标账号取自请求体 playerInfo.akUid。
     *
     * @param request 保存参数
     * @return 新增、更新与未变更数量
     */
    @Operation(summary = "批量保存角色信息与干员数据（OAuth）")
    @PostMapping("/operators/save")
    public Result<OperatorSaveResultVO> saveOperators(@Valid @RequestBody OperatorSaveRequest request) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_WRITE);
        return Result.success(akAccountService.saveOperators(UserContext.requireUid(), request));
    }

    /**
     * 读取当前 OAuth 用户本人的手动修改版干员数据：每个用户一份，与具体游戏账号无关。
     *
     * @return 手动版干员列表
     */
    @Operation(summary = "读取本人的手动修改版干员数据（OAuth）")
    @GetMapping("/manual-operators")
    public ResponseEntity<Result<List<OperatorVO>>> listManualOperators() {
        UserContext.requireScope(OAuthScope.GAMA_DATA_READ);
        List<OperatorVO> data = akOperatorStateManualService.listManualOperators(UserContext.requireUid());
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, OPERATOR_CACHE_CONTROL)
                .body(Result.success(data));
    }

    /**
     * 保存当前 OAuth 用户本人的手动修改版干员数据。
     *
     * @param request 保存参数（非空干员数组）
     * @return 新增/更新/未变更条数统计
     */
    @Operation(summary = "保存本人的手动修改版干员数据（OAuth）")
    @PostMapping("/manual-operators/save")
    public Result<OperatorSaveResultVO> saveManualOperators(@Valid @RequestBody OperatorStateManualSaveRequest request) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_WRITE);
        return Result.success(akOperatorStateManualService.saveManualOperators(UserContext.requireUid(), request));
    }
}
