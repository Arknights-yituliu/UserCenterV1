package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkOperatorStateStatistics;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 干员养成数据统计结果 Mapper（ak_operator_state_statistics）
 *
 * <p>本表只有几百行，读取直接用 BaseMapper 的 {@code selectList} 全表读取即可。写入由统计任务
 * 在同一事务内「清空 + 逐条插入」完成，因此额外提供一个显式的清表方法，避免依赖空条件删除的语义
 * （也便于日后接入防全表操作插件时不被误拦）。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkOperatorStateStatisticsMapper extends BaseMapper<AkOperatorStateStatistics> {

    /**
     * 清空统计结果表（整表替换的第一步）
     *
     * <p>统计每次都是全量重算，直接整表删除比逐条比对更简单，也不会残留已下架干员。
     * 调用方必须在事务内执行，否则读方会看到「已删除、尚未插入」的空窗。</p>
     *
     * @return 删除的行数
     */
    @Delete("DELETE FROM ak_operator_state_statistics")
    int deleteAll();
}
