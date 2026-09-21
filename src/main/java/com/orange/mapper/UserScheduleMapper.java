package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserSchedule;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 用户排班表 Mapper。 */
@Mapper
public interface UserScheduleMapper extends BaseMapper<UserSchedule> {

    @Select("SELECT COUNT(*) FROM user_schedule WHERE uid = #{uid}")
    long countByUid(@Param("uid") Long uid);

    @Select("SELECT * FROM user_schedule WHERE id = #{id} AND uid = #{uid} FOR UPDATE")
    UserSchedule selectOwnedByIdForUpdate(@Param("id") Long id, @Param("uid") Long uid);

    @Update("UPDATE user_schedule SET schedule = #{schedule}, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND uid = #{uid}")
    int updateOwnedSchedule(@Param("id") Long id, @Param("uid") Long uid,
                            @Param("schedule") String schedule);

    @Delete("DELETE FROM user_schedule WHERE id = #{id} AND uid = #{uid}")
    int deleteOwnedById(@Param("id") Long id, @Param("uid") Long uid);
}
