package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfCharacterPoolRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 干员寻访记录 Mapper（ef_character_pool_record）
 *
 * <p>沿用终末地一图流原有表：批量写入使用 ON DUPLICATE KEY UPDATE，由表上的唯一约束去重，
 * 冲突行仅在新值非空时覆盖旧值，避免上游字段降级把已有数据刷空。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface EfCharacterPoolRecordMapper extends BaseMapper<EfCharacterPoolRecord> {

    /**
     * 批量写入干员寻访记录，唯一键冲突时按「只补不毁」更新已有行
     *
     * <p>新值为 null 或空串时保留旧值，因此历史遗留的空 char_id 行会被后续导入
     * 补成 gift_ 伪编码；主键与 role_id、seq_id 等定位字段不参与更新。</p>
     *
     * @param items 待写入记录，调用方保证非空且已生成主键
     * @return 影响行数（新增计 1、更新计 2、无变化计 0）
     */
    @Insert("<script>INSERT INTO ef_character_pool_record "
            + "(id, role_id, pool_id, pool_name, char_id, char_name, rarity, is_free, is_new, "
            + "gacha_ts, seq_id, lang, pool_type, server_id) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.id}, #{it.roleId}, #{it.poolId}, #{it.poolName}, #{it.charId}, #{it.charName}, "
            + "#{it.rarity}, #{it.isFree}, #{it.isNew}, #{it.gachaTs}, #{it.seqId}, #{it.lang}, "
            + "#{it.poolType}, #{it.serverId})"
            + "</foreach>"
            + " ON DUPLICATE KEY UPDATE "
            + "char_id = IF(VALUES(char_id) IS NULL OR VALUES(char_id) = '', char_id, VALUES(char_id)), "
            + "char_name = IF(VALUES(char_name) IS NULL OR VALUES(char_name) = '', char_name, VALUES(char_name)), "
            + "rarity = IFNULL(VALUES(rarity), rarity), "
            + "gacha_ts = IF(VALUES(gacha_ts) IS NULL OR VALUES(gacha_ts) = '', gacha_ts, VALUES(gacha_ts))"
            + "</script>")
    int batchUpsert(@Param("items") List<EfCharacterPoolRecord> items);

    /**
     * 查询指定角色、指定卡池类型下已保存记录的最大 seq_id（按数字比较）
     *
     * @param roleId   终末地游戏内角色 UID
     * @param poolType 卡池类型
     * @return 最大 seq_id；该卡池类型无历史记录时返回 null
     */
    @Select("SELECT MAX(CAST(seq_id AS UNSIGNED)) FROM ef_character_pool_record "
            + "WHERE role_id = #{roleId} AND pool_type = #{poolType}")
    Integer getMaxSeqIdNumberByPoolType(@Param("roleId") String roleId, @Param("poolType") String poolType);
}
