package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全局用户 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {

    @Select("SELECT * FROM user_info WHERE user_name = #{userName}")
    UserInfo selectByUserName(@Param("userName") String userName);

    /**
     * 锁定用户行，作为同一用户跨实例创建排班表时的串行化锁。
     */
    @Select("SELECT * FROM user_info WHERE uid = #{uid} FOR UPDATE")
    UserInfo selectByUidForUpdate(@Param("uid") Long uid);

    /** 按 UID 键集分页读取指定注册日尚无审核记录的用户，只查询送审所需字段。 */
    @Select("""
            <script>
            SELECT u.uid, u.nickname, u.register_time
            FROM user_info u
            WHERE u.register_time &gt;= #{startTime} AND u.register_time &lt; #{endTime}
            <if test="afterUid != null">AND u.uid &gt; #{afterUid}</if>
            AND NOT EXISTS (SELECT 1 FROM user_nickname_audit a WHERE a.uid = u.uid)
            ORDER BY u.uid ASC
            LIMIT #{limit}
            </script>
            """)
    List<UserInfo> selectUnauditedNicknames(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("afterUid") Long afterUid,
                                           @Param("limit") int limit);
}
