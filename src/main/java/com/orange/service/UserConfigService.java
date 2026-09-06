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
     * 保存用户配置（不做并发控制）：
     * <ul>
     *   <li>id 为空 → 新建（按 category+version+name 幂等键防重复，冲突抛 {@code ConfigConflictException}）</li>
     *   <li>id 非空 → 按 id 直接覆盖更新（忽略 expectedHash，不校验内容 hash，last-write-wins）</li>
     * </ul>
     *
     * @param uid     用户 uid
     * @param request 保存参数
     * @return 配置 id 和新内容 hash
     */
    UserConfigSaveVO saveConfig(Long uid, UserConfigSaveRequest request);

    /**
     * 按 id + expectedHash 条件更新配置（防并发覆盖，乐观锁）：
     * 仅当数据库当前内容 hash 等于请求携带的 expectedHash 时更新成功，
     * 否则抛 {@code ConfigConflictException}（携带最新 hash 供调用方重试）。
     * 必须携带 id，且 category/version/name 不能修改；本接口不承担创建。
     *
     * @param uid     用户 uid
     * @param request 保存参数（id + expectedHash 必填）
     * @return 配置 id 和新内容 hash
     */
    UserConfigSaveVO saveConfigIfMatch(Long uid, UserConfigSaveRequest request);

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
