package com.orange.controller.user;

import com.orange.common.context.UserContext;
import com.orange.common.util.Result;
import com.orange.entity.dto.efplayer.EfPlayerDataSaveRequest;
import com.orange.entity.vo.efplayer.EfPlayerDataSaveResultVO;
import com.orange.service.EfPlayerDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
