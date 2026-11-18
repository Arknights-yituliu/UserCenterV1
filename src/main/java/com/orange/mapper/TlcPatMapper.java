package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.TlcPat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 个人访问令牌 Mapper（tlc_pat）
 *
 * <p>常规查询与删除直接用 {@link BaseMapper} 的条件构造器；这里只保留需要数据库语义保证的
 * 批量插入：迁移时按明文唯一键「已存在则跳过」写入（幂等）。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface TlcPatMapper extends BaseMapper<TlcPat> {

    /**
     * 迁移专用批量插入：明文已存在则跳过，不抛异常（幂等）
     *
     * <p>用 {@code INSERT IGNORE} 而非先查后插：迁移是游标式批量执行，重复跑同一段游标
     * （或中途失败重跑）时靠唯一键 {@code uk_tlc_pat_token} 兜底，避免重复行与并发竞态。
     * 单条语句写入整批，替代逐行往返；{@code IGNORE} 同时兜住旧表可能存在的重复明文
     * （{@code oauth_custom_token} 没有 token 唯一键）。</p>
     *
     * <p>{@code create_time} 显式搬迁，不走列默认值——否则历史令牌会全部记为迁移当天，
     * 而列表正是按 {@code (uid, create_time)} 倒序。</p>
     *
     * @param list 待写入的令牌记录集合（token 为旧令牌原样明文）
     * @return 实际插入行数（已存在的明文不计入）
     */
    @Insert("<script>"
            + "INSERT IGNORE INTO tlc_pat "
            + "(uid, client_id, name, token, token_prefix, scope, expires_in, expire_time, payload, create_time) "
            + "VALUES "
            + "<foreach collection=\"list\" item=\"item\" separator=\",\">"
            + "(#{item.uid}, #{item.clientId}, #{item.name}, #{item.token}, #{item.tokenPrefix}, "
            + "#{item.scope}, #{item.expiresIn}, #{item.expireTime}, "
            + "#{item.payload,jdbcType=VARCHAR}, #{item.createTime})"
            + "</foreach>"
            + "</script>")
    int insertIgnoreBatch(@Param("list") List<TlcPat> list);
}
