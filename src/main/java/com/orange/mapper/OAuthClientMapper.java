package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthClient;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 客户端 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface OAuthClientMapper extends BaseMapper<OAuthClient> {
}
