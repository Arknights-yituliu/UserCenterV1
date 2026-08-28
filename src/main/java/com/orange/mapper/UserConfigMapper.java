package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户配置 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface UserConfigMapper extends BaseMapper<UserConfig> {
}
