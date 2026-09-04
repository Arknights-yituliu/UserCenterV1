package com.orange.service;

import com.orange.event.OAuthClientOriginChangedEvent;
import com.orange.mapper.OAuthClientOriginMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 已审批 CORS Origin 的本地内存快照。
 *
 * <p>请求只访问不可变内存集合，不查询数据库。刷新成功后原子替换整个快照；刷新失败时
 * 保留上一份快照，应用首次加载失败则保持空集合并拒绝所有跨域请求。</p>
 *
 * @author UserCenter
 */
@Component
public class OAuthClientOriginCache {

    private static final Logger log = LoggerFactory.getLogger(OAuthClientOriginCache.class);

    private final OAuthClientOriginMapper oauthClientOriginMapper;
    private final AtomicReference<Set<String>> allowedOrigins = new AtomicReference<>(Set.of());

    public OAuthClientOriginCache(OAuthClientOriginMapper oauthClientOriginMapper) {
        this.oauthClientOriginMapper = oauthClientOriginMapper;
    }

    /**
     * 判断 Origin 是否在当前已审批快照中，按规范化后的完整字符串精确匹配。
     *
     * @param origin 请求 Origin
     * @return 是否允许跨域
     */
    public boolean isAllowed(String origin) {
        return StringUtils.hasText(origin) && allowedOrigins.get().contains(origin);
    }

    /**
     * 从 oauth_client_origin 单表加载启用且审批通过的 Origin，并原子替换缓存。
     *
     * @return 刷新后的 Origin 数量
     */
    public synchronized int refresh() {
        List<String> records = oauthClientOriginMapper.selectApprovedOrigins();
        Set<String> next = new LinkedHashSet<>();
        for (String origin : records) {
            if (StringUtils.hasText(origin)) {
                next.add(origin);
            }
        }
        allowedOrigins.set(Set.copyOf(next));
        log.info("[CORS] Origin 缓存刷新完成: count={}", next.size());
        return next.size();
    }

    /** 应用启动完成后加载一次，失败时保持空白名单。 */
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStartup() {
        refreshSafely("应用启动");
    }

    /** 定时对账，覆盖管理员直接审批以及多实例事件遗漏的情况。 */
    @Scheduled(fixedDelayString = "${user-center.oauth.origin-cache-refresh-ms:60000}")
    public void scheduledRefresh() {
        refreshSafely("定时任务");
    }

    /** Origin 写事务提交后立即刷新本节点缓存。 */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOriginChanged(OAuthClientOriginChangedEvent ignored) {
        refreshSafely("数据变更");
    }

    private void refreshSafely(String trigger) {
        try {
            refresh();
        } catch (RuntimeException e) {
            log.error("[CORS] Origin 缓存刷新失败，保留上一份快照: trigger={}", trigger, e);
        }
    }
}
