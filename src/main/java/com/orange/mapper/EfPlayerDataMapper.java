package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfPlayerData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 终末地玩家数据 Mapper（ef_player_data）
 *
 * <p>数据按 role_id + server_id 唯一，写入使用一条 upsert 覆盖两种场景：
 * 首次上传插入新行，再次上传覆盖 JSON 文本并刷新保存时间。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface EfPlayerDataMapper extends BaseMapper<EfPlayerData> {

    /**
     * 保存玩家数据：不存在则插入，已存在则覆盖 JSON 文本并刷新保存时间
     *
     * @param roleId     终末地游戏内角色 UID
     * @param serverId   服务器 ID（已归一化，非 null）
     * @param playerData 玩家数据 JSON 文本
     * @param updatedAt  本次保存时间，由服务端生成
     * @return 影响行数
     */
    @Insert("INSERT INTO ef_player_data (role_id, server_id, player_data, create_time, update_time) "
            + "VALUES (#{roleId}, #{serverId}, #{playerData}, #{updatedAt}, #{updatedAt}) "
            + "ON DUPLICATE KEY UPDATE player_data = #{playerData}, update_time = #{updatedAt}")
    int upsertPlayerData(@Param("roleId") String roleId,
                         @Param("serverId") String serverId,
                         @Param("playerData") String playerData,
                         @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 按角色定位查询玩家数据
     *
     * @param roleId   终末地游戏内角色 UID
     * @param serverId 服务器 ID（已归一化，非 null）
     * @return 玩家数据行，不存在时返回 null
     */
    @Select("SELECT * FROM ef_player_data WHERE role_id = #{roleId} AND server_id = #{serverId}")
    EfPlayerData selectByRoleAndServer(@Param("roleId") String roleId,
                                       @Param("serverId") String serverId);

    /**
     * 查询当前用户已绑定角色中最近保存的玩家数据，跳过仅有寻访记录或数据已清理的角色
     *
     * <p>按玩家数据保存时间排序，避免寻访记录导入刷新绑定时间影响默认选择；
     * 保存时间相同时按角色与服务器排序，保证结果稳定。</p>
     *
     * @param ownerUid 用户中心 UID，排除匿名导入档案
     * @return 最新玩家数据，无可用数据时返回 null
     */
    @Select("SELECT d.* FROM ef_player_data d "
            + "INNER JOIN ef_account_binding b ON b.role_id = d.role_id AND b.server_id = d.server_id "
            + "WHERE b.owner_uid = #{ownerUid} AND b.owner_uid > 0 "
            + "ORDER BY d.update_time DESC, d.role_id ASC, d.server_id ASC LIMIT 1")
    EfPlayerData selectLatestByOwner(@Param("ownerUid") Long ownerUid);
}
