package com.orange.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 注销审计与有限删除；其余业务数据全部保留。 */
@Mapper
public interface AccountDeletionMapper {

    /** 保存当前锁定用户的资料快照；密码哈希和令牌不进入审计表。 */
    @Insert("INSERT INTO user_deletion_audit "
            + "(uid, email, user_name, avatar, nickname, status, ip, register_time, last_login_time, "
            + "deleted_at, auth_type, client_id) "
            + "SELECT uid, email, user_name, avatar, nickname, status, ip, register_time, last_login_time, "
            + "CURRENT_TIMESTAMP(3), #{authType}, #{clientId,jdbcType=VARCHAR} "
            + "FROM user_info WHERE uid = #{uid}")
    int archiveUser(@Param("uid") Long uid, @Param("authType") String authType,
                    @Param("clientId") String clientId);

    @Delete("DELETE FROM ak_account_binding WHERE owner_uid = #{uid}")
    int deleteAkBindings(@Param("uid") Long uid);

    @Delete("DELETE FROM ef_account_binding WHERE owner_uid = #{uid}")
    int deleteEfBindings(@Param("uid") Long uid);
}
