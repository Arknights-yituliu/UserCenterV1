package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthCustomToken;
import com.orange.entity.vo.oauth.OAuthCustomTokenVO;
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
}
