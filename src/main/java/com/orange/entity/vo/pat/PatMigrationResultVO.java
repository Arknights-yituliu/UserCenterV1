package com.orange.entity.vo.pat;

/**
 * 旧自定义令牌迁移的执行结果
 *
 * <p>口径：{@code total} 为迁移开始时旧表中未过期的行数，{@code migrated + skipped + failed == total}。
 * 重复调用幂等——已处理过的行在 Redis 里已无明细，会落进 {@code skipped}，不会翻倍。</p>
 *
 * @author UserCenter
 */
public class PatMigrationResultVO {

    /** 待处理总数（旧表 expire_time > NOW() 的行数） */
    private long total;

    /** 成功迁移数 */
    private long migrated;

    /** 跳过数（旧 key 已不存在：已吊销或已迁移过） */
    private long skipped;

    /** 失败数（单行异常，不阻断后续） */
    private long failed;

    /** 耗时（毫秒） */
    private long elapsedMs;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getMigrated() {
        return migrated;
    }

    public void setMigrated(long migrated) {
        this.migrated = migrated;
    }

    public long getSkipped() {
        return skipped;
    }

    public void setSkipped(long skipped) {
        this.skipped = skipped;
    }

    public long getFailed() {
        return failed;
    }

    public void setFailed(long failed) {
        this.failed = failed;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
