package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.OAuthClientAuthMethod;
import com.orange.common.enums.OAuthGrantType;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.RedisKeyUtil;
import com.orange.entity.dto.oauthclient.OAuthClientRegisterRequest;
import com.orange.entity.dto.oauthclient.OAuthClientUpdateRequest;
import com.orange.entity.po.OAuthClient;
import com.orange.entity.po.OAuthClientOrigin;
import com.orange.entity.vo.oauth.OAuthClientCredentialVO;
import com.orange.entity.vo.oauth.OAuthClientVO;
import com.orange.event.OAuthClientOriginChangedEvent;
import com.orange.event.OAuthClientReviewNotificationEvent;
import com.orange.mapper.OAuthClientMapper;
import com.orange.mapper.OAuthClientOriginMapper;
import com.orange.service.OAuthClientAdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final OAuthClientOriginMapper oauthClientOriginMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    /** 单个开发者账号可注册的客户端数量上限 */
    @Value("${user-center.oauth.max-clients-per-owner:10}")
    private int maxClientsPerOwner;

    /**
     * 构造器注入依赖
     *
     * @param oauthClientMapper    OAuth 客户端 Mapper
     * @param oauthClientOriginMapper OAuth 客户端 Origin Mapper
     * @param stringRedisTemplate  Redis 客户端（级联清理令牌）
     * @param objectMapper         JSON 序列化器（解析令牌记录）
     * @param eventPublisher       Spring 事务事件发布器
     */
    public OAuthClientAdminServiceImpl(OAuthClientMapper oauthClientMapper,
                                       OAuthClientOriginMapper oauthClientOriginMapper,
                                       StringRedisTemplate stringRedisTemplate,
                                       ObjectMapper objectMapper,
                                       ApplicationEventPublisher eventPublisher) {
        this.oauthClientMapper = oauthClientMapper;
        this.oauthClientOriginMapper = oauthClientOriginMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 注册客户端：校验协议能力与地址边界，按认证方式决定是否生成密钥。
     *
     * <p>{@code none} 表示浏览器等无法保密的公共客户端，数据库中的密钥必须为 null；
     * {@code client_secret_post} 表示加密客户端，只保存 BCrypt 哈希，明文仅随本次响应返回。</p>
     *
     * @param uid     开发者用户 uid
     * @param request 注册参数
     * @return 客户端凭证（含明文 secret，仅此一次）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuthClientCredentialVO register(Long uid, OAuthClientRegisterRequest request) {
        // 1. 数量上限校验：单账号最多 maxClientsPerOwner 个客户端
        Long count = oauthClientMapper.selectCount(
                Wrappers.<OAuthClient>lambdaQuery().eq(OAuthClient::getOwnerUid, uid));
        if (count >= maxClientsPerOwner) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_LIMIT);
        }
        // 2. 在任何凭证生成和入库前完成归一化。这里集中处理必填项、白名单和跨字段规则，
        // 避免 DTO 注解只能校验单字段、无法表达 grantTypes 依赖关系的问题。
        OAuthClientAuthMethod authMethod = normalizeAuthMethod(request.getAuthMethod());
        List<String> grantTypes = normalizeGrantTypes(request.getGrantTypes());
        List<String> redirectUris = normalizeRedirectUris(request.getRedirectUris());
        List<String> scopes = normalizeScopes(request.getScopes());
        String websiteOrigin = normalizeWebsiteOrigin(request.getWebsiteOrigin());

        // 3. client_id 对两类客户端都需要；只有加密客户端生成 client_secret。
        String clientId = randomToken("cl_", 24);
        String clientSecret = null;
        String storedSecret = null;
        if (authMethod == OAuthClientAuthMethod.CLIENT_SECRET_POST) {
            clientSecret = randomToken("sk_", 32);
            storedSecret = passwordEncoder.encode(clientSecret);
        }

        // 4. 组装并入库。公共客户端显式写入 NULL，不能用空串伪装成“无密钥”。
        OAuthClient client = new OAuthClient();
        client.setId(clientId);
        client.setClientSecret(storedSecret);
        client.setClientName(request.getClientName());
        client.setAuthMethods(authMethod.getValue());
        client.setGrantTypes(join(grantTypes));
        client.setRedirectUris(join(redirectUris));
        client.setScopes(join(scopes));
        // 安全策略：PKCE 与授权确认页为系统强制开启，不开放给开发者自助编辑
        client.setRequirePkce(1);
        client.setRequireAuthConsent(1);
        client.setAccessTokenTtl(request.getAccessTokenTtl());
        client.setRefreshTokenTtl(request.getRefreshTokenTtl());
        // 新客户端默认进入管理员审批状态，且不具备直连认证能力。直连登录和注册
        // 只能由管理员对受信客户端开通，不能通过客户端自助接口申请或修改。
        client.setOwnerEnabled(1);
        client.setAdminApproved(0);
        client.setDirectAuthEnabled(0);
        client.setOwnerUid(uid);
        oauthClientMapper.insert(client);
        if (websiteOrigin != null) {
            oauthClientOriginMapper.insert(newPendingOrigin(
                    clientId, request.getClientName(), websiteOrigin, true));
            publishOriginChanged();
        }
        publishReviewNotification("注册", client, websiteOrigin);
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
        List<OAuthClient> clients = oauthClientMapper.selectList(Wrappers.<OAuthClient>lambdaQuery()
                        .eq(OAuthClient::getOwnerUid, uid)
                        .orderByDesc(OAuthClient::getCreateTime));
        if (clients.isEmpty()) {
            return List.of();
        }
        Map<String, OAuthClientOrigin> origins = oauthClientOriginMapper.selectBatchIds(
                        clients.stream().map(OAuthClient::getId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(OAuthClientOrigin::getClientId, origin -> origin));
        return clients.stream().map(client -> toVO(client, origins.get(client.getId())))
                .collect(Collectors.toList());
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
        OAuthClient client = getOwnedClient(uid, clientId);
        return toVO(client, oauthClientOriginMapper.selectById(clientId));
    }

    /**
     * 更新客户端（仅可改非敏感字段，client_id/authMethod/grantTypes 固定）
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     * @param request  更新参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateClient(Long uid, String clientId, OAuthClientUpdateRequest request) {
        List<String> redirectUris = normalizeRedirectUris(request.getRedirectUris());
        List<String> scopes = normalizeScopes(request.getScopes());
        String websiteOrigin = normalizeWebsiteOrigin(request.getWebsiteOrigin());
        OAuthClient client = getOwnedClient(uid, clientId);
        client.setClientName(request.getClientName());
        client.setRedirectUris(join(redirectUris));
        client.setScopes(join(scopes));
        client.setAccessTokenTtl(request.getAccessTokenTtl());
        client.setRefreshTokenTtl(request.getRefreshTokenTtl());
        oauthClientMapper.updateById(client);
        if (syncOrigin(clientId, request.getClientName(), websiteOrigin,
                client.getOwnerEnabled() != null && client.getOwnerEnabled() == 1)) {
            publishOriginChanged();
        }
        publishReviewNotification("更新", client, websiteOrigin);
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
        // 公共客户端的安全模型建立在“没有可保密的凭证”之上。禁止通过轮换接口隐式
        // 写入 secret，否则数据库声明仍为 none、运行时却出现密钥，形成配置漂移。
        if (OAuthClientAuthMethod.NONE.matches(client.getAuthMethods())) {
            throw new BusinessException(ResultCode.ILLEGAL_OPERATION, "公共客户端没有可轮换的密钥");
        }
        if (!OAuthClientAuthMethod.CLIENT_SECRET_POST.matches(client.getAuthMethods())) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_INVALID, "客户端认证方式不受支持");
        }
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
     * @param ownerEnabled 所有者是否启用客户端
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setOwnerEnabled(Long uid, String clientId, boolean ownerEnabled) {
        OAuthClient client = getOwnedClient(uid, clientId);
        // 管理员审批优先级最高：待审批或封禁中的客户端不允许所有者自助启用。
        if (ownerEnabled && (client.getAdminApproved() == null || client.getAdminApproved() != 1)) {
            throw new BusinessException(ResultCode.OAUTH_CLIENT_BANNED);
        }
        client.setOwnerEnabled(ownerEnabled ? 1 : 0);
        oauthClientMapper.updateById(client);
        OAuthClientOrigin origin = oauthClientOriginMapper.selectById(clientId);
        if (origin != null && !Objects.equals(origin.getEnabled(), ownerEnabled ? 1 : 0)) {
            origin.setEnabled(ownerEnabled ? 1 : 0);
            oauthClientOriginMapper.updateById(origin);
            publishOriginChanged();
        }
        log.info("[OAuthClient] {}客户端成功: ownerUid={}, clientId={}",
                ownerEnabled ? "启用" : "停用", uid, clientId);
    }

    /**
     * 删除客户端：级联吊销其名下全部 access/refresh 令牌后物理删除记录，不可恢复
     *
     * @param uid      开发者用户 uid
     * @param clientId 客户端 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteClient(Long uid, String clientId) {
        OAuthClient client = getOwnedClient(uid, clientId);
        revokeTokensByClient(clientId);
        oauthClientOriginMapper.deleteById(clientId);
        oauthClientMapper.deleteById(client.getId());
        publishOriginChanged();
        log.info("[OAuthClient] 删除客户端成功: ownerUid={}, clientId={}", uid, clientId);
    }

    /**
     * 校验客户端认证方式。调用方必须明确声明客户端类型，不根据缺省字段推断；
     * 值必须与枚举协议值完全一致，不接受大小写变体和首尾空格。
     */
    private OAuthClientAuthMethod normalizeAuthMethod(String authMethod) {
        if (!StringUtils.hasText(authMethod)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "authMethod 不能为空");
        }
        return OAuthClientAuthMethod.fromValue(authMethod);
    }

    /**
     * 归一化授权类型并执行跨字段规则。
     *
     * <p>调用方必须显式提交非空列表。输出顺序固定为枚举声明顺序，因而数据库不会
     * 因为请求数组顺序或重复项而产生多种等价字符串。</p>
     */
    private List<String> normalizeGrantTypes(List<String> requestedGrantTypes) {
        if (requestedGrantTypes == null || requestedGrantTypes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "grantTypes 不能为空");
        }
        EnumSet<OAuthGrantType> normalized = EnumSet.noneOf(OAuthGrantType.class);
        for (String grantType : requestedGrantTypes) {
            normalized.add(OAuthGrantType.fromValue(grantType));
        }
        if (!normalized.contains(OAuthGrantType.AUTHORIZATION_CODE)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "grantTypes 必须包含 authorization_code");
        }
        return Arrays.stream(OAuthGrantType.values())
                .filter(normalized::contains)
                .map(OAuthGrantType::getValue)
                .collect(Collectors.toList());
    }

    /**
     * 清理 scope 列表：去除首尾空白、保持首次出现顺序并去重。
     *
     * <p>数据库使用英文逗号存储列表，所以单个 scope 内严禁逗号，否则读取时无法区分
     * “一个带逗号的值”和“两个独立值”。</p>
     */
    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "授权范围不能为空");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String scope : scopes) {
            if (!StringUtils.hasText(scope)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "授权范围不能为空");
            }
            String value = scope.trim();
            if (value.contains(",")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "单个授权范围不能包含英文逗号: " + value);
            }
            normalized.add(value);
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 校验并去重回调地址。登记值不会被 URL 解码或改写，授权阶段仍以完整字符串精确匹配。
     *
     * <p>生产地址只允许 HTTPS；HTTP 仅对精确的 loopback 主机开放。使用 {@link URI}
     * 解析主机而不是字符串前缀判断，可阻止 {@code localhost.example.com} 绕过本地例外。</p>
     */
    private List<String> normalizeRedirectUris(List<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "回调地址不能为空");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : redirectUris) {
            if (!StringUtils.hasText(value) || !value.equals(value.trim()) || value.contains(",")) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "回调地址格式不合法: " + value);
            }
            URI uri = parseUri(value, "回调地址");
            validateWebUri(uri, value, true);
            normalized.add(value);
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 校验并规范化网站 Origin。Origin 只能包含 scheme、host 和可选 port，不能携带
     * 路径、查询参数、用户信息或 fragment。该值写入 oauth_client_origin 后默认为
     * 待审核，只有管理员审批通过才会进入运行时 CORS 缓存。
     */
    private String normalizeWebsiteOrigin(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (!value.equals(value.trim()) || value.contains(",")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "网站 Origin 格式不合法: " + value);
        }
        URI uri = parseUri(value, "网站 Origin");
        validateWebUri(uri, value, false);
        if (StringUtils.hasText(uri.getRawPath()) || uri.getRawQuery() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "网站 Origin 不能包含路径或查询参数: " + value);
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = stripIpv6Brackets(uri.getHost()).toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        boolean defaultPort = ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
        String renderedHost = host.contains(":") ? "[" + host + "]" : host;
        return scheme + "://" + renderedHost + (port == -1 || defaultPort ? "" : ":" + port);
    }

    /**
     * 对回调地址和 Origin 共用的 URI 安全边界进行检查。
     *
     * @param uri          已解析 URI
     * @param original     原始输入，用于错误信息
     * @param allowContent true 时允许回调地址携带路径和查询参数
     */
    private void validateWebUri(URI uri, String original, boolean allowContent) {
        if (!uri.isAbsolute() || uri.isOpaque() || !StringUtils.hasText(uri.getHost())
                || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "地址必须是无 user-info 和 fragment 的绝对 URI: " + original);
        }
        if (uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "地址端口不合法: " + original);
        }

        String scheme = uri.getScheme();
        String host = stripIpv6Brackets(uri.getHost());
        if ("https".equalsIgnoreCase(scheme)) {
            return;
        }
        if ("http".equalsIgnoreCase(scheme) && isLoopbackHost(host)) {
            return;
        }
        String addressType = allowContent ? "回调地址" : "网站 Origin";
        throw new BusinessException(ResultCode.PARAM_ERROR,
                addressType + "必须使用 https；http 仅允许 localhost、127.0.0.1 或 ::1: " + original);
    }

    /** 将文本解析为 URI，并把语法异常转换为统一业务参数错误。 */
    private URI parseUri(String value, String fieldName) {
        try {
            return new URI(value);
        } catch (URISyntaxException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, fieldName + "格式不合法: " + value);
        }
    }

    /** 判断 URI host 是否为允许使用 HTTP 的本机回环地址。 */
    private boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    }

    /** 兼容不同 JDK 对 IPv6 host 是否保留方括号的返回差异。 */
    private String stripIpv6Brackets(String host) {
        if (host != null && host.length() >= 2 && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
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
    @SuppressWarnings("unchecked")
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
    private OAuthClientVO toVO(OAuthClient client, OAuthClientOrigin origin) {
        OAuthClientVO vo = new OAuthClientVO();
        vo.setClientId(client.getId());
        vo.setClientName(client.getClientName());
        vo.setAuthMethod(client.getAuthMethods());
        vo.setGrantTypes(split(client.getGrantTypes()));
        vo.setRedirectUris(split(client.getRedirectUris()));
        vo.setScopes(split(client.getScopes()));
        vo.setRequirePkce(client.getRequirePkce() != null && client.getRequirePkce() == 1);
        vo.setRequireAuthConsent(client.getRequireAuthConsent() != null && client.getRequireAuthConsent() == 1);
        vo.setWebsiteOrigin(origin == null ? null : origin.getOrigin());
        vo.setOriginApproved(origin != null && origin.getAdminApproved() != null
                && origin.getAdminApproved() == 1);
        vo.setOwnerEnabled(client.getOwnerEnabled() != null && client.getOwnerEnabled() == 1);
        vo.setAdminApproved(client.getAdminApproved() != null && client.getAdminApproved() == 1);
        vo.setDirectAuthEnabled(client.getDirectAuthEnabled() != null && client.getDirectAuthEnabled() == 1);
        vo.setCreateTime(client.getCreateTime());
        return vo;
    }

    /**
     * 同步客户端当前登记的唯一 Origin。Origin 内容变化时必须重新进入管理员审批。
     *
     * @return 是否修改了 Origin 表
     */
    private boolean syncOrigin(String clientId, String clientName, String normalizedOrigin, boolean enabled) {
        OAuthClientOrigin existing = oauthClientOriginMapper.selectById(clientId);
        if (normalizedOrigin == null) {
            if (existing == null) {
                return false;
            }
            oauthClientOriginMapper.deleteById(clientId);
            return true;
        }
        if (existing == null) {
            oauthClientOriginMapper.insert(newPendingOrigin(clientId, clientName, normalizedOrigin, enabled));
            return true;
        }

        boolean originChanged = !normalizedOrigin.equals(existing.getOrigin());
        boolean clientNameChanged = !Objects.equals(clientName, existing.getClientName());
        int nextEnabled = enabled ? 1 : 0;
        boolean enabledChanged = !Objects.equals(existing.getEnabled(), nextEnabled);
        if (!originChanged && !clientNameChanged && !enabledChanged) {
            return false;
        }
        existing.setClientName(clientName);
        existing.setOrigin(normalizedOrigin);
        existing.setEnabled(nextEnabled);
        if (originChanged) {
            existing.setAdminApproved(0);
        }
        oauthClientOriginMapper.updateById(existing);
        return true;
    }

    private OAuthClientOrigin newPendingOrigin(
            String clientId, String clientName, String origin, boolean enabled) {
        OAuthClientOrigin record = new OAuthClientOrigin();
        record.setClientId(clientId);
        record.setClientName(clientName);
        record.setOrigin(origin);
        record.setEnabled(enabled ? 1 : 0);
        record.setAdminApproved(0);
        return record;
    }

    private void publishOriginChanged() {
        eventPublisher.publishEvent(new OAuthClientOriginChangedEvent());
    }

    private void publishReviewNotification(String action, OAuthClient client, String origin) {
        eventPublisher.publishEvent(new OAuthClientReviewNotificationEvent(
                action, client.getId(), client.getClientName(), client.getOwnerUid(), origin));
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
        vo.setAuthMethod(client.getAuthMethods());
        vo.setOwnerEnabled(client.getOwnerEnabled() != null && client.getOwnerEnabled() == 1);
        vo.setAdminApproved(client.getAdminApproved() != null && client.getAdminApproved() == 1);
        vo.setDirectAuthEnabled(client.getDirectAuthEnabled() != null && client.getDirectAuthEnabled() == 1);
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
