package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthClientOrigin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * OAuth 客户端 Origin Mapper。
 *
 * @author UserCenter
 */
@Mapper
public interface OAuthClientOriginMapper extends BaseMapper<OAuthClientOrigin> {

    /**
     * 单表查询当前启用且审批通过的 CORS Origin。
     *
     * @return 已允许的 Origin 列表
     */
    @Select("SELECT origin FROM oauth_client_origin WHERE enabled = 1 AND admin_approved = 1")
    List<String> selectApprovedOrigins();
}
