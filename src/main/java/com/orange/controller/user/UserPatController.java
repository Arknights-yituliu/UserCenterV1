package com.orange.controller.user;

import com.orange.common.context.UserContext;
import com.orange.common.util.Result;
import com.orange.entity.dto.pat.PatIssueRequest;
import com.orange.entity.dto.pat.PatRevokeRequest;
import com.orange.entity.vo.pat.PatIssueVO;
import com.orange.entity.vo.pat.PatVO;
import com.orange.service.PatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 个人访问令牌自助接口（需登录态）
 *
 * <p>申请、查看、撤销 PAT 时，请求头传的仍是用户会话；PAT 本身不是本组接口的凭证。
 * 路径用 {@code /list} 而非资源根，给以后的 {@code GET /user/pat/{id}} 让路——否则
 * {@code {id}} 会捕获 {@code list}。</p>
 *
 * @author UserCenter
 */
@Tag(name = "个人访问令牌")
@RestController
@RequestMapping("/user/pat")
public class UserPatController {

    private final PatService patService;

    /**
     * 构造器注入服务
     *
     * @param patService PAT 服务
     */
    public UserPatController(PatService patService) {
        this.patService = patService;
    }

    /**
     * 签发一枚 PAT（明文原样入库，列表可回看）
     *
     * @param request 签发参数
     * @return 含明文令牌的签发结果
     */
    @Operation(summary = "签发个人访问令牌")
    @PostMapping
    public Result<PatIssueVO> issue(@Valid @RequestBody PatIssueRequest request) {
        return Result.success(patService.issue(UserContext.requireUid(), request));
    }

    /**
     * 查看我的 PAT 列表（含令牌明文）
     *
     * @return 令牌列表
     */
    @Operation(summary = "查看我的个人访问令牌")
    @GetMapping("/list")
    public Result<List<PatVO>> list() {
        return Result.success(patService.list(UserContext.requireUid()));
    }

    /**
     * 按 id 撤销一条 PAT
     *
     * @param request 撤销参数
     * @return 空结果
     */
    @Operation(summary = "撤销个人访问令牌")
    @PostMapping("/revoke")
    public Result<Void> revoke(@Valid @RequestBody PatRevokeRequest request) {
        patService.revoke(UserContext.requireUid(), request.getId());
        return Result.success();
    }

    /**
     * 作废本人全部 PAT
     *
     * @return 空结果
     */
    @Operation(summary = "作废我的全部个人访问令牌")
    @PostMapping("/revoke-all")
    public Result<Void> revokeAll() {
        patService.revokeAllByUid(UserContext.requireUid());
        return Result.success();
    }
}
