package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkPlayerInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;
import java.util.List;

/**
 * 游戏角色信息 Mapper（ak_player_info）：按 ak_uid 唯一，多用户共享一行
 *
 * @author UserCenter
 */
@Mapper
public interface AkPlayerInfoMapper extends BaseMapper<AkPlayerInfo> {

    /**
     * 按游戏账号 UID 查询角色信息
     *
     * @param akUid 游戏账号 UID
     * @return 角色信息，不存在返回 null
     */
    @Select("SELECT * FROM ak_player_info WHERE ak_uid = #{akUid}")
    AkPlayerInfo selectByAkUid(@Param("akUid") String akUid);

    /**
     * 按游戏账号 UID 批量查询角色信息
     *
     * @param akUids 游戏账号 UID 集合，调用方保证非空
     * @return 角色信息列表
     */
    @Select("<script>SELECT * FROM ak_player_info WHERE ak_uid IN "
            + "<foreach collection='akUids' item='item' open='(' separator=',' close=')'>#{item}</foreach>"
            + "</script>")
    List<AkPlayerInfo> selectByAkUids(@Param("akUids") Collection<String> akUids);

    /**
     * 按行 ID 更新角色元数据（昵称与可空渠道字段），null 表示清空该字段
     *
     * @param id              角色信息行 ID
     * @param akNickName      游戏角色昵称
     * @param channelName     渠道名称，可为 null
     * @param channelMasterId 渠道主 ID，可为 null
     * @param updateTime      服务端生成的变更时间戳（Unix 毫秒）
     * @return 影响行数
     */
    @Update("UPDATE ak_player_info SET ak_nick_name = #{akNickName}, channel_name = #{channelName}, "
            + "channel_master_id = #{channelMasterId}, update_time = #{updateTime} WHERE id = #{id}")
    int updateMetaById(@Param("id") Long id,
                       @Param("akNickName") String akNickName,
                       @Param("channelName") String channelName,
                       @Param("channelMasterId") Integer channelMasterId,
                       @Param("updateTime") Long updateTime);
}
