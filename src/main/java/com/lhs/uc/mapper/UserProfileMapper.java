package com.lhs.uc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lhs.uc.entity.po.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站点扩展资料 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
