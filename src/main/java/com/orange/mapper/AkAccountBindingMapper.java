package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AkAccountBinding;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 游戏账号绑定关系 Mapper（ak_account_binding）
 *
 * <p>绑定关系为多对多：一个 uid 可绑定多个 ak_uid，一个 ak_uid 也可被多个 uid 绑定；
 * 绑定归属于用户，不再按 OAuth 客户端拆分。</p>
 *
 * <p>表主键为代理自增 id，业务唯一性由唯一键 {@code uk_ak_owner (ak_uid, owner_uid)} 保证，
 * 故 {@code upsertBinding} 的 {@code ON DUPLICATE KEY UPDATE} 按该唯一键判定冲突。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface AkAccountBindingMapper extends BaseMapper<AkAccountBinding> {

    /**
     * 统计当前用户对某游戏账号的绑定关系
     *
     * @param akUid    游戏账号 UID
     * @param ownerUid 用户中心 UID
     * @return 绑定条数，0 表示未绑定
     */
    @Select("SELECT COUNT(1) FROM ak_account_binding "
            + "WHERE ak_uid = #{akUid} AND owner_uid = #{ownerUid}")
    int countBinding(@Param("akUid") String akUid,
                     @Param("ownerUid") Long ownerUid);

    /**
     * 建立或刷新绑定关系：不存在则插入，已存在则推进“最近导入时间”并刷新账号展示信息
     *
     * <p>用一条 upsert 同时覆盖两种场景：首次导入建立绑定（create_time / update_time 由数据库
     * 默认值写入），再次导入刷新 update_time。相比“先查后插”少一次往返、也没有并发窗口；
     * 同一毫秒内重复刷新因值未变化，影响行数为 0，属正常情况。</p>
     *
     * <p>三个展示字段为可选项：{@code null} 表示本次未上报，插入时落空串、更新时沿用旧值；
     * 显式传空串表示清空。这样旧客户端不上报这些字段也不会抹掉已有账号信息。
     * 它们按绑定行冗余存放，同一 ak_uid 被多个 uid 绑定时各自保留一份。</p>
     *
     * <p>通过 user_info 存在性条件写入，账户注销后到达的延迟请求不再重建绑定。</p>
     *
     * @param akUid           游戏账号 UID
     * @param ownerUid        用户中心 UID
     * @param channelName     渠道名称，null 表示本次未上报
     * @param channelMasterId 渠道主账号标识，null 表示本次未上报
     * @param nickName        游戏内角色昵称，null 表示本次未上报
     * @return 影响行数，0 表示本次未改变任何列
     */
    @Insert("INSERT INTO ak_account_binding "
            + "(ak_uid, owner_uid, channel_name, channel_master_id, nick_name) "
            + "SELECT #{akUid}, #{ownerUid}, "
            + "COALESCE(#{channelName,jdbcType=VARCHAR}, ''), "
            + "COALESCE(#{channelMasterId,jdbcType=VARCHAR}, ''), "
            + "COALESCE(#{nickName,jdbcType=VARCHAR}, '') "
            + "FROM user_info WHERE uid = #{ownerUid} "
            + "ON DUPLICATE KEY UPDATE "
            + "channel_name = COALESCE(#{channelName,jdbcType=VARCHAR}, channel_name), "
            + "channel_master_id = COALESCE(#{channelMasterId,jdbcType=VARCHAR}, channel_master_id), "
            + "nick_name = COALESCE(#{nickName,jdbcType=VARCHAR}, nick_name), "
            + "update_time = NOW(3)")
    int upsertBinding(@Param("akUid") String akUid,
                      @Param("ownerUid") Long ownerUid,
                      @Param("channelName") String channelName,
                      @Param("channelMasterId") String channelMasterId,
                      @Param("nickName") String nickName);

    /**
     * 查询当前用户已绑定的全部游戏账号，按最近导入时间倒序
     *
     * <p>倒序是为了让前端默认展示最新导入的账号数据；同一毫秒内导入的账号再按 ak_uid 排序，
     * 保证展示顺序稳定可复现。</p>
     *
     * @param ownerUid 用户中心 UID
     * @return 绑定关系列表（含创建时间与最近导入时间），最近导入的在前
     */
    @Select("SELECT * FROM ak_account_binding WHERE owner_uid = #{ownerUid} "
            + "ORDER BY update_time DESC, ak_uid ASC")
    List<AkAccountBinding> selectByOwnerOrderByUpdateTime(@Param("ownerUid") Long ownerUid);

    /**
     * 删除当前用户对某游戏账号的绑定关系
     *
     * @param akUid    游戏账号 UID
     * @param ownerUid 用户中心 UID
     * @return 影响行数，0 表示原本未绑定
     */
    @Delete("DELETE FROM ak_account_binding WHERE ak_uid = #{akUid} AND owner_uid = #{ownerUid}")
    int deleteBinding(@Param("akUid") String akUid,
                      @Param("ownerUid") Long ownerUid);
}
