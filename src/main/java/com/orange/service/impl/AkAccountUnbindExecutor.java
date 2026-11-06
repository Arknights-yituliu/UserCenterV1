package com.orange.service.impl;

import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkDefaultSourceMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 森空岛游戏账号解绑写事务执行器
 *
 * <p>独立成 Bean 是为了让 {@code @Transactional} 生效（Spring 自调用不走代理）。
 * 解绑只删除当前用户与该 ak_uid 的绑定关系，以及恰好指向该账号的默认偏好；
 * {@code ak_player_info / ak_operator_state / ak_item_data} 按 ak_uid 共享，
 * 解绑不清理这些数据，其他绑定用户与后续重新导入都不受影响。</p>
 *
 * @author UserCenter
 */
@Component
public class AkAccountUnbindExecutor {

    /** 解绑只涉及森空岛自动导入的账号 */
    private static final String SOURCE_SKLAND = "skland";

    private final AkAccountBindingMapper bindingMapper;
    private final AkDefaultSourceMapper defaultSourceMapper;

    /**
     * 构造器注入 Mapper
     *
     * @param bindingMapper       绑定关系 Mapper
     * @param defaultSourceMapper 默认数据来源偏好 Mapper
     */
    public AkAccountUnbindExecutor(AkAccountBindingMapper bindingMapper,
                                   AkDefaultSourceMapper defaultSourceMapper) {
        this.bindingMapper = bindingMapper;
        this.defaultSourceMapper = defaultSourceMapper;
    }

    /**
     * 解除当前用户对某游戏账号的绑定，并清除指向该账号的默认偏好
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     */
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long uid, String akUid) {
        bindingMapper.deleteBinding(akUid, uid);
        defaultSourceMapper.deleteByTarget(uid, SOURCE_SKLAND, akUid);
    }
}
