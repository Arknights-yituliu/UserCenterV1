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
     * 把指定令牌家族的全部未吊销授权置为已吊销（refresh_token 重放时的整族吊销）
     *
     * @param familyId 令牌家族标识
     * @return 受影响行数
     */
    @Update("UPDATE oauth_grant SET revoked = 1 "
            + "WHERE family_id = #{familyId} AND revoked = 0")
    int markRevokedByFamily(@Param("familyId") String familyId);

    /**
     * refresh_token 轮转时把台账摘要替换为新值，同时补写令牌家族标识
     *
     * <p>轮转沿用同一条台账记录（授权时间与到期时间保持不变，刷新不会让「我的授权」列表新增条目），
     * 因此这里只替换摘要；历史记录没有家族标识时由本次轮转补写。</p>
     *
     * @param oldTokenHash 轮转前的 refresh_token 摘要
     * @param newTokenHash 轮转后的 refresh_token 摘要
     * @param familyId     令牌家族标识
     * @return 受影响行数；0 表示台账中没有对应的有效记录
     */
    @Update("UPDATE oauth_grant SET token_hash = #{newTokenHash}, family_id = #{familyId} "
            + "WHERE token_hash = #{oldTokenHash} AND revoked = 0")
    int rotateTokenHash(@Param("oldTokenHash") String oldTokenHash,
                        @Param("newTokenHash") String newTokenHash,
                        @Param("familyId") String familyId);

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

    /**
     * 更新指定用户对指定应用全部未吊销授权的授权范围（用户自助调整权限后同步台账）
     *
     * <p>台账是「我的授权」列表的数据来源，用户追加/取消权限后若不同步，
     * 列表会继续展示调整前的范围。</p>
     *
     * @param uid      用户 uid
     * @param clientId 客户端 ID
     * @param scope    调整后的授权范围（英文逗号分隔）
     * @return 受影响行数
     */
    @Update("UPDATE oauth_grant SET scope = #{scope} "
            + "WHERE uid = #{uid} AND client_id = #{clientId} AND revoked = 0")
    int updateScopeByUidAndClient(@Param("uid") Long uid, @Param("clientId") String clientId,
                                  @Param("scope") String scope);
}
