package com.orange.controller.user;

import com.orange.common.context.UserContext;
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
 * 终末地玩家数据接口（需登录态，路径由 /user/** 会话拦截器保护）
 *
 * <p>玩家数据整份以 JSON 对象上传，服务端直接保存 JSON 文本；归属取自会话中的 uid，
 * 客户端不能通过请求参数指定或覆盖。</p>
 *
 * @author UserCenter
 */
@Tag(name = "终末地玩家数据接口")
@RestController
@RequestMapping("/user/ef-accounts")
public class EfPlayerDataController {

    /** 玩家数据读取响应的缓存策略：仅允许私人缓存且每次都要回源校验，不得被公共缓存共享 */
    private static final String PLAYER_DATA_CACHE_CONTROL = "private, no-cache";

    private final EfPlayerDataService efPlayerDataService;

    /**
     * 构造器注入服务
     *
     * @param efPlayerDataService 终末地玩家数据服务
     */
    public EfPlayerDataController(EfPlayerDataService efPlayerDataService) {
        this.efPlayerDataService = efPlayerDataService;
    }

    /**
     * 查询当前用户已绑定的终末地角色列表（不含玩家数据正文）
     *
     * @return 已绑定角色列表，按最近导入时间倒序
     */
    @Operation(summary = "查询已绑定的终末地角色列表")
    @GetMapping
    public Result<List<EfAccountVO>> listAccounts() {
        return Result.success(efPlayerDataService.listBoundAccounts(UserContext.requireUid()));
    }

    /**
     * 读取指定角色的玩家数据：角色由 roleId + serverId 定位，且必须已绑定到当前用户
     *
     * @param roleId   终末地游戏内角色 UID
     * @param serverId 服务器 ID，可省略（按空串处理）
     * @return 玩家数据 JSON 对象，响应仅允许私人缓存
     */
    @Operation(summary = "读取终末地玩家数据")
    @GetMapping("/player-data")
    public ResponseEntity<Result<EfPlayerDataVO>> getPlayerData(
            @RequestParam("roleId") String roleId,
            @RequestParam(value = "serverId", required = false) String serverId) {
        EfPlayerDataVO data = efPlayerDataService.getPlayerData(UserContext.requireUid(), roleId, serverId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, PLAYER_DATA_CACHE_CONTROL)
                .body(Result.success(data));
    }

    /**
     * 保存终末地玩家数据：已存在则覆盖 JSON 文本，不存在则新增，首次上传同时建立角色绑定
     *
     * @param request 保存参数（roleId + serverId + playerData）
     * @return 本次写入的角色标识与保存时间
     */
    @Operation(summary = "保存终末地玩家数据")
    @PostMapping("/player-data/save")
    public Result<EfPlayerDataSaveResultVO> savePlayerData(@Valid @RequestBody EfPlayerDataSaveRequest request) {
        return Result.success(efPlayerDataService.savePlayerData(UserContext.requireUid(), request));
    }
}
