package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthUserScope;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户自定义 OAuth 授权范围 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface OAuthUserScopeMapper extends BaseMapper<OAuthUserScope> {

    /**
     * 查询指定用户对指定应用的自定义授权范围
     *
     * @param uid      用户 uid
     * @param clientId 客户端 ID
     * @return 自定义范围记录；从未自定义过时返回 null
     */
    @Select("SELECT id, uid, client_id AS clientId, scopes, create_time AS createTime, update_time AS updateTime "
            + "FROM oauth_user_scope WHERE uid = #{uid} AND client_id = #{clientId}")
    OAuthUserScope selectByUidAndClient(@Param("uid") Long uid, @Param("clientId") String clientId);

    /**
     * 写入或覆盖用户对某应用的自定义授权范围（依赖 uk_uid_client 实现原子 upsert）
     *
     * <p>并发提交同一 (uid, client_id) 时由数据库唯一键保证只落一行，避免先查后写的竞态。</p>
     *
     * @param uid      用户 uid
     * @param clientId 客户端 ID
     * @param scopes   自定义范围（英文逗号分隔，调用方须保证非空）
     * @return 受影响行数
     */
    @Insert("INSERT INTO oauth_user_scope (uid, client_id, scopes) VALUES (#{uid}, #{clientId}, #{scopes}) "
            + "ON DUPLICATE KEY UPDATE scopes = VALUES(scopes), update_time = NOW()")
    int upsert(@Param("uid") Long uid, @Param("clientId") String clientId, @Param("scopes") String scopes);
}
