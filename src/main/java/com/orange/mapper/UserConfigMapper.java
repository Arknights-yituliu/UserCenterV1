package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户配置 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface UserConfigMapper extends BaseMapper<UserConfig> {

    @Select("SELECT * FROM user_config "
            + "WHERE id = #{id} AND uid = #{uid} AND client_id = #{clientId}")
    UserConfig selectOwnedById(@Param("id") Long id,
                               @Param("uid") Long uid,
                               @Param("clientId") String clientId);

    @Select("SELECT * FROM user_config "
            + "WHERE id = #{id} AND uid = #{uid} AND client_id = #{clientId} FOR UPDATE")
    UserConfig selectOwnedByIdForUpdate(@Param("id") Long id,
                                        @Param("uid") Long uid,
                                        @Param("clientId") String clientId);

    @Select("SELECT * FROM user_config WHERE uid = #{uid} AND client_id = #{clientId} "
            + "AND category = #{category} AND version = #{version} AND name = #{name}")
    UserConfig selectByIdentity(@Param("uid") Long uid,
                                @Param("clientId") String clientId,
                                @Param("category") String category,
                                @Param("version") String version,
                                @Param("name") String name);

    @Update("UPDATE user_config SET source = #{source}, note = #{note}, config = #{config}, "
            + "content_hash = #{newHash}, config_bytes = #{newBytes}, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND uid = #{uid} AND client_id = #{clientId}")
    int updateOwnedById(@Param("id") Long id,
                        @Param("uid") Long uid,
                        @Param("clientId") String clientId,
                        @Param("source") String source,
                        @Param("note") String note,
                        @Param("config") String config,
                        @Param("newHash") String newHash,
                        @Param("newBytes") long newBytes);

    @Update("UPDATE user_config SET source = #{source}, note = #{note}, config = #{config}, "
            + "content_hash = #{newHash}, config_bytes = #{newBytes}, update_time = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND uid = #{uid} AND client_id = #{clientId} "
            + "AND content_hash = #{expectedHash}")
    int updateIfHashMatches(@Param("id") Long id,
                            @Param("uid") Long uid,
                            @Param("clientId") String clientId,
                            @Param("source") String source,
                            @Param("note") String note,
                            @Param("config") String config,
                            @Param("newHash") String newHash,
                            @Param("newBytes") long newBytes,
                            @Param("expectedHash") String expectedHash);

    @Delete("DELETE FROM user_config WHERE id = #{id} AND uid = #{uid} AND client_id = #{clientId}")
    int deleteOwnedById(@Param("id") Long id,
                        @Param("uid") Long uid,
                        @Param("clientId") String clientId);
}
