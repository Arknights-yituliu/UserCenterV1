package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.context.UserContext;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.userconfig.UserConfigSaveRequest;
import com.orange.entity.po.UserConfig;
import com.orange.entity.vo.UserConfigVO;
import com.orange.mapper.UserConfigMapper;
import com.orange.service.UserConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户配置服务实现：保存、查询、删除
 *
 * @author UserCenter
 */
@Service
public class UserConfigServiceImpl implements UserConfigService {

    private static final Logger log = LoggerFactory.getLogger(UserConfigServiceImpl.class);

    private final UserConfigMapper userConfigMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param userConfigMapper 用户配置 Mapper
     * @param objectMapper     JSON 序列化器
     */
    public UserConfigServiceImpl(UserConfigMapper userConfigMapper, ObjectMapper objectMapper) {
        this.userConfigMapper = userConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存用户配置：client_id 取自登录上下文（前端不可指定）；传 id 走编辑（校验归属），
     * 不传 id 按（uid+客户端+分类+版本+名称）幂等覆盖，保证同键不重复
     *
     * @param uid     用户 uid
     * @param request 保存参数
     * @return 配置 id
     */
    @Override
    public Long saveConfig(Long uid, UserConfigSaveRequest request) {
        String clientId = requireClientId();
        UserConfig config;
        if (request.getId() != null) {
            // 编辑：校验记录存在且归属当前用户，client_id 以登录上下文为准
            config = getOwnedConfig(uid, request.getId());
            config.setClientId(clientId);
            config.setCategory(request.getCategory());
            config.setVersion(request.getVersion());
            config.setName(request.getName());
        } else {
            // 新增：同（uid+客户端+分类+版本+名称）已存在则复用该记录，保证幂等（version/name 必填，无空值匹配问题）
            config = userConfigMapper.selectOne(Wrappers.<UserConfig>lambdaQuery()
                    .eq(UserConfig::getUid, uid)
                    .eq(UserConfig::getClientId, clientId)
                    .eq(UserConfig::getCategory, request.getCategory())
                    .eq(UserConfig::getVersion, request.getVersion())
                    .eq(UserConfig::getName, request.getName()));
            if (config == null) {
                config = new UserConfig();
                config.setUid(uid);
                config.setClientId(clientId);
                config.setCategory(request.getCategory());
                config.setVersion(request.getVersion());
                config.setName(request.getName());
                config.setDeleteFlag(0);
            }
        }
        config.setSource(request.getSource());
        config.setNote(request.getNote());
        config.setConfig(toConfigString(request.getConfig()));
        if (config.getId() == null) {
            userConfigMapper.insert(config);
        } else {
            userConfigMapper.updateById(config);
        }
        return config.getId();
    }

    /**
     * 获取当前登录上下文中的来源客户端标识，缺失时抛参数错误
     *
     * @return 来源客户端 id
     */
    private String requireClientId() {
        String clientId = UserContext.getClientId();
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少来源客户端标识，请重新登录");
        }
        return clientId;
    }

    /**
     * 查询用户在某客户端某分类下的全部配置，config 由 JSON 字符串转为对象返回
     *
     * @param uid      用户 uid
     * @param clientId 来源客户端标识
     * @param category 配置分类
     * @param version  配置版本（可空，空则返回该分类下全部版本）
     * @param name     配置名称（可空，空则返回该版本下全部命名配置）
     * @return 配置列表（按更新时间倒序）
     */
    @Override
    public List<UserConfigVO> listConfigs(Long uid, String clientId, String category, String version, String name) {
        // client_id 必须来自登录上下文，前端不可指定
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少来源客户端标识，请重新登录");
        }
        List<UserConfig> list = userConfigMapper.selectList(Wrappers.<UserConfig>lambdaQuery()
                .eq(UserConfig::getUid, uid)
                .eq(UserConfig::getClientId, clientId)
                .eq(UserConfig::getCategory, category)
                .eq(StringUtils.hasText(version), UserConfig::getVersion, version)
                .eq(StringUtils.hasText(name), UserConfig::getName, name)
                .orderByDesc(UserConfig::getUpdateTime));
        return list.stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 删除用户配置（逻辑删除：delete_flag 置 1，由 @TableLogic 自动处理）
     *
     * @param uid 用户 uid
     * @param id  配置 id
     */
    @Override
    public void deleteConfig(Long uid, Long id) {
        UserConfig config = getOwnedConfig(uid, id);
        userConfigMapper.deleteById(config.getId());
    }

    /**
     * 查询归属当前用户的配置，不存在或非本人所有则抛异常
     *
     * @param uid 用户 uid
     * @param id  配置 id
     * @return 配置实体
     */
    private UserConfig getOwnedConfig(Long uid, Long id) {
        UserConfig config = userConfigMapper.selectById(id);
        if (config == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置不存在或已删除");
        }
        if (!uid.equals(config.getUid())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该配置");
        }
        return config;
    }

    /**
     * 配置内容转 JSON 字符串存储（对象序列化，字符串保持原样）
     *
     * @param config 配置内容
     * @return JSON 字符串
     */
    private String toConfigString(Object config) {
        if (config instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.warn("配置内容序列化失败：{}", e.getMessage());
            throw new BusinessException(ResultCode.PARAM_ERROR, "配置内容格式错误");
        }
    }

    /**
     * JSON 字符串转对象返回，解析失败时原样返回字符串
     *
     * @param configStr 配置内容（JSON 字符串）
     * @return 配置对象
     */
    private Object parseConfig(String configStr) {
        try {
            return objectMapper.readValue(configStr, Object.class);
        } catch (IOException e) {
            log.warn("配置内容解析失败，原样返回：{}", e.getMessage());
            return configStr;
        }
    }

    /**
     * 实体转视图对象
     *
     * @param config 配置实体
     * @return 配置视图
     */
    private UserConfigVO toVO(UserConfig config) {
        UserConfigVO vo = new UserConfigVO();
        vo.setId(config.getId());
        vo.setClientId(config.getClientId());
        vo.setCategory(config.getCategory());
        vo.setVersion(config.getVersion());
        vo.setName(config.getName());
        vo.setSource(config.getSource());
        vo.setNote(config.getNote());
        vo.setConfig(parseConfig(config.getConfig()));
        vo.setCreateTime(config.getCreateTime());
        vo.setUpdateTime(config.getUpdateTime());
        return vo;
    }
}
