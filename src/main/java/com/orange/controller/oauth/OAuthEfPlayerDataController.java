package com.orange.controller.oauth;

import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.Result;
import com.orange.entity.dto.efplayer.EfPlayerDataSaveRequest;
import com.orange.entity.vo.efplayer.EfAccountVO;
import com.orange.entity.vo.efplayer.EfPlayerDataSaveResultVO;
import com.orange.entity.vo.efplayer.EfPlayerDataVO;
import com.orange.service.EfPlayerDataService;
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
 * OAuth 授权访问的终末地玩家数据接口
 *
 * <p>查询绑定角色与读取玩家数据需要 {@code endfield-data.read}，保存玩家数据需要
 * {@code endfield-data.write}；上级范围 {@code gama-data.read} / {@code gama-data.write} 同样放行。
 * 用户身份取自 access_token，请求参数不能指定或覆盖；
 * 角色归属校验与用户侧接口一致，玩家数据按 {@code roleId} + {@code serverId} 由全部绑定用户共享。</p>
 *
 * @author UserCenter
 */
@Tag(name = "OAuth 终末地玩家数据接口")
@RestController
@RequestMapping("/oauth2/ef-accounts")
public class OAuthEfPlayerDataController {

    /** 玩家数据读取响应的缓存策略：仅允许私人缓存且每次回源校验，不得被公共缓存共享 */
    private static final String PLAYER_DATA_CACHE_CONTROL = "private, no-cache";

    private final EfPlayerDataService efPlayerDataService;

    /**
     * 构造器注入服务
     *
     * @param efPlayerDataService 终末地玩家数据服务
     */
    public OAuthEfPlayerDataController(EfPlayerDataService efPlayerDataService) {
        this.efPlayerDataService = efPlayerDataService;
    }

    /**
     * 查询当前令牌用户已绑定的终末地角色列表（不含玩家数据正文）
     *
     * @return 已绑定角色列表，按最近导入时间倒序
     */
    @Operation(summary = "查询已绑定的终末地角色列表（OAuth）")
    @GetMapping
    public Result<List<EfAccountVO>> listAccounts() {
        UserContext.requireScope(OAuthScope.EF_DATA_READ);
        return Result.success(efPlayerDataService.listBoundAccounts(UserContext.requireUid()));
    }

    /**
     * 读取玩家数据：省略角色时默认读取最新导入，指定角色时必须已绑定到当前令牌用户
     *
     * @param roleId   终末地游戏内角色 UID，可省略（读取最新玩家数据）
     * @param serverId 服务器 ID，指定角色时可省略（按空串定位）
     * @return 玩家数据 JSON 对象，响应仅允许私人缓存
     */
    @Operation(summary = "读取终末地玩家数据（OAuth）", description = "不传roleId时默认返回当前用户已绑定角色中最新导入的玩家数据")
    @GetMapping("/player-data")
    public ResponseEntity<Result<EfPlayerDataVO>> getPlayerData(
            @RequestParam(value = "roleId", required = false) String roleId,
            @RequestParam(value = "serverId", required = false) String serverId) {
        UserContext.requireScope(OAuthScope.EF_DATA_READ);
        EfPlayerDataVO data = efPlayerDataService.getPlayerData(UserContext.requireUid(), roleId, serverId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, PLAYER_DATA_CACHE_CONTROL)
                .body(Result.success(data));
    }

    /**
     * 保存终末地玩家数据：已存在则整份覆盖，不存在则新增，首次上传同时建立角色绑定
     *
     * @param request 保存参数（roleId + serverId + playerData）
     * @return 本次写入的角色标识与保存时间
     */
    @Operation(summary = "保存终末地玩家数据（OAuth）")
    @PostMapping("/player-data/save")
    public Result<EfPlayerDataSaveResultVO> savePlayerData(@Valid @RequestBody EfPlayerDataSaveRequest request) {
        UserContext.requireScope(OAuthScope.EF_DATA_WRITE);
        return Result.success(efPlayerDataService.savePlayerData(UserContext.requireUid(), request));
    }
}
