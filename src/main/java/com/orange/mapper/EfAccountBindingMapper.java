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
     * 建立或刷新绑定关系：不存在则插入，已存在则只推进最近导入时间
     *
     * @param roleId   终末地游戏内角色 UID
     * @param serverId 服务器 ID（已归一化，非 null）
     * @param ownerUid 用户中心 UID
     * @return 影响行数
     */
    @Insert("INSERT INTO ef_account_binding (role_id, server_id, owner_uid) "
            + "VALUES (#{roleId}, #{serverId}, #{ownerUid}) "
            + "ON DUPLICATE KEY UPDATE update_time = NOW(3)")
    int upsertBinding(@Param("roleId") String roleId,
                      @Param("serverId") String serverId,
                      @Param("ownerUid") Long ownerUid);

    /**
     * 查询当前用户已绑定的全部终末地角色，按最近导入时间倒序
     *
     * <p>倒序是为了让前端默认展示最新导入的角色数据；同一毫秒内导入的角色再按
     * role_id、server_id 排序，保证展示顺序稳定可复现。</p>
     *
     * @param ownerUid 用户中心 UID
     * @return 绑定关系列表（含创建时间与最近导入时间），最近导入的在前
     */
    @Select("SELECT * FROM ef_account_binding WHERE owner_uid = #{ownerUid} "
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
}
