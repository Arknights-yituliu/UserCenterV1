package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfCharacterPoolRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 干员寻访记录 Mapper（character_pool_record）
 *
 * <p>沿用终末地一图流原有表：批量写入使用 INSERT IGNORE，由表上的唯一约束去重，
 * 重复记录不报错也不覆盖已有数据。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface EfCharacterPoolRecordMapper extends BaseMapper<EfCharacterPoolRecord> {

    /**
     * 批量插入干员寻访记录（重复记录由数据库唯一约束自动忽略）
     *
     * @param items 待插入记录，调用方保证非空且已生成主键
     * @return 实际插入行数
     */
    @Insert("<script>INSERT IGNORE INTO character_pool_record "
            + "(id, role_id, pool_id, pool_name, char_id, char_name, rarity, is_free, is_new, "
            + "gacha_ts, seq_id, lang, pool_type, server_id) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.id}, #{it.roleId}, #{it.poolId}, #{it.poolName}, #{it.charId}, #{it.charName}, "
            + "#{it.rarity}, #{it.isFree}, #{it.isNew}, #{it.gachaTs}, #{it.seqId}, #{it.lang}, "
            + "#{it.poolType}, #{it.serverId})"
            + "</foreach></script>")
    int batchInsert(@Param("items") List<EfCharacterPoolRecord> items);

    /**
     * 查询指定角色、指定卡池类型下已保存记录的最大 seq_id（按数字比较）
     *
     * @param roleId   终末地游戏内角色 UID
     * @param poolType 卡池类型
     * @return 最大 seq_id；该卡池类型无历史记录时返回 null
     */
    @Select("SELECT MAX(CAST(seq_id AS UNSIGNED)) FROM character_pool_record "
            + "WHERE role_id = #{roleId} AND pool_type = #{poolType}")
    Integer getMaxSeqIdNumberByPoolType(@Param("roleId") String roleId, @Param("poolType") String poolType);
}
