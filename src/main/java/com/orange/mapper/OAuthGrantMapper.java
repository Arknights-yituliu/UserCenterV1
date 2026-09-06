package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthGrant;
import com.orange.entity.vo.oauth.RefreshGrantVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * OAuth 授权台账 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface OAuthGrantMapper extends BaseMapper<OAuthGrant> {

    /**
     * 查询指定用户当前仍有效的授权记录（含应用名），按授权时间倒序
     *
     * @param uid 用户 uid
     * @return 授权记录列表（已吊销/已过期的不返回）
     */
    @Select("SELECT g.client_id AS clientId, c.client_name AS clientName, g.scope, "
            + "g.issue_time AS createdAt, "
            + "TIMESTAMPDIFF(SECOND, NOW(), g.expire_time) AS expiresInSeconds "
            + "FROM oauth_grant g "
            + "LEFT JOIN oauth_client c ON c.id = g.client_id "
            + "WHERE g.uid = #{uid} AND g.revoked = 0 AND g.expire_time > NOW() "
            + "ORDER BY g.issue_time DESC")
    List<RefreshGrantVO> selectValidGrants(@Param("uid") Long uid);

    /**
     * 按 refresh_token 摘要置为已吊销（精确吊销单条授权）
     *
     * @param tokenHash refresh_token 的 SHA-256
     * @return 受影响行数
     */
    @Update("UPDATE oauth_grant SET revoked = 1 "
            + "WHERE token_hash = #{tokenHash} AND revoked = 0")
    int markRevokedByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 把指定用户对指定应用（客户端）的全部未吊销授权置为已吊销（按应用整体撤销）
     *
     * @param uid      用户 uid
     * @param clientId 客户端 ID
     * @return 受影响行数
     */
    @Update("UPDATE oauth_grant SET revoked = 1 "
            + "WHERE uid = #{uid} AND client_id = #{clientId} AND revoked = 0")
    int markRevokedByUidAndClient(@Param("uid") Long uid, @Param("clientId") String clientId);

    /**
     * 把指定用户全部未吊销授权置为已吊销（按用户整体吊销，如 revoke-user）
     *
     * @param uid 用户 uid
     * @return 受影响行数
     */
    @Update("UPDATE oauth_grant SET revoked = 1 WHERE uid = #{uid} AND revoked = 0")
    int markRevokedByUid(@Param("uid") Long uid);
}
