package com.lhs.uc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhs.uc.entity.po.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录日志 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
}
