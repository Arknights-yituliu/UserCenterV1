package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.dto.oauthclient.OAuthClientRegisterRequest;
import com.orange.entity.dto.oauthclient.OAuthClientUpdateRequest;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.vo.oauth.OAuthClientCredentialVO;
import com.orange.entity.vo.oauth.OAuthClientVO;
import com.orange.mapper.OAuthClientMapper;
import com.orange.service.OAuthClientAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OAuth 客户端自助管理服务实现：客户端注册、查询、更新、密钥轮换、停用、删除（级联吊销令牌）
 *
 * @author UserCenter
 */
@Service
public class OAuthClientAdminServiceImpl implements OAuthClientAdminService {

    private static final Logger log = LoggerFactory.getLogger(OAuthClientAdminServiceImpl.class);

    /** uidOauth 反向索引中 access_token 成员前缀（与 OAuthTokenServiceImpl 保持一致） */
    private static final String OAUTH_ACCESS_MEMBER_PREFIX = "access:";

    /** uidOauth 反向索引中 refresh_token 成员前缀（与 OAuthTokenServiceImpl 保持一致） */
    private static final String OAUTH_REFRESH_MEMBER_PREFIX = "refresh:";

    private final OAuthClientMapper oauthClientMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    /** 单个开发者账号可注册的客户端数量上限 */
    @Value("${user-center.oauth.max-clients-per-owner:10}")
    private int maxClientsPerOwner;

