package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.PatUtil;
import com.orange.entity.dto.pat.PatIssueRequest;
import com.orange.entity.po.TlcPat;
import com.orange.entity.vo.pat.PatIssueVO;
import com.orange.entity.vo.pat.PatVO;
import com.orange.mapper.TlcPatMapper;
import com.orange.service.OAuthTokenService;
import com.orange.service.PatPrincipal;
import com.orange.service.PatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 个人访问令牌服务实现
 *
 * <p>管理面：签发 / 列表 / 撤销 / 全撤销 + 应用视角列表与吊销；消费面：
 * {@link #resolve(String)} 供 {@code /pat/**} 拦截器解析凭证。两条链路都不写 Redis、
 * 不改 OAuth 认证分派——存量 access_token 与 {@code uc:oauth:*} 键零影响。</p>
 *
 * <p><strong>全链路明文（业务取舍）</strong>：签发与迁移都把令牌明文原样写入 {@code tlc_pat.token}，
 * 校验与应用侧吊销按明文精确匹配，列表接口回传明文。日志只记 id/uid/name 等元数据，
 * 严禁把令牌明文写入日志、异常与监控上报。</p>
 *
 * @author UserCenter
 */
@Service
public class PatServiceImpl implements PatService {

    /** 来源：用户自助签发 */
    private static final String SOURCE_SESSION = "session";

    /** 有效期上限：与直签 access_token 同口径（10 年零 1 个月），避免「无限期」与 plusSeconds 溢出 */
    private static final long MAX_TTL_SECONDS = (365L * 10 + 31) * 24 * 60 * 60;

    /** payload 序列化后的最大字节数 */
    private static final int MAX_PAYLOAD_BYTES = 4096;

    /** name 最大长度（与 tlc_pat.name 列宽一致） */
    private static final int NAME_MAX_LENGTH = 50;

    private final TlcPatMapper tlcPatMapper;

    private final OAuthTokenService oauthTokenService;

    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param tlcPatMapper       PAT Mapper
     * @param oauthTokenService  OAuth 令牌服务（复用权限收敛与应用侧直连准入校验）
     * @param objectMapper       JSON 序列化器（payload 存取）
     */
    public PatServiceImpl(TlcPatMapper tlcPatMapper, OAuthTokenService oauthTokenService,
                          ObjectMapper objectMapper) {
        this.tlcPatMapper = tlcPatMapper;
        this.oauthTokenService = oauthTokenService;
        this.objectMapper = objectMapper;
    }

    /**
     * 签发一枚 PAT：校验参数与权限、生成明文并原样落库
     *
     * @param uid     登录用户 uid
     * @param request 签发参数
     * @return 含明文令牌的签发结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatIssueVO issue(Long uid, PatIssueRequest request) {
        Long userId = requireUid(uid);
        String name = validateName(request == null ? null : request.getName());
        long expiresIn = validateExpiresIn(request == null ? null : request.getExpiresIn());
        resolveClientId(request == null ? null : request.getClientId());
        String scope = oauthTokenService.resolveSelfIssuedScope(request == null ? null : request.getScope());
        String payload = normalizePayload(request == null ? null : request.getPayload());
        PatIssueVO vo = insertToken(userId, name, expiresIn, scope, payload);
        LogUtil.debug(PatServiceImpl.class, "[PAT] 签发: uid={}, name={}, expiresIn={}s", userId, name, expiresIn);
        return vo;
    }

    /**
     * 列出当前用户的全部 PAT（含已过期记录、含令牌明文，过期与否由 {@code expiresInSeconds} 体现）
     *
     * @param uid 用户 uid
     * @return 按签发时间倒序的令牌列表
     */
    @Override
    public List<PatVO> list(Long uid) {
        Long userId = requireUid(uid);
        List<TlcPat> rows = tlcPatMapper.selectList(Wrappers.<TlcPat>lambdaQuery()
                .eq(TlcPat::getUid, userId)
                .orderByDesc(TlcPat::getCreateTime)
                .orderByDesc(TlcPat::getId));
        return toVOList(rows);
    }

    /**
     * 按 id 撤销本人名下的一条 PAT（删除行，幂等）
     *
     * @param uid 用户 uid
     * @param id  令牌记录主键
     */
    @Override
    public void revoke(Long uid, Long id) {
        Long userId = requireUid(uid);
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "id 不能为空");
        }
        int deleted = tlcPatMapper.delete(Wrappers.<TlcPat>lambdaQuery()
                .eq(TlcPat::getId, id)
                .eq(TlcPat::getUid, userId));
        LogUtil.debug(PatServiceImpl.class, "[PAT] 撤销: uid={}, id={}, deleted={}", userId, id, deleted);
    }

    /**
     * 作废本人名下全部 PAT：按 uid 删行
     *
     * @param uid 用户 uid
     */
    @Override
    public void revokeAllByUid(Long uid) {
        Long userId = requireUid(uid);
        int deleted = tlcPatMapper.delete(Wrappers.<TlcPat>lambdaQuery().eq(TlcPat::getUid, userId));
        LogUtil.debug(PatServiceImpl.class, "[PAT] 全撤销: uid={}, deleted={}", userId, deleted);
    }

    /**
     * 应用视角列出本应用为该用户签发的 PAT
     *
     * @param uid      用户 uid（由 access_token 解析得出）
     * @param clientId 调用方应用 ID
     * @return 令牌列表（含明文）
     */
    @Override
    public List<PatVO> listByClient(Long uid, String clientId) {
        if (uid == null || !StringUtils.hasText(clientId)) {
            return Collections.emptyList();
        }
        oauthTokenService.requireDirectAuthEnabledClient(clientId);
        List<TlcPat> rows = tlcPatMapper.selectList(Wrappers.<TlcPat>lambdaQuery()
                .eq(TlcPat::getUid, uid)
                .eq(TlcPat::getClientId, clientId)
                .orderByDesc(TlcPat::getCreateTime)
                .orderByDesc(TlcPat::getId));
        return toVOList(rows);
    }

    /**
     * 应用视角按明文吊销本应用为该用户签发的一条 PAT
     *
     * @param uid      用户 uid（由 access_token 解析得出）
     * @param clientId 调用方应用 ID
     * @param token    PAT 明文
     */
    @Override
    public void revokeByClient(Long uid, String clientId, String token) {
        if (uid == null || !StringUtils.hasText(clientId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "缺少用户或客户端标识");
        }
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "token 不能为空");
        }
        oauthTokenService.requireDirectAuthEnabledClient(clientId);
        int deleted = tlcPatMapper.delete(Wrappers.<TlcPat>lambdaQuery()
                .eq(TlcPat::getToken, token.trim())
                .eq(TlcPat::getUid, uid)
                .eq(TlcPat::getClientId, clientId));
        LogUtil.debug(PatServiceImpl.class, "[PAT] 应用侧吊销: uid={}, clientId={}, deleted={}",
                uid, clientId, deleted);
    }

    /**
     * 校验 PAT 明文并解析出令牌主体（消费端 {@code /pat/**} 使用）
     *
     * <p>两代令牌都按明文精确查库：带 {@code tlc_pat_} 前缀的新令牌与不带前缀的 64 位 hex
     * 旧令牌（迁移行，{@code source=legacy}），后者没有 payload 约定，主体里 {@code prefixed=false}。
     * 其余格式直接返回 null，不产生数据库查询；命中明文且未过期才返回主体，过期与不存在不作区分。
     * 库列用 {@code utf8mb4_bin}，大小写不会被折叠。</p>
     *
     * @param raw 明文令牌
     * @return 令牌主体；格式不符、未命中或已过期时返回 null
     */
    @Override
    public PatPrincipal resolve(String raw) {
        boolean prefixed = PatUtil.isPat(raw);
        if (!prefixed && !PatUtil.isLegacyToken(raw)) {
            return null;
        }
        TlcPat row = tlcPatMapper.selectOne(Wrappers.<TlcPat>lambdaQuery()
                .eq(TlcPat::getToken, raw)
                .gt(TlcPat::getExpireTime, LocalDateTime.now()));
        if (row == null) {
            return null;
        }
        return new PatPrincipal(row.getUid(), row.getClientId(), row.getScope(),
                parsePayloadMap(row.getPayload()), prefixed);
    }

    /**
     * 落库一枚新令牌并组装签发响应（明文原样入库，后续列表也可回看）
     *
     * @param uid       用户 uid
     * @param name      名称（已校验）
     * @param expiresIn 有效期（秒，已校验）
     * @param scope     权限范围（已收敛）
     * @param payload   payload JSON 原文（可空）
     * @return 含明文令牌的签发结果
     */
    private PatIssueVO insertToken(Long uid, String name, long expiresIn, String scope, String payload) {
        String token = PatUtil.generate();
        TlcPat pat = new TlcPat();
        pat.setUid(uid);
        pat.setClientId(PERSONAL_CLIENT_ID);
        pat.setName(name);
        pat.setToken(token);
        pat.setTokenPrefix(PatUtil.displayPrefix(token));
        pat.setScope(scope);
        pat.setExpiresIn(expiresIn);
        pat.setExpireTime(LocalDateTime.now().plusSeconds(expiresIn));
        pat.setSource(SOURCE_SESSION);
        pat.setPayload(payload);
        tlcPatMapper.insert(pat);

        PatIssueVO vo = new PatIssueVO();
        vo.setId(pat.getId());
        vo.setToken(token);
        vo.setTokenPrefix(pat.getTokenPrefix());
        vo.setName(pat.getName());
        vo.setClientId(pat.getClientId());
        vo.setScope(pat.getScope());
        vo.setExpiresIn(pat.getExpiresIn());
        vo.setExpireTime(pat.getExpireTime());
        vo.setPayload(parsePayload(pat.getPayload()));
        vo.setCreatedAt(pat.getCreateTime());
        return vo;
    }

    /**
     * 实体列表转视图对象（解析 payload、计算剩余秒数）
     *
     * @param rows 令牌实体列表
     * @return 视图对象列表
     */
    private List<PatVO> toVOList(List<TlcPat> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<PatVO> result = new ArrayList<>(rows.size());
        LocalDateTime now = LocalDateTime.now();
        for (TlcPat row : rows) {
            PatVO vo = new PatVO();
            vo.setId(row.getId());
            vo.setClientId(row.getClientId());
            vo.setName(row.getName());
            vo.setToken(row.getToken());
            vo.setTokenPrefix(row.getTokenPrefix());
            vo.setScope(row.getScope());
            vo.setExpiresIn(row.getExpiresIn());
            vo.setExpireTime(row.getExpireTime());
            vo.setSource(row.getSource());
            vo.setPayload(parsePayload(row.getPayload()));
            vo.setLastUsedAt(row.getLastUsedAt());
            vo.setCreatedAt(row.getCreateTime());
            vo.setExpiresInSeconds(row.getExpireTime() == null
                    ? null
                    : Duration.between(now, row.getExpireTime()).getSeconds());
            result.add(vo);
        }
        return result;
    }

    /**
     * 校验并归一化 payload：必须是 JSON object，序列化后不超过 4KB，空对象等价于无 payload
     *
     * @param payload 原始入参（Jackson 反序列化后的任意对象）
     * @return 待落库的 JSON 原文；无 payload 时返回 null
     */
    private String normalizePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (!(payload instanceof Map<?, ?> map)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "payload 必须是 JSON 对象");
        }
        if (map.isEmpty()) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "payload 序列化后不能超过 4KB");
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "payload 序列化失败");
        }
    }

    /**
     * 把库里的 payload 原文解析为对象（解析失败按无 payload 处理，不影响列表可用性）
     *
     * @param json payload JSON 原文
     * @return 解析后的对象；空或解析失败返回 null
     */
    private Object parsePayload(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            LogUtil.warn(PatServiceImpl.class, "[PAT] payload 解析失败，按空处理: {}", json, e);
            return null;
        }
    }

    /**
     * 把库里的 payload 原文解析为 KV 对象（消费端使用）
     *
     * <p>空值与解析失败都返回空 Map：payload 是调用方自定义内容，解析不了按「未配置」处理，
     * 不应让整个请求以系统错误收场。</p>
     *
     * @param json payload JSON 原文
     * @return 解析后的 KV 对象；空或解析失败时返回空 Map
     */
    private Map<String, Object> parsePayloadMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            LogUtil.warn(PatServiceImpl.class, "[PAT] payload 解析失败，按空处理: {}", json, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 校验用户 uid
     *
     * @param uid 用户 uid
     * @return 非空 uid
     */
    private Long requireUid(Long uid) {
        if (uid == null) {
            throw new BusinessException(ResultCode.NOT_LOGIN);
        }
        return uid;
    }

    /**
     * 校验名称：去空白、非空、不超列宽
     *
     * @param name 原始名称
     * @return 去空白后的名称
     */
    private String validateName(String name) {
        String trimmed = name == null ? null : name.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "name 不能为空");
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "name 长度不能超过 " + NAME_MAX_LENGTH);
        }
        return trimmed;
    }

    /**
     * 校验有效期：必须为正且不超过上限
     *
     * @param expiresIn 有效期（秒）
     * @return 校验通过的有效期
     */
    private long validateExpiresIn(Long expiresIn) {
        if (expiresIn == null || expiresIn <= 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "expires_in 必须为大于 0 的秒数");
        }
        if (expiresIn > MAX_TTL_SECONDS) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "expires_in 不能超过 " + MAX_TTL_SECONDS + " 秒（10 年零 1 个月）");
        }
        return expiresIn;
    }

    /**
     * 校验目标客户端标识：PAT 只承载个人令牌，不接受其它 client_id
     *
     * @param requested 请求携带的 client_id（可空）
     * @return 归一化后的客户端标识
     */
    private String resolveClientId(String requested) {
        String trimmed = requested == null ? null : requested.trim();
        if (!StringUtils.hasText(trimmed)) {
            return PERSONAL_CLIENT_ID;
        }
        if (!PERSONAL_CLIENT_ID.equals(trimmed)) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "PAT 仅支持个人令牌，client_id 固定为 " + PERSONAL_CLIENT_ID);
        }
        return PERSONAL_CLIENT_ID;
    }
}
