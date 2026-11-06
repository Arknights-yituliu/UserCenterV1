package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkDefaultSource;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户默认数据来源偏好 Mapper（ak_default_source）
 *
 * <p>偏好按用户唯一（uk_default_source_uid），设置默认用一条 upsert 同时覆盖
 * 「首次设置」与「修改设置」，避免先查后写的并发窗口。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkDefaultSourceMapper extends BaseMapper<AkDefaultSource> {

    /**
     * 查询用户的默认数据来源偏好
     *
     * @param uid 用户中心 UID
     * @return 偏好记录；用户未设置默认时返回 null
     */
    @Select("SELECT * FROM ak_default_source WHERE uid = #{uid}")
    AkDefaultSource selectByUid(@Param("uid") Long uid);

    /**
     * 设置默认数据来源：不存在则插入，已存在则覆盖来源与归属键
     *
     * @param uid    用户中心 UID
     * @param source 数据来源：skland 或 manual
     * @param akUid  对应来源的归属键
     * @return 影响行数
     */
    @Insert("INSERT INTO ak_default_source (uid, source, ak_uid) VALUES (#{uid}, #{source}, #{akUid}) "
            + "ON DUPLICATE KEY UPDATE source = #{source}, ak_uid = #{akUid}, update_time = NOW(3)")
    int upsert(@Param("uid") Long uid,
               @Param("source") String source,
               @Param("akUid") String akUid);

    /**
     * 删除指向指定条目的默认偏好：仅当用户默认来源恰好是该条目时才会命中
     *
     * @param uid    用户中心 UID
     * @param source 数据来源
     * @param akUid  数据条目 UID
     * @return 影响行数，0 表示默认来源不是该条目
     */
    @Delete("DELETE FROM ak_default_source WHERE uid = #{uid} AND source = #{source} AND ak_uid = #{akUid}")
    int deleteByTarget(@Param("uid") Long uid,
                       @Param("source") String source,
                       @Param("akUid") String akUid);
}
