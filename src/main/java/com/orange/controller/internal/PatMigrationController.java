package com.orange.controller.internal;

import com.orange.common.util.Result;
import com.orange.entity.vo.pat.PatMigrationResultVO;
import com.orange.service.PatMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PAT 迁移触发端点（一次性构建专用）
 *
 * <p>无参数、无鉴权、同步执行——因此默认<strong>不装配</strong>：只有显式配置
 * {@code pat.migration.enabled=true} 时才会注册该 Bean。这是把「跑完即删」落成编译期之外
 * 的开关，避免正式版本里长期挂着一个「任何人访问即触发一次不可逆全表迁移」的按钮。</p>
 *
 * <p>执行前置条件（缺一不可）：新链路已能校验 PAT、旧表已备份、迁移期间停写。
 * 跑完把开关关掉并重新发布。</p>
 *
 * @author UserCenter
 */
@Tag(name = "PAT 迁移（一次性）")
@RestController
@RequestMapping("/internal/pat-migration")
@ConditionalOnProperty(name = "pat.migration.enabled", havingValue = "true")
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
    @Operation(summary = "触发旧自定义令牌迁移（一次性，默认关闭）")
    @PostMapping("/run")
    public Result<PatMigrationResultVO> run() {
        return Result.success(patMigrationService.run());
    }
}
