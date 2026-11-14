package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.UserNicknameAudit;
import org.apache.ibatis.annotations.Mapper;

/** 昵称审核结果 Mapper，主键冲突由调用方跳过，不执行覆盖更新。 */
@Mapper
public interface UserNicknameAuditMapper extends BaseMapper<UserNicknameAudit> {
}
