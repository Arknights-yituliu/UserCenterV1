package com.orange.service;

import com.orange.entity.vo.akoperator.SkinStatisticsVO;

import java.util.List;

/**
 * 皮肤持有统计服务
 *
 * <p>全表分片扫描 ak_skin_state，汇总每个皮肤被多少账号拥有，并给出本轮参与统计的账号总数
 * 作为持有率分母。属于离线跑批能力，
 * 单次执行会全表扫描，不要放在在线请求路径上频繁调用。</p>
 *
 * <p>统计与查询拆成两个动作：{@link #refreshSkinStatistics()} 负责跑统计并整表替换落库，
 * {@link #getSkinStatistics()} 只读结果表；在线接口一律走后者。</p>
 *
 * @author UserCenter
 */
public interface AkSkinStatisticsService {

    /**
     * 全量统计所有皮肤的持有情况
     *
     * <p>只计算结果不落库，耗时较长；需要落库请用 {@link #refreshSkinStatistics()}。</p>
     *
     * @return 按皮肤编码升序排列的统计结果，无数据时返回空列表
     */
    List<SkinStatisticsVO> collectSkinStatistics();

    /**
     * 重新统计皮肤数据，并在同一事务内整表替换统计结果表
     *
     * @return 本轮写入的皮肤数量
     */
    int refreshSkinStatistics();

    /**
     * 异步触发一轮皮肤数据统计，供定时调度使用
     *
     * <p>立即返回，统计在后台线程执行；同一时刻只允许一轮统计在跑，
     * 已在跑时本次触发被忽略，避免两轮全表扫描同时进行并互相覆盖结果。</p>
     *
     * @return true 表示本次已成功触发；false 表示已有一轮统计在执行，本次未触发
     */
    boolean triggerSkinStatisticsRefresh();

    /**
     * 读取最近一轮皮肤统计结果
     *
     * @return 按皮肤编码升序排列的统计结果，尚未跑过统计时返回空列表
     */
    List<SkinStatisticsVO> getSkinStatistics();
}
