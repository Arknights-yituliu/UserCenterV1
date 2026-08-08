package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AppInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 接入方应用 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface AppInfoMapper extends BaseMapper<AppInfo> {
}
