package com.lhs.uc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhs.uc.entity.po.UserInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 全局用户 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface UserInfoMapper extends BaseMapper<UserInfo> {
}
