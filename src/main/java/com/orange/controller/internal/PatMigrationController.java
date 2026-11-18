package com.orange.controller.internal;

import com.orange.common.util.Result;
import com.orange.entity.vo.pat.PatMigrationResultVO;
import com.orange.service.PatMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAT 迁移触发端点（一次性运维）
 *
 * <p>无参数、无鉴权、同步执行，且会删除旧链路在 Redis 中的令牌明细——它天生只能短暂存在。
 * 本次按需求直接随应用启动装配、<strong>不设开关</strong>，用完立即删除本类并重新发布，
 * 不要把它留在后续版本里。</p>
 *
 * <p>既然没有开关，暴露面只能靠部署侧兜住：必须限定内网访问（网关、安全组或白名单），
 * 不得对外网开放。</p>
 *
 * <p>执行前置条件（缺一不可）：新链路已能校验 PAT、旧表已备份、迁移期间停写。</p>
 *
 * <p>搬迁为全量：不逐条判断旧令牌在 Redis 中是否仍有效，已撤销的签发记录也会变成可用 PAT，
 * 该代价已确认接受。详见 {@code PatMigrationService}。</p>
 *
 * @author UserCenter
 */
@Tag(name = "PAT 迁移（一次性）")
@RestController
@RequestMapping("/internal/pat-migration")
public class PatMigrationController {

    private final PatMigrationService patMigrationService;

    /**
     * 构造器注入服务
     *
     * @param patMigrationService 迁移服务
     */
    public PatMigrationController(PatMigrationService patMigrationService) {
        this.patMigrationService = patMigrationService;
    }

    /**
     * 触发一次全量迁移（幂等，重复调用不会翻倍）
     *
     * @return 迁移统计
     */
    @Operation(summary = "触发旧自定义令牌迁移（一次性运维，用完即删）")
    @PostMapping("/run")
    public Result<PatMigrationResultVO> run() {
        return Result.success(patMigrationService.run());
    }
}
