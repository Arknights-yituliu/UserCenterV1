package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkOperatorState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 干员养成状态 Mapper（ak_operator_state）
 *
 * <p>全量读取走 uk_ak_operator 的 ak_uid 范围扫描；保存时按提交条数选择全量读取或
 * operator_id IN (...) 点查，比较后只写入新增行与属性变化行。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkOperatorStateMapper extends BaseMapper<AkOperatorState> {

    /**
     * 全量读取某游戏账号的干员数据（按干员编码排序，保证响应顺序稳定）
     *
     * @param akUid 游戏账号 UID
     * @return 干员记录列表，无数据时返回空列表
     */
    @Select("SELECT * FROM ak_operator_state WHERE ak_uid = #{akUid} ORDER BY operator_id")
    List<AkOperatorState> selectByAkUid(@Param("akUid") String akUid);

    /**
     * 点查某游戏账号下指定干员编码的记录（少量上传时使用）
     *
     * @param akUid       游戏账号 UID
     * @param operatorIds 干员编码集合，调用方保证非空
     * @return 干员记录列表
     */
    @Select("<script>SELECT * FROM ak_operator_state WHERE ak_uid = #{akUid} AND operator_id IN "
            + "<foreach collection='operatorIds' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</script>")
    List<AkOperatorState> selectByAkUidAndIds(@Param("akUid") String akUid,
                                             @Param("operatorIds") Collection<String> operatorIds);

    /**
     * 批量插入干员记录（调用方按每批约 100 行切片）
     *
     * @param items 待插入记录，调用方保证非空且数值字段已归一化
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO ak_operator_state "
            + "(ak_uid, operator_id, rarity, level, evolve_phase, main_skill_level, "
            + "skill1, skill2, skill3, equip_x, equip_y, equip_d, equip_a, equip_b, "
            + "potential_rank, updated_at) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.akUid}, #{it.operatorId}, #{it.rarity}, #{it.level}, #{it.evolvePhase}, "
            + "#{it.mainSkillLevel}, #{it.skill1}, #{it.skill2}, #{it.skill3}, #{it.equipX}, "
            + "#{it.equipY}, #{it.equipD}, #{it.equipA}, #{it.equipB}, #{it.potentialRank}, #{it.updatedAt})"
            + "</foreach></script>")
    int batchInsert(@Param("items") List<AkOperatorState> items);

    /**
     * 按主键升序分片扫描全表，供干员数据统计使用
     *
     * <p>读取统计所需的 9 个取值列、主键，以及账号归集用的 ak_uid 与 updated_at：调用方在扫描
     * 过程中顺带归集每个账号的最近变更时间，就不必再发一次「MAX(updated_at) GROUP BY ak_uid」，
     * 后者虽然只返回账号数的行数，却要把数千万行重新读一遍，缓冲池装不下整表时全是磁盘 I/O。
     * 调用方以本批最后一条的 id 作为下一批起点，走主键范围扫描而非 LIMIT OFFSET，避免深分页。</p>
     *
     * @param lastId 上一批最后一条记录的 id，首页传 0
     * @param size   本批最大条数，调用方按内存情况控制
     * @return 按 id 升序排列的记录，无数据时返回空列表
     */
    @Select("SELECT id, ak_uid, operator_id, evolve_phase, skill1, skill2, skill3, "
            + "equip_x, equip_y, equip_d, equip_a, equip_b, updated_at "
            + "FROM ak_operator_state WHERE id > #{lastId} ORDER BY id LIMIT #{size}")
    List<AkOperatorState> selectStatisticsBatch(@Param("lastId") long lastId, @Param("size") int size);

    /**
     * 按数据库行 ID 更新属性实际变化的干员记录，并刷新变更时间
     *
     * @param record 记录（需含 id、akUid 与全部已归一化数值字段、updatedAt）
     * @return 影响行数
     */
    @Update("UPDATE ak_operator_state SET rarity = #{rarity}, level = #{level}, "
            + "evolve_phase = #{evolvePhase}, main_skill_level = #{mainSkillLevel}, "
            + "skill1 = #{skill1}, skill2 = #{skill2}, skill3 = #{skill3}, "
            + "equip_x = #{equipX}, equip_y = #{equipY}, equip_d = #{equipD}, "
            + "equip_a = #{equipA}, equip_b = #{equipB}, potential_rank = #{potentialRank}, "
            + "updated_at = #{updatedAt} WHERE id = #{id} AND ak_uid = #{akUid}")
    int updateState(AkOperatorState record);
}
