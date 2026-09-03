package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserConfigQuota;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户配置配额 Mapper。
 *
 * @author UserCenter
 */
@Mapper
public interface UserConfigQuotaMapper extends BaseMapper<UserConfigQuota> {

    @Insert("INSERT INTO user_config_quota (uid, used_bytes, limit_bytes) "
            + "VALUES (#{uid}, 0, #{defaultLimit}) "
            + "ON DUPLICATE KEY UPDATE uid = #{uid}")
    int initialize(@Param("uid") Long uid, @Param("defaultLimit") long defaultLimit);

    @Select("SELECT * FROM user_config_quota WHERE uid = #{uid} FOR UPDATE")
    UserConfigQuota selectForUpdate(@Param("uid") Long uid);
}
