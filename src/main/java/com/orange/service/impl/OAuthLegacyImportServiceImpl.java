package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.dto.oauth.LegacyImportTokenItem;
import com.orange.entity.po.OAuthCustomToken;
import com.orange.mapper.OAuthCustomTokenMapper;
import com.orange.service.OAuthLegacyImportService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 旧系统只读令牌导入实现（临时接口）
 *
 * <p>把旧系统签发的只读令牌批量迁移进 {@code oauth_custom_token} 表，统一挂到固定客户端
 * {@code arknights-yituliu-web} 名下。逐条校验后批量入库，非法条目跳过，不阻断整批导入。</p>
 *
 * <p>该接口为存量迁移临时使用，导入结束后应随即下线。</p>
 *
 * @author UserCenter
 */
@Service
public class OAuthLegacyImportServiceImpl implements OAuthLegacyImportService {

    /** 到期时间支持的解析格式：库内标准存储格式 */
    private static final DateTimeFormatter EXPIRE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** name 列长度上限，与表结构一致 */
    private static final int MAX_NAME_LENGTH = 50;

    /** token 列长度上限，与表结构一致 */
    private static final int MAX_TOKEN_LENGTH = 128;

    /** scope 列长度上限，与表结构一致 */
    private static final int MAX_SCOPE_LENGTH = 256;

    /** 单批入库的最大条数，避免单条 SQL 过大 */
    private static final int BATCH_SIZE = 500;

    /** uid 反向索引中 access_token 成员前缀，与 OAuthTokenServiceImpl 保持一致 */
    private static final String OAUTH_ACCESS_MEMBER_PREFIX = "access:";

    private final OAuthCustomTokenMapper oauthCustomTokenMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /** 调用方密钥：与请求头 X-Import-Key 做字符串比对 */
    @Value("${legacy-import-key:}")
    private String importKey;

