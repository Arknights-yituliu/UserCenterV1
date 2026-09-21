package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.Result;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.service.AkAccountService;
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

    public OAuthAkAccountController(AkAccountService akAccountService) {
        this.akAccountService = akAccountService;
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
     * 批量保存某游戏账号的角色信息与干员数据。
     *
     * @param akUid 游戏账号 UID
     * @param request 保存参数
     * @return 新增、更新与未变更数量
     */
    @Operation(summary = "批量保存角色信息与干员数据（OAuth）")
    @PostMapping("/operators/save")
    public Result<OperatorSaveResultVO> saveOperators(@RequestParam("akUid") String akUid,
                                                     @Valid @RequestBody OperatorSaveRequest request) {
        UserContext.requireScope(OAuthScope.GAMA_DATA_WRITE);
        return Result.success(akAccountService.saveOperators(UserContext.requireUid(), akUid, request));
    }
}
