package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户配置 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface UserConfigMapper extends BaseMapper<UserConfig> {

    /**
     * 统计指定用户全部有效配置内容的总字节数（含各客户端，LONG TEXT 按字节计）
     *
     * @param uid 用户 uid
     * @return 总字节数，无记录时返回 0
     */
    @Select("SELECT IFNULL(SUM(LENGTH(config)), 0) FROM user_config WHERE uid = #{uid} AND delete_flag = 0")
    long sumConfigBytes(@Param("uid") Long uid);
}
