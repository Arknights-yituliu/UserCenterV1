package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.EfAccountBinding;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
}