    /**
     * 构造器注入依赖
     *
     * @param oauthCustomTokenMapper 自定义令牌 Mapper
     * @param stringRedisTemplate    Redis 客户端（同步写入令牌记录，使旧令牌可被资源服务器校验）
     * @param objectMapper           JSON 序列化器（构造 Redis 令牌记录）
     */
    public OAuthLegacyImportServiceImpl(OAuthCustomTokenMapper oauthCustomTokenMapper,
                                        StringRedisTemplate stringRedisTemplate,
                                        ObjectMapper objectMapper) {
        this.oauthCustomTokenMapper = oauthCustomTokenMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量导入旧系统只读令牌
     *
     * @param requestKey 调用方提交的密钥（请求头 X-Import-Key）
     * @param items      待导入的令牌条目列表
     * @return 导入结果（成功入库条数与跳过条数）
     */
    @Override
    public LegacyImportResult importTokens(String requestKey, List<LegacyImportTokenItem> items) {
        // 密钥校验：临时接口按需求仅做固定字符串比对
        if (!StringUtils.hasText(importKey) || !importKey.equals(requestKey)) {
            LogUtil.warn(OAuthLegacyImportServiceImpl.class, "[OAuth] 旧系统令牌导入被拒绝（密钥校验失败）");
            throw new BusinessException(ResultCode.OAUTH_LEGACY_IMPORT_KEY_INVALID);
        }
        if (items == null || items.isEmpty()) {
            return new LegacyImportResult(0, 0);
        }
        List<OAuthCustomToken> records = new ArrayList<>(items.size());
        int skipped = 0;
        for (LegacyImportTokenItem item : items) {
            OAuthCustomToken record = convert(item);
            if (record == null) {
                skipped++;
                continue;
            }
            records.add(record);
        }
        for (int i = 0; i < records.size(); i += BATCH_SIZE) {
            List<OAuthCustomToken> batch = records.subList(i, Math.min(i + BATCH_SIZE, records.size()));
            oauthCustomTokenMapper.batchInsert(batch);
            // 同步写入 Redis：资源服务器仅凭 uc:oauth:access:{token} 校验令牌，
            // 只入库不写 Redis 的旧令牌将无法通过访问校验
            syncToRedis(batch);
        }
        LogUtil.info(OAuthLegacyImportServiceImpl.class,
                "[OAuth] 旧系统令牌导入完成: imported={}, skipped={}", records.size(), skipped);
        return new LegacyImportResult(records.size(), skipped);
    }

    /**
     * 把导入的旧令牌同步写入 Redis，使其与 UC 现场签发的 access_token 等价可校验
     *
     * <p>写入内容与 {@code oauthAccess} 记录一致（uid/clientId/scope），TTL 取令牌到期时间
     * 的剩余秒数；已过期的旧令牌只入库、不写 Redis。同时登记 uid 反向索引，供整体撤销与
     * 惰性清理定位。Redis 写入失败会向上抛出，避免出现「入库成功但令牌不可用」的静默错误。</p>
     *
     * @param records 本批已完成入库的令牌记录
     */
    private void syncToRedis(List<OAuthCustomToken> records) {
        LocalDateTime now = LocalDateTime.now();
        for (OAuthCustomToken record : records) {
            long ttlSeconds = Duration.between(now, record.getExpireTime()).getSeconds();
            if (ttlSeconds <= 0) {
                // 已过期的旧令牌无需写入 Redis
                continue;
            }
            Map<String, Object> redisRecord = new LinkedHashMap<>();
            redisRecord.put("uid", record.getUid());
            redisRecord.put("clientId", record.getClientId());
            redisRecord.put("scope", record.getScope());
            String json;
            try {
                json = objectMapper.writeValueAsString(redisRecord);
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "旧令牌记录序列化失败");
            }
            stringRedisTemplate.opsForValue().set(
                    RedisKeyUtil.oauthAccess(record.getToken()), json, ttlSeconds, TimeUnit.SECONDS);
            stringRedisTemplate.opsForSet().add(
                    RedisKeyUtil.uidOauth(record.getUid()), OAUTH_ACCESS_MEMBER_PREFIX + record.getToken());
        }
    }

    /**
     * 把单条导入项转换为 {@code oauth_custom_token} 记录
     *
     * @param item 导入项
     * @return 记录；uid/token/name/scope/expiresIn/expireTime 存在非法或缺失时返回 null（调用方计为跳过）
     */
    private OAuthCustomToken convert(LegacyImportTokenItem item) {
        if (item == null) {
            return null;
        }
        Long uid = parseLong(item.getUid());
        Long expiresIn = parseLong(item.getExpiresIn());
        LocalDateTime expireTime = parseDateTime(item.getExpireTime());
        if (uid == null || !StringUtils.hasText(item.getToken())
                || !StringUtils.hasText(item.getName()) || !StringUtils.hasText(item.getScope())
                || expiresIn == null || expireTime == null) {
            return null;
        }
        String name = item.getName().trim();
        String token = item.getToken().trim();
        String scope = item.getScope().trim();
        if (name.length() > MAX_NAME_LENGTH || token.length() > MAX_TOKEN_LENGTH
                || scope.length() > MAX_SCOPE_LENGTH) {
            return null;
        }
        OAuthCustomToken record = new OAuthCustomToken();
        record.setUid(uid);
        record.setClientId(LEGACY_CLIENT_ID);
        record.setName(name);
        record.setToken(token);
        record.setScope(scope);
        record.setExpiresIn(expiresIn);
        record.setExpireTime(expireTime);
        return record;
    }

    /**
     * 解析长整型，非法返回 null
     *
     * @param text 原始字符串
     * @return 解析结果，空或非法返回 null
     */
    private Long parseLong(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析到期时间，仅接受 {@code yyyy-MM-dd HH:mm:ss}
     *
     * @param text 原始字符串
     * @return 解析结果，空或非法返回 null
     */
    private LocalDateTime parseDateTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), EXPIRE_TIME_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
