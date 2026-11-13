package com.orange.service.scheduler;

import com.orange.common.config.OAuthProperties;
import com.orange.service.OAuthClientOriginCache;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * OAuth 客户端 CORS Origin 缓存的定时对账调度器
 *
 * <p>缓存本身在写事务提交后已通过事务事件即时刷新，本任务只做兜底：覆盖管理员直接审批、
 * 多实例部署下事件未送达等遗漏场景。刷新间隔由
 * {@link OAuthProperties} 控制，默认 60 秒。</p>
 *
 * <p>刷新失败不影响服务：缓存保留上一份快照，异常只记日志。</p>
 *
 * @author UserCenter
 */
@Component
public class OAuthClientOriginCacheScheduler {

    private final OAuthClientOriginCache originCache;

    /**
     * 构造器注入缓存
     *
     * @param originCache 已审批 Origin 的本地内存快照
     */
    public OAuthClientOriginCacheScheduler(OAuthClientOriginCache originCache) {
        this.originCache = originCache;
    }

    /**
     * 按配置间隔对账一次 Origin 白名单
     */
    @Scheduled(fixedDelayString = "#{@oauthProperties.originCacheRefreshMs}")
    public void refreshOriginCache() {
        originCache.refreshSafely("定时任务");
    }
}
