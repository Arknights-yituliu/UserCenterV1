package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.TlcPat;
import org.apache.ibatis.annotations.Mapper;

/**
 * 个人访问令牌 Mapper（tlc_pat）
 *
 * <p>签发、查询与删除使用 {@link BaseMapper} 提供的方法。</p>
 *
 * @author UserCenter
 */
@Mapper
public interface TlcPatMapper extends BaseMapper<TlcPat> {
}
