package com.orange.mapper;

import com.orange.entity.po.AkAccountBinding;
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
 * @author UserCenter
 */
@Mapper
public interface AkAccountBindingMapper {

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
     * 建立或刷新绑定关系：不存在则插入，已存在则只推进“最近导入时间”
     *
     * <p>用一条 upsert 同时覆盖两种场景：首次导入建立绑定（create_time / update_time 由数据库
     * 默认值写入），再次导入刷新 update_time。相比“先查后插”少一次往返、也没有并发窗口；
     * 同一毫秒内重复刷新因值未变化，影响行数为 0，属正常情况。</p>
     *
     * @param akUid    游戏账号 UID
     * @param ownerUid 用户中心 UID
     * @return 影响行数，0 表示本次未改变任何列
     */
    @Insert("INSERT INTO ak_account_binding (ak_uid, owner_uid) "
            + "VALUES (#{akUid}, #{ownerUid}) "
            + "ON DUPLICATE KEY UPDATE update_time = NOW(3)")
    int upsertBinding(@Param("akUid") String akUid,
                      @Param("ownerUid") Long ownerUid);

    /**
     * 查询当前用户已绑定的全部游戏账号，按最近导入时间倒序
     *
     * <p>倒序是为了让前端默认展示最新导入的账号数据；同一毫秒内导入的账号再按 ak_uid 排序，
     * 保证展示顺序稳定可复现。</p>
     *
     * @param ownerUid 用户中心 UID
     * @return 绑定关系列表（含创建时间与最近导入时间），最近导入的在前
     */
    @Select("SELECT ak_uid AS akUid, create_time AS createTime, update_time AS updateTime "
            + "FROM ak_account_binding WHERE owner_uid = #{ownerUid} "
            + "ORDER BY update_time DESC, ak_uid ASC")
    List<AkAccountBinding> selectByOwnerOrderByUpdateTime(@Param("ownerUid") Long ownerUid);
}
