package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkOperatorStateManual;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 用户手动修改版干员数据 Mapper（ak_operator_state_manual）
 *
 * <p>全量读取走唯一键 {@code uk_uid_operator_state_manual} 的 uid 范围扫描；保存时按提交条数
 * 选择全量读取或 operator_id IN (...) 点查，比较后只写入新增行与属性变化行。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkOperatorStateManualMapper extends BaseMapper<AkOperatorStateManual> {

    /**
     * 全量读取某用户的手动修改版干员数据（按干员编码排序，保证响应顺序稳定）
     *
     * @param uid 用户中心 UID
     * @return 手动版干员记录列表，无数据时返回空列表
     */
    @Select("SELECT * FROM ak_operator_state_manual WHERE uid = #{uid} ORDER BY operator_id")
    List<AkOperatorStateManual> selectByUid(@Param("uid") Long uid);

    /**
     * 点查某用户下指定干员编码的手动版记录（少量提交时使用）
     *
     * @param uid         用户中心 UID
     * @param operatorIds 干员编码集合，调用方保证非空
     * @return 手动版干员记录列表
     */
    @Select("<script>SELECT * FROM ak_operator_state_manual WHERE uid = #{uid} AND operator_id IN "
            + "<foreach collection='operatorIds' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</script>")
    List<AkOperatorStateManual> selectByUidAndIds(@Param("uid") Long uid,
                                            @Param("operatorIds") Collection<String> operatorIds);

    /**
     * 批量插入手动版干员记录（调用方按每批约 100 行切片）
     *
     * @param items 待插入记录，调用方保证非空且数值字段已归一化
     * @return 影响行数
     */
    @Insert("<script>INSERT INTO ak_operator_state_manual "
            + "(uid, operator_id, owned, rarity, level, evolve_phase, main_skill_level, "
            + "skill1, skill2, skill3, equip_x, equip_y, equip_d, equip_a, equip_b, "
            + "potential_rank, updated_at) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.uid}, #{it.operatorId}, #{it.owned}, #{it.rarity}, #{it.level}, #{it.evolvePhase}, "
            + "#{it.mainSkillLevel}, #{it.skill1}, #{it.skill2}, #{it.skill3}, #{it.equipX}, "
            + "#{it.equipY}, #{it.equipD}, #{it.equipA}, #{it.equipB}, #{it.potentialRank}, #{it.updatedAt})"
            + "</foreach></script>")
    int batchInsert(@Param("items") List<AkOperatorStateManual> items);

    /**
     * 按数据库行 ID 更新属性实际变化的手动版记录，并刷新变更时间
     *
     * @param record 记录（需含 id、uid、owned 与全部已归一化数值字段、updatedAt）
     * @return 影响行数
     */
    @Update("UPDATE ak_operator_state_manual SET owned = #{owned}, rarity = #{rarity}, level = #{level}, "
            + "evolve_phase = #{evolvePhase}, main_skill_level = #{mainSkillLevel}, "
            + "skill1 = #{skill1}, skill2 = #{skill2}, skill3 = #{skill3}, "
            + "equip_x = #{equipX}, equip_y = #{equipY}, equip_d = #{equipD}, "
            + "equip_a = #{equipA}, equip_b = #{equipB}, potential_rank = #{potentialRank}, "
            + "updated_at = #{updatedAt} WHERE id = #{id} AND uid = #{uid}")
    int updateManual(AkOperatorStateManual record);
}
