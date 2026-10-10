package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthCustomToken;
import com.orange.entity.vo.oauth.OAuthCustomTokenVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户自定义有效期 access_token 签发记录 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface OAuthCustomTokenMapper extends BaseMapper<OAuthCustomToken> {

    /**
     * 查询指定用户签发的全部自定义令牌，按签发时间倒序
     *
     * <p>包含已过期记录（由 expire_time 与剩余秒数体现），便于用户回看历史签发。</p>
     *
     * @param uid 用户 uid
     * @return 自定义令牌列表（含应用名称与剩余有效期）
     */
    @Select("SELECT t.id AS id, t.client_id AS clientId, c.client_name AS clientName, "
            + "t.name AS name, t.token AS token, t.scope AS scope, t.expires_in AS expiresIn, "
            + "t.expire_time AS expireTime, t.create_time AS createdAt, "
            + "TIMESTAMPDIFF(SECOND, NOW(), t.expire_time) AS expiresInSeconds "
            + "FROM oauth_custom_token t "
            + "LEFT JOIN oauth_client c ON c.id = t.client_id "
            + "WHERE t.uid = #{uid} "
            + "ORDER BY t.create_time DESC")
    List<OAuthCustomTokenVO> selectByUid(@Param("uid") Long uid);

    /**
     * 查询指定用户在指定应用（OAuth 客户端）下签发的自定义令牌，按签发时间倒序
     *
     * <p>用于 refresh_token 认证的 OAuth 侧查询：调用方只能看到自己 client_id 名下的记录，
     * 避免通过某个应用的 refresh_token 读到该用户在其它应用的自定义令牌明文。</p>
     *
     * @param uid      用户 uid
     * @param clientId 目标应用（OAuth 客户端）ID
     * @return 该应用下的自定义令牌列表（含应用名称与剩余有效期）
     */
    @Select("SELECT t.id AS id, t.client_id AS clientId, c.client_name AS clientName, "
            + "t.name AS name, t.token AS token, t.scope AS scope, t.expires_in AS expiresIn, "
            + "t.expire_time AS expireTime, t.create_time AS createdAt, "
            + "TIMESTAMPDIFF(SECOND, NOW(), t.expire_time) AS expiresInSeconds "
            + "FROM oauth_custom_token t "
            + "LEFT JOIN oauth_client c ON c.id = t.client_id "
            + "WHERE t.uid = #{uid} AND t.client_id = #{clientId} "
            + "ORDER BY t.create_time DESC")
    List<OAuthCustomTokenVO> selectByUidAndClientId(@Param("uid") Long uid,
                                                    @Param("clientId") String clientId);

    /**
     * 批量插入自定义令牌记录（供旧系统令牌导入使用）
     *
     * <p>调用方按每批约 500 条切片；create_time / update_time 由数据库默认值填充。</p>
     *
     * @param items 待插入记录，调用方保证非空且字段已校验
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO oauth_custom_token "
            + "(uid, client_id, name, token, scope, expires_in, expire_time) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.uid}, #{it.clientId}, #{it.name}, #{it.token}, #{it.scope}, "
            + "#{it.expiresIn}, #{it.expireTime})"
            + "</foreach></script>")
    int batchInsert(@Param("items") List<OAuthCustomToken> items);
}
