package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfPoolRecordTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;

/**
 * 寻访记录导入任务 Mapper（player_pool_record_task）
 *
 * @author UserCenter
 */
@Mapper
public interface EfPoolRecordTaskMapper extends BaseMapper<EfPoolRecordTask> {

    /**
     * 查询最早创建的、尚未开始处理的任务
     *
     * @return 待执行任务；没有待执行任务时返回 null
     */
    @Select("SELECT * FROM player_pool_record_task WHERE start_flag = false "
            + "ORDER BY create_time ASC LIMIT 1")
    EfPoolRecordTask selectFirstByStartFlagFalseOrderByCreateTimeAsc();

    /**
     * 把创建时间落在指定区间内的任务 token 覆盖为占位值，避免长期留存用户凭证
     *
     * <p>只扫描最近一段时间内的任务，避免每次定时任务都全表更新。</p>
     *
     * @param startTime   区间起点（含）
     * @param endTime     区间终点（不含）
     * @param replacement 覆盖后的占位值
     * @return 被覆盖的任务数
     */
    @Update("UPDATE player_pool_record_task SET token = #{replacement} "
            + "WHERE create_time >= #{startTime} AND create_time < #{endTime} "
            + "AND token IS NOT NULL AND token <> #{replacement}")
    int maskExpiredToken(@Param("startTime") Date startTime, @Param("endTime") Date endTime,
                         @Param("replacement") String replacement);
}
