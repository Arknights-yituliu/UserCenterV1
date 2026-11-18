package com.orange.service;

import com.orange.entity.vo.pat.PatMigrationResultVO;

/**
 * 旧自定义令牌 → PAT 的一次性全量迁移服务（阶段二）
 *
 * <p>行为固定：按 {@code oauth_custom_token} 全量搬一次，每批顺序为
 * {@code 批量 INSERT IGNORE → SREM uid 索引 → DEL 旧 key}，重复调用幂等、不会翻倍。</p>
 *
 * <p><strong>不做有效性判空</strong>：旧表里已被撤销（Redis 明细已删、签发记录仍在）的行
 * 会一并迁成可用 PAT，这是已确认接受的取舍，不是遗漏。</p>
 *
 * <p><strong>这是不可逆操作</strong>：旧 key 被删后，旧版本代码再也认不出这些令牌。
 * 只能在「新链路已能校验 PAT」的版本上执行，且执行前必须先备份旧表。</p>
 *
 * @author UserCenter
 */
public interface PatMigrationService {

    /**
     * 执行一次全量迁移（同步，跑完返回统计）
     *
     * @return 迁移统计（total / migrated / skipped / failed / elapsedMs）
     */
    PatMigrationResultVO run();
}
