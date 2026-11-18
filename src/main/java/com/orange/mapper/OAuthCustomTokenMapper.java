package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.OAuthCustomToken;
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
     * 统计旧表中仍未过期的自定义令牌条数（迁移对账的 total 口径）
     *
     * @return 未过期行数
     */
    @Select("SELECT COUNT(*) FROM oauth_custom_token WHERE expire_time > NOW()")
    long countAlive();

    /**
     * 按主键游标分页读取未过期的自定义令牌（PAT 迁移专用，只读）
     *
     * <p>用 {@code id > cursor ORDER BY id} 而非 offset 分页：迁移过程中不依赖行数稳定，
     * 中途失败重跑也不会跳过或重复处理行（配合摘要唯一键实现幂等）。</p>
     *
     * @param cursor 上一批最大的 id（首批传 0）
     * @param limit  批大小
     * @return 该批令牌记录（含明文，仅迁移进程内使用）
     */
    @Select("SELECT id, uid, client_id AS clientId, name, token, scope, "
            + "expires_in AS expiresIn, expire_time AS expireTime, create_time AS createTime "
            + "FROM oauth_custom_token "
            + "WHERE expire_time > NOW() AND id > #{cursor} "
            + "ORDER BY id ASC LIMIT #{limit}")
    List<OAuthCustomToken> selectAliveAfterId(@Param("cursor") long cursor, @Param("limit") int limit);
}