    /**
     * 构造器注入依赖
     *
     * @param oauthClientMapper    OAuth 客户端 Mapper
     * @param stringRedisTemplate  Redis 客户端（级联清理令牌）
     * @param objectMapper         JSON 序列化器（解析令牌记录）
     */
    public OAuthClientAdminServiceImpl(OAuthClientMapper oauthClientMapper,
                                       StringRedisTemplate stringRedisTemplate,
                                       ObjectMapper objectMapper) {
        this.oauthClientMapper = oauthClientMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 注册客户端：校验数量上限与回调地址，生成 client_id/secret（BCrypt 入库），返回明文 secret 一次
     *
     * @param uid     开发者用户 uid
     * @param request 注册参数
     * @return 客户端凭证（含明文 secret，仅此一次）
     */
    @Override
    public OAuthClientCredentialVO register(Long uid, OAuthClientRegisterRequest request) {
        // 1. 数量上限校验：单账号最多 maxClientsPerOwner 个客户端
        Long count = oauthClientMapper.selectCount(
                Wrappers.<OAuthClient>lambdaQuery().eq(OAuthClient::getOwnerUid, uid));
        if (count >= maxClientsPerOwner) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_LIMIT);
        }
        // 2. 回调地址安全校验（必须 https，本地联调允许 http://localhost）
        validateRedirectUris(request.getRedirectUris());
        // 3. 生成凭证
        String clientId = randomToken("cl_", 24);
        String clientSecret = randomToken("sk_", 32);
        // 4. 组装并入库（secret 仅存 BCrypt 哈希）
        OAuthClient client = new OAuthClient();
        client.setId(clientId);
        client.setClientSecret(passwordEncoder.encode(clientSecret));
        client.setClientName(request.getClientName());
        client.setAuthMethods(StringUtils.hasText(request.getAuthMethod())
                ? request.getAuthMethod() : "client_secret_post");
        client.setGrantTypes(join(request.getGrantTypes() == null || request.getGrantTypes().isEmpty()
                ? Arrays.asList("authorization_code", "refresh_token") : request.getGrantTypes()));
        client.setRedirectUris(join(request.getRedirectUris()));
        client.setScopes(join(request.getScopes()));
        // 安全策略：PKCE 与授权确认页为系统强制开启，不开放给开发者自助编辑
        client.setRequirePkce(1);
        client.setRequireAuthConsent(1);
        client.setWebsiteOrigin(request.getWebsiteOrigin());
        client.setAccessTokenTtl(request.getAccessTokenTtl());
        client.setRefreshTokenTtl(request.getRefreshTokenTtl());
        client.setStatus(1);
        client.setAdminBanned(0);
        client.setOwnerUid(uid);
        oauthClientMapper.insert(client);
        log.info("[OAuthClient] 注册客户端成功: ownerUid={}, clientId={}", uid, clientId);
        return credential(client, clientSecret);
    }

    /**
     * 查询当前开发者名下全部客户端（按创建时间倒序）
     *
     * @param uid 开发者用户 uid
     * @return 客户端列表（不返回 secret）
     */
    @Override
    public List<OAuthClientVO> listClients(Long uid) {
        return oauthClientMapper.selectList(Wrappers.<OAuthClient>lambdaQuery()
                        .eq(OAuthClient::getOwnerUid, uid)
                        .orderByDesc(OAuthClient::getCreateTime))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 查询单个客户端详情（校验归属）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @return 客户端详情（不返回 secret）
     */
    @Override
    public OAuthClientVO getClient(Long uid, String clientId) {
        return toVO(getOwnedClient(uid, clientId));
    }

    /**
     * 更新客户端（仅可改非敏感字段，client_id/authMethod/grantTypes 固定）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @param request  更新参数
     */
    @Override
    public void updateClient(Long uid, String clientId, OAuthClientUpdateRequest request) {
        validateRedirectUris(request.getRedirectUris());
        OAuthClient client = getOwnedClient(uid, clientId);
        client.setClientName(request.getClientName());
        client.setRedirectUris(join(request.getRedirectUris()));
        client.setScopes(join(request.getScopes()));
        client.setWebsiteOrigin(request.getWebsiteOrigin());
        client.setAccessTokenTtl(request.getAccessTokenTtl());
        client.setRefreshTokenTtl(request.getRefreshTokenTtl());
        oauthClientMapper.updateById(client);
        log.info("[OAuthClient] 更新客户端成功: ownerUid={}, clientId={}", uid, clientId);
    }

    /**
     * 轮换密钥：新 secret 生成并 BCrypt 入库，旧值立即失效，返回新明文一次
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @return 客户端凭证（含新明文 secret，仅此一次）
     */
    @Override
    public OAuthClientCredentialVO rotateSecret(Long uid, String clientId) {
        OAuthClient client = getOwnedClient(uid, clientId);
        String newSecret = randomToken("sk_", 32);
        client.setClientSecret(passwordEncoder.encode(newSecret));
        oauthClientMapper.updateById(client);
        log.info("[OAuthClient] 轮换密钥成功: ownerUid={}, clientId={}", uid, clientId);
        return credential(client, newSecret);
    }

    /**
     * 停用/启用客户端：停用后授权跳转、换 token、刷新均返回 90001
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @param enabled  true=启用 false=停用
     */
    @Override
    public void setClientStatus(Long uid, String clientId, boolean enabled) {
        OAuthClient client = getOwnedClient(uid, clientId);
        // 管理员封禁优先级最高：封禁中的客户端不允许用户自助启用
        if (enabled && client.getAdminBanned() != null && client.getAdminBanned() == 1) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_BANNED);
        }
        client.setStatus(enabled ? 1 : 0);
        oauthClientMapper.updateById(client);
        log.info("[OAuthClient] {}客户端成功: ownerUid={}, clientId={}", enabled ? "启用" : "停用", uid, clientId);
    }

    /**
     * 删除客户端：级联吊销其名下全部 access/refresh 令牌后物理删除记录，不可恢复
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     */
    @Override
    public void deleteClient(Long uid, String clientId) {
        OAuthClient client = getOwnedClient(uid, clientId);
        revokeTokensByClient(clientId);
        oauthClientMapper.deleteById(client.getId());
        log.info("[OAuthClient] 删除客户端成功: ownerUid={}, clientId={}", uid, clientId);
    }

    /**
     * 校验回调地址合法性：必须 https，本地联调允许 http://localhost
     *
     * @param redirectUris 回调地址列表
     */
    private void validateRedirectUris(List<String> redirectUris) {
        for (String uri : redirectUris) {
            if (!uri.startsWith("https://") && !uri.startsWith("http://localhost")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "回调地址必须使用 https（本地联调可 http://localhost）：" + uri);
            }
        }
    }

    /**
     * 查询归属当前开发者的客户端，不存在或非本人所有则抛异常
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @return 客户端实体
     */
    private OAuthClient getOwnedClient(Long uid, String clientId) {
        OAuthClient client = oauthClientMapper.selectById(clientId);
        if (client == null) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID);
        }
        if (!uid.equals(client.getOwnerUid())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该客户端");
        }
        return client;
    }

    /**
     * 级联吊销指定客户端名下的全部 access/refresh 令牌（扫描对应前缀，匹配 clientId 后删除并维护 uid 反向索引）
     *
     * @param clientId 客户端 ID
     */
    private void revokeTokensByClient(String clientId) {
        revokeByPrefix(RedisKeyUtil.oauthAccessPrefix(), OAUTH_ACCESS_MEMBER_PREFIX, clientId);
        revokeByPrefix(RedisKeyUtil.oauthRefreshPrefix(), OAUTH_REFRESH_MEMBER_PREFIX, clientId);
    }

    /**
     * 按前缀扫描并删除归属指定客户端的令牌
     *
     * @param prefix      令牌 key 前缀
     * @param memberPrefix uid 反向索引成员前缀
     * @param clientId    客户端 ID
     */
    private void revokeByPrefix(String prefix, String memberPrefix, String clientId) {
        Cursor<String> cursor = stringRedisTemplate.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(200).build());
        try (cursor) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String json = stringRedisTemplate.opsForValue().get(key);
                if (!StringUtils.hasText(json)) {
                    continue;
                }
                try {
                    Map<String, Object> record = objectMapper.readValue(json, Map.class);
                    if (!clientId.equals(record.get("clientId"))) {
                        continue;
                    }
                    stringRedisTemplate.delete(key);
                    Object uidObj = record.get("uid");
                    if (uidObj != null) {
                        String token = key.substring(prefix.length());
                        stringRedisTemplate.opsForSet().remove(
                                RedisKeyUtil.uidOauth(((Number) uidObj).longValue()), memberPrefix + token);
                    }
                } catch (IOException e) {
                    log.warn("[OAuthClient] 解析令牌记录失败，跳过: key={}", key);
                }
            }
        }
    }

    /**
     * 实体转视图对象（secret 不下发，逗号分隔字段拆为列表）
     *
     * @param client 客户端实体
     * @return 客户端视图
     */
    private OAuthClientVO toVO(OAuthClient client) {
        OAuthClientVO vo = new OAuthClientVO();
        vo.setClientId(client.getId());
        vo.setClientName(client.getClientName());
        vo.setRedirectUris(split(client.getRedirectUris()));
        vo.setScopes(split(client.getScopes()));
        vo.setRequirePkce(client.getRequirePkce() != null && client.getRequirePkce() == 1);
        vo.setRequireAuthConsent(client.getRequireAuthConsent() != null && client.getRequireAuthConsent() == 1);
        vo.setWebsiteOrigin(client.getWebsiteOrigin());
        vo.setStatus(client.getStatus());
        vo.setAdminBanned(client.getAdminBanned());
        vo.setCreateTime(client.getCreateTime());
        return vo;
    }

    /**
     * 构建凭证视图（含明文 secret，仅注册/轮换时调用）
     *
     * @param client       客户端实体
     * @param clientSecret 明文密钥
     * @return 凭证视图
     */
    private OAuthClientCredentialVO credential(OAuthClient client, String clientSecret) {
        OAuthClientCredentialVO vo = new OAuthClientCredentialVO();
        vo.setClientId(client.getId());
        vo.setClientSecret(clientSecret);
        vo.setClientName(client.getClientName());
        vo.setStatus(client.getStatus());
        return vo;
    }

    /**
     * 列表转逗号分隔字符串
     *
     * @param values 字符串列表
     * @return 逗号分隔字符串
     */
    private String join(List<String> values) {
        return String.join(",", values);
    }

    /**
     * 逗号分隔字符串转列表（空值返回空列表）
     *
     * @param value 逗号分隔字符串
     * @return 字符串列表
     */
    private List<String> split(String value) {
        if (!StringUtils.hasText(value)) {
            return new ArrayList<>();
        }
        return Arrays.stream(value.split(",")).filter(StringUtils::hasText).collect(Collectors.toList());
    }

    /**
     * 生成随机令牌（SecureRandom，字符集为 0-9a-f）
     *
     * @param prefix 前缀（如 cl_ / sk_）
     * @param length 随机段长度（十六进制字符个数）
     * @return 前缀 + 随机十六进制串
     */
    private String randomToken(String prefix, int length) {
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < length; i++) {
            sb.append(Character.forDigit(secureRandom.nextInt(16), 16));
        }
        return sb.toString();
    }
}
