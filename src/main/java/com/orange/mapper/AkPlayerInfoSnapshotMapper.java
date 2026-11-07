package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkPlayerInfoSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 森空岛 player/info 数据镜像 Mapper（ak_player_info_snapshot）
 *
 * <p>数据按 ak_uid 唯一，写入使用一条 upsert 覆盖两种场景：首次上报插入新行，
 * 再次上报时整段覆盖文本。时间列由数据库默认值与 ON UPDATE 维护，Java 侧不传。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkPlayerInfoSnapshotMapper extends BaseMapper<AkPlayerInfoSnapshot> {

    /**
     * 保存 player/info 文本：不存在则插入，已存在则整段覆盖
     *
     * <p>文本与库中完全一致时 MySQL 视为无变更，不改写行，{@code update_time} 也不会刷新。</p>
     *
     * @param akUid      游戏账号 UID
     * @param playerData 前端上报的文本原文
     * @return 影响行数
     */
    @Insert("INSERT INTO ak_player_info_snapshot (ak_uid, player_data) "
            + "VALUES (#{akUid}, #{playerData}) "
            + "ON DUPLICATE KEY UPDATE player_data = #{playerData}")
    int upsertPlayerData(@Param("akUid") String akUid,
                         @Param("playerData") String playerData);

    /**
     * 查询 player_data 文本
     *
     * <p>只查文本列，避免把整行宽字段一并取出。</p>
     *
     * @param akUid 游戏账号 UID
     * @return 上报文本，不存在时返回 null
     */
    @Select("SELECT player_data FROM ak_player_info_snapshot WHERE ak_uid = #{akUid}")
    String selectPlayerData(@Param("akUid") String akUid);
}
