package com.orange.service;

import com.orange.entity.dto.userconfig.UserConfigSaveRequest;
import com.orange.entity.vo.UserConfigVO;

import java.util.List;

/**
 * 用户配置服务接口：保存、查询、删除
 *
 * @author UserCenter
 */
public interface UserConfigService {

    /**
     * 保存用户配置（同用户+项目+分类+版本已存在则覆盖更新，否则新增）
     *
     * @param uid     用户 uid
     * @param request 保存参数
     * @return 配置 id
     */
    Long saveConfig(Long uid, UserConfigSaveRequest request);

    /**
     * 查询用户在某客户端某分类下的全部配置（clientId 取自登录上下文，前端不可指定）
     *
     * @param uid      用户 uid
     * @param clientId 来源客户端标识（取自登录上下文）
     * @param category 配置分类
     * @param version  配置版本（可空，空则返回该分类下全部版本）
     * @param name     配置名称（可空，空则返回该版本下全部命名配置）
     * @return 配置列表
     */
    List<UserConfigVO> listConfigs(Long uid, String clientId, String category, String version, String name);

    /**
     * 删除用户配置（逻辑删除）
     *
     * @param uid 用户 uid
     * @param id  配置 id
     */
    void deleteConfig(Long uid, Long id);
}
