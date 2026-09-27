package com.orange.service;

import com.orange.entity.vo.akoperator.OperatorStatisticsVO;

import java.util.List;

/**
 * 干员养成数据统计服务
 *
 * <p>全表分片扫描 ak_operator_state，汇总每个干员的精英化、技能与模组等级分布，
 * 并拉取外部实装时间数据计算有效样本数。属于离线跑批能力，单次执行会全表扫描，
 * 不要放在在线请求路径上频繁调用。</p>
 *
 * <p>统计与查询拆成两个动作：{@link #refreshOperatorStatistics()} 负责跑统计并整表替换落库，
 * {@link #getOperatorStatistics()} 只读结果表；在线接口一律走后者。</p>
 *
 * @author UserCenter
 */
public interface AkOperatorStateStatisticsService {

    /**
     * 全量统计所有干员的养成数据分布
     *
     * <p>只计算结果不落库，耗时较长；需要落库请用 {@link #refreshOperatorStatistics()}。</p>
     *
     * @return 按干员编码升序排列的统计结果，无数据时返回空列表
     */
    List<OperatorStatisticsVO> collectOperatorStatistics();

    /**
     * 重新统计干员数据，并在同一事务内整表替换统计结果表
     *
     * @return 本轮写入的干员数量
     */
    int refreshOperatorStatistics();

    /**
     * 异步触发一轮干员数据统计，供手动触发接口与定时调度使用
     *
     * <p>立即返回，统计在后台线程执行；同一时刻只允许一轮统计在跑，
     * 已在跑时本次触发被忽略，避免两轮全表扫描同时进行并互相覆盖结果。</p>
     *
     * @return true 表示本次已成功触发；false 表示已有一轮统计在执行，本次未触发
     */
    boolean triggerOperatorStatisticsRefresh();

    /**
     * 读取最近一轮干员统计结果
     *
     * @return 按干员编码升序排列的统计结果，尚未跑过统计时返回空列表
     */
    List<OperatorStatisticsVO> getOperatorStatistics();
}
