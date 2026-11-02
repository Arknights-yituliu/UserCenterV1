package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfWeaponPoolRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 武器寻访记录 Mapper（weapon_pool_record）
 *
 * <p>沿用终末地一图流原有表：批量写入使用 INSERT IGNORE，由表上的唯一约束去重。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface EfWeaponPoolRecordMapper extends BaseMapper<EfWeaponPoolRecord> {

    /**
     * 批量插入武器寻访记录（重复记录由数据库唯一约束自动忽略）
     *
     * @param items 待插入记录，调用方保证非空且已生成主键
     * @return 实际插入行数
     */
    @Insert("<script>INSERT IGNORE INTO weapon_pool_record "
            + "(id, role_id, pool_id, pool_name, weapon_id, weapon_name, weapon_type, rarity, is_new, "
            + "gacha_ts, seq_id, lang, server_id) VALUES "
            + "<foreach collection='items' item='it' separator=','>"
            + "(#{it.id}, #{it.roleId}, #{it.poolId}, #{it.poolName}, #{it.weaponId}, #{it.weaponName}, "
            + "#{it.weaponType}, #{it.rarity}, #{it.isNew}, #{it.gachaTs}, #{it.seqId}, #{it.lang}, #{it.serverId})"
            + "</foreach></script>")
    int batchInsert(@Param("items") List<EfWeaponPoolRecord> items);

    /**
     * 查询指定角色已保存武器寻访记录的最大 seq_id（按数字比较）
     *
     * @param roleId 终末地游戏内角色 UID
     * @return 最大 seq_id；无历史记录时返回 null
     */
    @Select("SELECT MAX(CAST(seq_id AS UNSIGNED)) FROM weapon_pool_record WHERE role_id = #{roleId}")
    Integer getMaxSeqIdNumber(@Param("roleId") String roleId);
}
