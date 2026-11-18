package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.TlcPat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人访问令牌 Mapper（tlc_pat）
 *
 * <p>常规查询与删除直接用 {@link BaseMapper} 的条件构造器；这里只保留一条需要
 * 数据库语义保证的语句：迁移时按明文唯一键「已存在则跳过」插入（幂等）。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface TlcPatMapper extends BaseMapper<TlcPat> {

    /**
     * 迁移专用插入：明文已存在则跳过，不抛异常（幂等）
     *
     * <p>用 {@code INSERT IGNORE} 而非先查后插：迁移是游标式批量执行，重复跑同一段游标
     * （或中途失败重跑）时靠唯一键 {@code uk_tlc_pat_token} 兜底，避免重复行与并发竞态。</p>
     *
     * @param pat 待写入的令牌记录（token 为旧令牌原样明文）
     * @return 影响行数，0 表示明文已存在被跳过
     */
    @Insert("INSERT IGNORE INTO tlc_pat "
            + "(uid, client_id, name, token, token_prefix, scope, expires_in, expire_time, source, payload) "
            + "VALUES (#{uid}, #{clientId}, #{name}, #{token}, #{tokenPrefix}, #{scope}, "
            + "#{expiresIn}, #{expireTime}, #{source}, #{payload,jdbcType=VARCHAR})")
    int insertIgnore(TlcPat pat);
}
