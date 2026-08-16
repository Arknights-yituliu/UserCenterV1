package com.orange.common.util;


import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;

/**
 * 自定义雪花 ID 生成器（与 BackEndV3 保持一致）
 *
 * <p>结构：1bit 符号 | 41bit 相对时间戳（起点 2019-05-01）| 3bit workerId | 10bit 序列号。
 * 生成的 ID 约 13 位十进制，处于 JavaScript Number 安全整数范围内。</p>
 *
 * @author UserCenter
 */
public class IdGenerator {

    /** 起始时间戳（2019-05-01 00:00:00） */
    private static final long START_TIMESTAMP = 1556676000000L;

    /** 序列号位数（支持最大序列号 1023） */
    private static final long SEQUENCE_BITS = 10L;

    /** 最大序列号 */
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    /** workerId 位数 */
    private static final long WORKER_ID_BITS = 3L;

    /** 最大 workerId */
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1;

    /** workerId 左移位数 */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /** 时间戳左移位数 */
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /** 默认单例（workerId=1），全应用共享以保证同一毫秒内序列不冲突 */
    private static final IdGenerator DEFAULT_INSTANCE = new IdGenerator(1L);

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * 构造函数
     *
     * @param workerId 工作节点 ID (0-7)
     */
    public IdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                String.format("workerId必须在0到%d之间", MAX_WORKER_ID));
        }
        this.workerId = workerId;
    }

    /**
     * 获取默认单例
     *
     * @return workerId=1 的全局单例
     */
    public static IdGenerator getInstance() {
        return DEFAULT_INSTANCE;
    }

    /**
     * 生成下一个 ID
     *
     * @return 生成的唯一 ID
     */
    public synchronized long nextId() {
        long timestamp = timeGen();

        // 时钟回拨检查
        if (timestamp < lastTimestamp) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }

        // 同一毫秒内生成序列号
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 序列号溢出，等待下一毫秒
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            // 新时间戳，重置序列号
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        // 组合 ID：时间戳 | workerId | 序列号
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
             | (workerId << WORKER_ID_SHIFT)
             | sequence;
    }

    /**
     * 等待下一毫秒
     *
     * @param lastTimestamp 上次时间戳
     * @return 下一毫秒时间戳
     */
    protected long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳（毫秒）
     */
    protected long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * 解析 ID 的各个部分（用于调试和测试）
     *
     * @param id 要解析的 ID
     * @return 包含时间戳、workerId 和序列号的数组
     */
    public static long[] parseId(long id) {
        long timestamp = (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
        long workerId = (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
        long sequence = id & MAX_SEQUENCE;

        return new long[]{timestamp, workerId, sequence};
    }

    /**
     * 获取 ID 的生成时间
     *
     * @param id 要解析的 ID
     * @return 生成时间戳
     */
    public static long getTimestampFromId(long id) {
        return (id >> TIMESTAMP_SHIFT) + START_TIMESTAMP;
    }
}
