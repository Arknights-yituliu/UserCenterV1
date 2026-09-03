package com.orange.service;

import com.orange.entity.dto.userconfig.UserConfigSaveRequest;
import com.orange.entity.vo.UserConfigQuotaVO;
import com.orange.entity.vo.UserConfigSaveVO;
import com.orange.entity.vo.UserConfigVO;

import java.util.List;

/**
 * 用户配置服务接口：保存、查询、删除
 *
 * @author UserCenter
 */
public interface UserConfigService {

    /**
     * 通过 CAS 保存用户配置。
     *
     * @param uid     用户 uid
     * @param request 保存参数
     * @return 配置 id 和新内容 hash
     */
    UserConfigSaveVO saveConfig(Long uid, UserConfigSaveRequest request);

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
     * 查询用户配置配额使用情况。
     *
     * @param uid 用户 uid
     * @return 配额使用情况
     */
    UserConfigQuotaVO getQuota(Long uid);

    /**
     * 物理删除用户配置并释放配额。
     *
     * @param uid 用户 uid
     * @param id  配置 id
     */
    void deleteConfig(Long uid, Long id);

    /**
     * 调整指定用户的配置配额，供受控的管理逻辑调用。
     *
     * @param uid           用户 uid
     * @param newLimitBytes 新配额字节数
     * @param operatorId    操作者 id
     * @param reason        调整原因
     */
    void adjustQuota(Long uid, long newLimitBytes, Long operatorId, String reason);
}
