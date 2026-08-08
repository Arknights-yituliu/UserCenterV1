package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserExternalBinding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 第三方绑定 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface UserExternalBindingMapper extends BaseMapper<UserExternalBinding> {
}
