package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfPlayerData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
