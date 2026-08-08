package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
