package com.orange.mapper;

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
     * 新增一条绑定关系，并发重复插入由组合主键兜底
     *
     * @param akUid    游戏账号 UID
     * @param ownerUid 用户中心 UID
     * @return 影响行数
     */
    @Insert("INSERT INTO ak_account_binding (ak_uid, owner_uid) "
            + "VALUES (#{akUid}, #{ownerUid})")
    int insertBinding(@Param("akUid") String akUid,
                      @Param("ownerUid") Long ownerUid);

    /**
     * 查询当前用户已绑定的全部游戏账号 UID
     *
     * @param ownerUid 用户中心 UID
     * @return 游戏账号 UID 列表
     */
    @Select("SELECT ak_uid FROM ak_account_binding "
            + "WHERE owner_uid = #{ownerUid} ORDER BY ak_uid")
    List<String> selectAkUidsByOwner(@Param("ownerUid") Long ownerUid);
}
