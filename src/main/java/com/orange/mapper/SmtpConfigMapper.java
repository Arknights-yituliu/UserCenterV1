package com.orange.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.entity.po.SmtpConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * SMTP 邮件渠道配置 Mapper
 *
 * @author UserCenter
 */
@Mapper
public interface SmtpConfigMapper extends BaseMapper<SmtpConfig> {
}
