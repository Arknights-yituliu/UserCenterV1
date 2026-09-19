package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkPlayerInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
