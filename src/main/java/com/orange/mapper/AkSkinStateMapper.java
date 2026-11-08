package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkSkinState;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 游戏账号皮肤 Mapper（ak_skin_state）
 *
 * <p>全量读取走 uk_ak_skin 的 ak_uid 范围扫描；保存时先按 ak_uid 取出全部已存行，
 * 在内存中与本次提交比对，只写新增行与获得时间变化的行，并删除本次未提交的旧行
 * （森空岛返回的 skins 是账号完整集合，全量对齐能让数据自愈）。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkSkinStateMapper extends BaseMapper<AkSkinState> {

    /**
     * 全量读取某游戏账号已拥有的皮肤（按皮肤编码排序，保证响应顺序稳定）
     *
     * @param akUid 游戏账号 UID
     * @return 皮肤记录列表，无数据时返回空列表
     */
    @Select("SELECT * FROM ak_skin_state WHERE ak_uid = #{akUid} ORDER BY skin_id")
    List<AkSkinState> selectByAkUid(@Param("akUid") String akUid);

    /**
     * 按主键键集分页读取皮肤记录，供离线统计任务全表扫描
     *
     * <p>用 {@code id > lastId} 而不是 OFFSET：深分页时 OFFSET 需要先扫过并丢弃前 N 行，
     * 而键集分页每批都从主键索引的定位点直接开始。只取统计需要的三列，{@code gain_ts}
     * 与 {@code updated_at} 不参与统计，不读进结果集。</p>
     *
     * @param lastId 上一批最后一行的主键，首批传 0
     * @param limit  本批最大行数
     * @return 按主键升序排列的皮肤记录，无更多数据时返回空列表
     */
    @Select("SELECT id, ak_uid, skin_id FROM ak_skin_state WHERE id > #{lastId} "
            + "ORDER BY id LIMIT #{limit}")
    List<AkSkinState> selectStatisticsBatch(@Param("lastId") long lastId, @Param("limit") int limit);

    /**
     * 批量插入皮肤记录（调用方按每批约 100 行切片）
     *
     * @param items 待插入记录，调用方保证非空
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO ak_skin_state (ak_uid, skin_id, gain_ts, updated_at) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.akUid}, #{it.skinId}, #{it.gainTs}, #{it.updatedAt})"
            + "</foreach></script>")
    int batchInsert(@Param("items") List<AkSkinState> items);

    /**
     * 按数据库行 ID 更新获得时间与变更时间
     *
     * @param record 记录（需含 id、akUid、gainTs 与 updatedAt）
     * @return 影响行数
     */
    @Update("UPDATE ak_skin_state SET gain_ts = #{gainTs}, updated_at = #{updatedAt} "
            + "WHERE id = #{id} AND ak_uid = #{akUid}")
    int updateGainTs(AkSkinState record);

    /**
     * 删除某游戏账号下不在本次提交集合中的皮肤记录
     *
     * <p>仅在本次确实上报了非空皮肤数组时调用，调用方保证 {@code skinIds} 非空，
     * 避免拼出 {@code NOT IN ()} 的清空全表语句。</p>
     *
     * @param akUid   游戏账号 UID
     * @param skinIds 本次提交的皮肤编码集合，调用方保证非空
     * @return 影响行数
     */
    @Delete("<script>DELETE FROM ak_skin_state WHERE ak_uid = #{akUid} AND skin_id NOT IN "
            + "<foreach collection='skinIds' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</script>")
    int deleteByAkUidAndNotIn(@Param("akUid") String akUid,
                              @Param("skinIds") Collection<String> skinIds);
}
