package com.orange.entity.vo.pat;

/**
 * 旧自定义令牌迁移的执行结果
 *
 * <p>口径：{@code total} 为迁移开始时旧表中未过期的行数，{@code migrated + failed == total}。
 * 全量搬迁不逐条判空，因此 {@code skipped} 恒为 0，保留该字段仅为不破坏既有响应结构。
 * 重复调用幂等——已写入的明文被唯一键忽略，不会翻倍。</p>
 *
 * @author UserCenter
 */
public class PatMigrationResultVO {

    /** 待处理总数（旧表 expire_time > NOW() 的行数） */
    private long total;

    /** 成功迁移数 */
    private long migrated;

    /** 跳过数（全量搬迁下恒为 0，保留字段仅为兼容既有响应结构） */
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
