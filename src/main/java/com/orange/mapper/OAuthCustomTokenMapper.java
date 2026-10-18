package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthCustomToken;
import com.orange.entity.vo.oauth.OAuthCustomTokenVO;
import org.apache.ibatis.annotations.Delete;
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
     * 删除指定用户在指定应用名下的一条自定义令牌签发记录
     *
     * <p>按 uid + client_id + token 三条件删除：uid 限定记录归属的用户，client_id 限定
     * 调用方应用，token 定位具体一条。三者同时匹配才能删除，避免某个应用凭拿到的 token
     * 字符串越权吊销其它应用为用户签发的令牌。</p>
     *
     * @param uid      用户 uid
     * @param clientId 调用方应用（OAuth 客户端）ID
     * @param token    access_token 明文
     * @return 影响行数（0 表示记录不存在或不属于该用户/该应用）
     */
    @Delete("DELETE FROM oauth_custom_token "
            + "WHERE uid = #{uid} AND client_id = #{clientId} AND token = #{token}")
    int deleteByUidAndClientIdAndToken(@Param("uid") Long uid,
                                       @Param("clientId") String clientId,
                                       @Param("token") String token);

    /**
     * 删除指定用户名下的一条自定义令牌签发记录（不限定应用）
     *
     * <p>按 uid + token 双条件删除：uid 限定记录归属的用户，token 定位具体一条。用于用户
     * 自助侧吊销——用户是自己所有令牌的归属者，可跨应用吊销名下任意一条，无须指明 client_id。</p>
     *
     * @param uid   用户 uid
     * @param token access_token 明文
     * @return 影响行数（0 表示记录不存在或不属于该用户）
     */
    @Delete("DELETE FROM oauth_custom_token WHERE uid = #{uid} AND token = #{token}")
    int deleteByUidAndToken(@Param("uid") Long uid, @Param("token") String token);
}
