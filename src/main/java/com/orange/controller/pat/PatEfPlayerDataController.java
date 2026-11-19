package com.orange.controller.pat;

import com.orange.common.context.PatContext;
import com.orange.common.context.UserContext;
import com.orange.common.enums.OAuthScope;
import com.orange.common.util.PatPayloadUtil;
import com.orange.common.util.Result;
import com.orange.entity.dto.efplayer.EfPlayerDataSaveRequest;
import com.orange.entity.vo.efplayer.EfPlayerDataSaveResultVO;
import com.orange.entity.vo.efplayer.EfPlayerDataVO;
import com.orange.service.EfPlayerDataService;
import com.orange.service.PatPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAT 终末地玩家数据接口。
 *
 * <p>读取时，新令牌按 payload 的 game_uid + server_id 定位角色，业务层校验角色绑定；
 * 无前缀的旧令牌读取当前用户已绑定角色中最近保存的玩家数据。
 * 账号完全由令牌决定，不接受查询参数指定角色、服务器或用户。</p>
 *
 * <p>上传时，目标角色与服务器取自请求体，不受 payload 限制；用户身份取自 PAT，
 * 复用 OAuth 上传服务，首次上传自动建立绑定。</p>
 *
 * @author UserCenter
 */
@Tag(name = "PAT 终末地玩家数据接口")
@RestController
@RequestMapping("/pat/ef-accounts")
public class PatEfPlayerDataController {

    private static final String PLAYER_DATA_CACHE_CONTROL = "private, no-cache";

    private final EfPlayerDataService efPlayerDataService;

    public PatEfPlayerDataController(EfPlayerDataService efPlayerDataService) {
        this.efPlayerDataService = efPlayerDataService;
    }

    /**
     * 读取 PAT 指定角色的玩家数据，响应结构与 OAuth 版一致。
     *
     * <p>新令牌的 game_uid 缺失或空白时返回 10002，不能回退到其它角色；
     * server_id 省略或空白时按空串定位，与 OAuth 显式指定角色时的规则一致。</p>
     *
     * @return 玩家数据，绑定存在但数据行缺失时为 null
     */
    @Operation(summary = "读取 PAT 对应角色的终末地玩家数据（PAT）")
    @GetMapping("/player-data")
    public ResponseEntity<Result<EfPlayerDataVO>> getPlayerData() {
        UserContext.requireScope(OAuthScope.EF_DATA_READ);
        PatPrincipal principal = PatContext.require();
        String roleId = null;
        String serverId = null;
        if (principal.isPrefixed()) {
            roleId = PatPayloadUtil.requireGameUid(principal.getPayload());
            serverId = principal.getPayload().getServerId();
        }
        EfPlayerDataVO data = efPlayerDataService.getPlayerData(UserContext.requireUid(), roleId, serverId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, PLAYER_DATA_CACHE_CONTROL)
                .body(Result.success(data));
    }

    /**
     * 保存终末地玩家数据，与 OAuth 上传接口共用请求体、响应及保存逻辑。
     *
     * <p>目标角色取自请求体 roleId + serverId，无需预先绑定，也不核对 PAT payload。
     * 新旧 PAT 均须具备终末地写权限，用户身份始终取自认证上下文。</p>
     *
     * @param request 保存参数
     * @return 本次写入的角色标识与保存时间
     */
    @Operation(summary = "保存终末地玩家数据（PAT）")
    @PostMapping("/player-data/save")
    public Result<EfPlayerDataSaveResultVO> savePlayerData(@Valid @RequestBody EfPlayerDataSaveRequest request) {
        UserContext.requireScope(OAuthScope.EF_DATA_WRITE);
        return Result.success(efPlayerDataService.savePlayerData(UserContext.requireUid(), request));
    }
}
