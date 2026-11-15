package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
