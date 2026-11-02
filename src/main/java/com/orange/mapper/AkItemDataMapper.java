package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkItemData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 游戏账号物品存量 Mapper（ak_item_data）
 *
 * <p>数据按 ak_uid 唯一，写入使用一条 upsert 覆盖两种场景：
 * 首次上报插入新行，再次携带 items 时整体覆盖 JSON 文本并刷新保存时间。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkItemDataMapper extends BaseMapper<AkItemData> {

    /**
     * 保存物品数据：不存在则插入，已存在则整体覆盖 JSON 文本并刷新保存时间
     *
     * @param akUid    游戏账号 UID
     * @param itemData 物品存量 JSON 文本（服务端已校验大小）
     * @param now      本次保存时间，由服务端生成
     * @return 影响行数
     */
    @Insert("INSERT INTO ak_item_data (ak_uid, item_data, create_time, update_time) "
            + "VALUES (#{akUid}, #{itemData}, #{now}, #{now}) "
            + "ON DUPLICATE KEY UPDATE item_data = #{itemData}, update_time = #{now}")
    int upsertItemData(@Param("akUid") String akUid,
                       @Param("itemData") String itemData,
                       @Param("now") LocalDateTime now);

    /**
     * 按游戏账号 UID 查询物品数据
     *
     * @param akUid 游戏账号 UID
     * @return 物品数据行，不存在时返回 null
     */
    @Select("SELECT * FROM ak_item_data WHERE ak_uid = #{akUid}")
    AkItemData selectByAkUid(@Param("akUid") String akUid);
}
