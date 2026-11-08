package com.orange.entity.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 皮肤持有统计结果实体（ak_skin_statistics）
 *
 * <p>一行一个皮肤，只保留最新一轮统计结果，整表由统计任务在同一事务内「清表 + 批量插入」替换。
 * {@code own} 为拥有该皮肤的账号数，{@code totalAccounts} 为本轮参与统计的账号总数（持有率分母），
 * 两者口径来自同一轮扫描，可直接相除得到持有率。</p>
 *
 * <p>不保存获得时间：玩家数据里的 gain_ts 只能得到「样本中的最早/最晚」，受样本覆盖影响大，
 * 容易被误读成皮肤上线时间；上线、复刻、是否在售属于游戏静态数据与运营配置的职责，
 * 不从这里反推。</p>
 *
 * <p>{@code skinId} 取自 ak_skin_state.skin_id，区分大小写，因此本表排序规则为 utf8mb4_bin。</p>
 *
 * @author UserCenter
 */
@TableName("ak_skin_statistics")
public class AkSkinStatistics {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 皮肤编码，对应 ak_skin_state.skin_id，区分大小写 */
    private String skinId;

    /** 拥有该皮肤的账号数 */
    private Long own;

    /** 本轮参与统计的账号总数（持有率分母） */
    private Long totalAccounts;

    /** 本轮统计的完成时间 */
    private LocalDateTime statisticsTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkinId() {
        return skinId;
    }

    public void setSkinId(String skinId) {
        this.skinId = skinId;
    }

    public Long getOwn() {
        return own;
    }

    public void setOwn(Long own) {
        this.own = own;
    }

    public Long getTotalAccounts() {
        return totalAccounts;
    }

    public void setTotalAccounts(Long totalAccounts) {
        this.totalAccounts = totalAccounts;
    }

    public LocalDateTime getStatisticsTime() {
        return statisticsTime;
    }

    public void setStatisticsTime(LocalDateTime statisticsTime) {
        this.statisticsTime = statisticsTime;
    }
}
