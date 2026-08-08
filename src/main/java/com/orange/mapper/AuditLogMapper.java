package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.AuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
