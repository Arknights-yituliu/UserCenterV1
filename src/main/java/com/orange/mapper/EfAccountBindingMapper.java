package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfAccountBinding;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 终末地角色绑定关系 Mapper（ef_account_binding）
 *
 * <p>业务唯一性由 {@code uk_ef_owner (role_id, server_id, owner_uid)} 保证，
 * 首次上传时建立绑定，之后每次上传只刷新最近导入时间。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface EfAccountBindingMapper extends BaseMapper<EfAccountBinding> {

    /**
     * 建立或刷新玩家数据绑定：写入绑定关系并标记该角色已导入过玩家数据（角色详情）
     *
     * @param roleId   终末地游戏内角色 UID
     * @param serverId 服务器 ID（已归一化，非 null）
     * @param ownerUid 用户中心 UID
     * @return 影响行数
     */
    @Insert("INSERT INTO ef_account_binding (role_id, server_id, owner_uid, player_data_imported) "
            + "VALUES (#{roleId}, #{serverId}, #{ownerUid}, 1) "
            + "ON DUPLICATE KEY UPDATE player_data_imported = 1, update_time = NOW(3)")
    int upsertBinding(@Param("roleId") String roleId,
                      @Param("serverId") String serverId,
                      @Param("ownerUid") Long ownerUid);

    /**
     * 建立或刷新寻访记录绑定：写入角色档案并标记该角色已导入过寻访记录
     *
     * <p>寻访记录不区分服务器，server_id 固定为空串；同一角色已有绑定时不改归属，
     * 只刷新角色档案与最近导入时间。</p>
     *
     * @param roleId   终末地游戏内角色 UID
     * @param ownerUid 用户中心 UID
     * @param hgUid    鹰角通行证 UID
     * @param nickName 玩家昵称
     * @return 影响行数
     */
    @Insert("INSERT INTO ef_account_binding (role_id, server_id, owner_uid, hg_uid, nick_name, pool_record_imported) "
            + "VALUES (#{roleId}, '', #{ownerUid}, #{hgUid}, #{nickName}, 1) "
            + "ON DUPLICATE KEY UPDATE hg_uid = #{hgUid}, nick_name = #{nickName}, "
            + "pool_record_imported = 1, update_time = NOW(3)")
    int upsertPoolRecordBinding(@Param("roleId") String roleId,
                                @Param("ownerUid") Long ownerUid,
                                @Param("hgUid") String hgUid,
                                @Param("nickName") String nickName);

    /**
     * 查询当前用户已导入过数据的终末地角色，按最近导入时间倒序
     *
     * <p>倒序是为了让前端默认展示最新导入的角色数据；同一毫秒内导入的角色再按
     * role_id、server_id 排序，保证展示顺序稳定可复现。
     * 显式排除 owner_uid = 0 的匿名导入档案，匿名历史只有在该角色被鉴权导入、
     * 建立真实归属后才会出现在列表中。</p>
     *
     * @param ownerUid 用户中心 UID
     * @return 绑定关系列表（含角色档案与最近导入时间），最近导入的在前
     */
    @Select("SELECT * FROM ef_account_binding WHERE owner_uid = #{ownerUid} AND owner_uid > 0 "
            + "ORDER BY update_time DESC, role_id ASC, server_id ASC")
    List<EfAccountBinding> selectByOwnerOrderByUpdateTime(@Param("ownerUid") Long ownerUid);

    /**
     * 统计当前用户对某个角色的绑定关系
     *
     * @param roleId   终末地游戏内角色 UID
     * @param serverId 服务器 ID（已归一化，非 null）
     * @param ownerUid 用户中心 UID
     * @return 绑定条数，0 表示未绑定
     */
    @Select("SELECT COUNT(1) FROM ef_account_binding "
            + "WHERE role_id = #{roleId} AND server_id = #{serverId} AND owner_uid = #{ownerUid}")
    int countBinding(@Param("roleId") String roleId,
                     @Param("serverId") String serverId,
                     @Param("ownerUid") Long ownerUid);

    /**
     * 统计当前用户对某个角色的绑定关系（不限服务器）
     *
     * <p>寻访记录只按 roleId 存储、不区分服务器，因此归属校验只需角色维度。</p>
     *
     * @param roleId   终末地游戏内角色 UID
     * @param ownerUid 用户中心 UID
     * @return 绑定条数，0 表示未绑定
     */
    @Select("SELECT COUNT(1) FROM ef_account_binding "
            + "WHERE role_id = #{roleId} AND owner_uid = #{ownerUid}")
    int countBindingByRoleId(@Param("roleId") String roleId,
                             @Param("ownerUid") Long ownerUid);
}
