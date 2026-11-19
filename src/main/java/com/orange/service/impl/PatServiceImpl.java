package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.PatUtil;
import com.orange.entity.dto.pat.PatIssueByRefreshRequest;
import com.orange.entity.dto.pat.PatIssueRequest;
import com.orange.entity.dto.pat.PatPayload;
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

/**
 * 个人访问令牌服务实现
 *
 * <p>管理面：用户自助签发 / 应用侧凭 refresh_token 签发 / 列表 / 撤销 / 全撤销 +
 * 应用视角列表与吊销；消费面：
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
        PatIssueVO vo = insertToken(userId, name, expiresIn, scope,
                request == null ? null : request.getPayload());
        LogUtil.debug(PatServiceImpl.class, "[PAT] 签发: uid={}, name={}, expiresIn={}s", userId, name, expiresIn);
        return vo;
    }

    /**
     * 应用侧凭 refresh_token 签发一枚 PAT：校验凭证与准入后按凭证归属落库
     *
     * <p>refresh_token 只作凭证，不消费、不轮转；uid/client_id 全部取自凭证记录，杜绝跨应用冒签。
     * 准入与 {@code /oauth2/pat/list}、{@code /oauth2/pat/revoke} 对齐（{@code direct_auth_enabled=1}），
     * 权限上限为「凭证范围 ∩ 可自助范围」。日志只记元数据，绝不落令牌明文。</p>
     *
     * @param request 签发参数（含 refresh_token）
     * @return 含明文令牌的签发结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PatIssueVO issueByRefreshToken(PatIssueByRefreshRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请求参数不能为空");
        }
        OAuthTokenService.RefreshTokenPrincipal principal =
                oauthTokenService.resolveRefreshToken(request.getRefreshToken());
        oauthTokenService.requireDirectAuthEnabledClient(principal.getClientId());
        String name = validateName(request.getName());
        long expiresIn = validateExpiresIn(request.getExpiresIn());
        String scope = oauthTokenService.resolveSelfIssuedScopeWithin(
                principal.getScope(), request.getScope());
        PatIssueVO vo = insertToken(principal.getUid(), principal.getClientId(),
                name, expiresIn, scope, request.getPayload());
        LogUtil.debug(PatServiceImpl.class,
                "[PAT] 应用侧签发: uid={}, clientId={}, name={}, expiresIn={}s",
                principal.getUid(), principal.getClientId(), name, expiresIn);
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
     * 直连应用查询当前用户的全部 PAT（跨应用）
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
        return list(uid);
    }

    /**
     * 应用视角按明文吊销本应用为该用户签发的一条 PAT
     *
     * <p>入参 token 由入口层（控制器 / {@code RequestUtil}）完成归一化，本方法只做
     * 精确匹配，不再自行 trim，避免同一份凭证在不同层被重复归一化。</p>
     *
     * @param uid      用户 uid（由 access_token 解析得出）
     * @param clientId 调用方应用 ID
     * @param token    PAT 明文（已归一化）
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
                .eq(TlcPat::getToken, token)
                .eq(TlcPat::getUid, uid)
                .eq(TlcPat::getClientId, clientId));
        LogUtil.debug(PatServiceImpl.class, "[PAT] 应用侧吊销: uid={}, clientId={}, deleted={}",
                uid, clientId, deleted);
    }

    /**
     * 校验 PAT 明文并解析出令牌主体（消费端 {@code /pat/**} 使用）
     *
     * <p>两代令牌都按明文精确查库：带 {@code tlc_pat_} 前缀的新令牌与不带前缀的 64 位 hex
     * 旧令牌（由 {@code oauth_custom_token} 迁移而来），后者没有 payload 约定，主体里 {@code prefixed=false}。
     * 其余格式直接返回 null，不产生数据库查询；命中明文且未过期才返回主体，过期与不存在不作区分。
     * 库列用 {@code utf8mb4_bin}，大小写不会被折叠。</p>
     *
     * <p>入参由 {@code RequestUtil.resolveToken} 完成归一化（剥离 Bearer 前缀与首尾空白），
     * 本方法只做精确匹配，不重复 trim。</p>
     *
     * @param raw 明文令牌（已归一化）
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
                parsePayloadOrEmpty(row.getPayload()), prefixed);
    }

    /**
     * 用户自助签发入口：client_id 固定为个人令牌标识，其余落库逻辑与应用侧签发共用
     *
     * @param uid       用户 uid
     * @param name      名称（已校验）
     * @param expiresIn 有效期（秒，已校验）
     * @param scope     权限范围（已收敛）
     * @param payload   自定义 payload 对象（可空，允许全空或部分为空）
     * @return 含明文令牌的签发结果
     */
    private PatIssueVO insertToken(Long uid, String name, long expiresIn, String scope,
                                   PatPayload payload) {
        return insertToken(uid, PERSONAL_CLIENT_ID, name, expiresIn, scope, payload);
    }

    /**
     * 落库一枚新令牌并组装签发响应（明文原样入库，后续列表也可回看）
     *
     * @param uid       用户 uid
     * @param clientId  归属应用（用户自助签发为 this_land_cloud，应用侧签发为 refresh_token 所属应用）
     * @param name      名称（已校验）
     * @param expiresIn 有效期（秒，已校验）
     * @param scope     权限范围（已收敛）
     * @param payload   自定义 payload 对象（可空，允许全空或部分为空）；落库时序列化为 JSON 文本
     * @return 含明文令牌的签发结果
     */
    private PatIssueVO insertToken(Long uid, String clientId, String name, long expiresIn,
                                   String scope, PatPayload payload) {
        String token = PatUtil.generate();
        String payloadJson = normalizePayload(payload);
        TlcPat pat = new TlcPat();
        pat.setUid(uid);
        pat.setClientId(clientId);
        pat.setName(name);
        pat.setToken(token);
        pat.setTokenPrefix(PatUtil.displayPrefix(token));
        pat.setScope(scope);
        pat.setExpiresIn(expiresIn);
        pat.setExpireTime(LocalDateTime.now().plusSeconds(expiresIn));
        pat.setPayload(payloadJson);
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
        vo.setPayload(payloadJson == null ? null : payload);
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
     * 把自定义 payload 实体序列化为待落库的 JSON 文本
     *
     * <p>实体类型已由 DTO 保证，无需再校验「是不是 JSON object」；全空对象（所有字段为 null）
     * 等价于不传 payload，落库为 {@code NULL}，与改造前 {@code {}} 的口径一致。
     * 序列化上限仍保留，避免单个字段写超长文本撑爆 {@code TEXT} 列。</p>
     *
     * @param payload 自定义 payload 对象（可空）
     * @return 待落库的 JSON 原文；无有效字段时返回 null
     */
    private String normalizePayload(PatPayload payload) {
        if (payload == null || payload.isEmpty()) {
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
     * 把库里的 JSON 文本转为自定义 payload 实体（解析失败按无 payload 处理）
     *
     * <p>{@link PatPayload} 带 {@code @JsonIgnoreProperties(ignoreUnknown = true)}：历史行里
     * 的任意自定义键、或更新版本写入的新键都会被静默忽略，本类已定义但 JSON 里缺失的字段
     * 保持 {@code null}。因此读到旧数据不会因字段对不上而报错，也不影响列表可用性。</p>
     *
     * @param json payload JSON 原文
     * @return 解析后的实体；空、全空或解析失败返回 null
     */
    private PatPayload parsePayload(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            PatPayload payload = objectMapper.readValue(json, PatPayload.class);
            return payload == null || payload.isEmpty() ? null : payload;
        } catch (JsonProcessingException e) {
            LogUtil.warn(PatServiceImpl.class, "[PAT] payload 解析失败，按空处理: {}", json, e);
            return null;
        }
    }

    /**
     * 把库里的 JSON 文本转为自定义 payload 实体（消费端使用，恒不返回 null）
     *
     * <p>空值与解析失败都返回空实体：payload 是调用方自定义内容，解析不了按「未配置」处理，
     * 由 {@link com.orange.common.util.PatPayloadUtil} 在取账号时报 10002，
     * 不应让整个请求以系统错误收场。</p>
     *
     * @param json payload JSON 原文
     * @return 解析后的实体；空或解析失败时返回全空实体
     */
    private PatPayload parsePayloadOrEmpty(String json) {
        PatPayload payload = parsePayload(json);
        return payload == null ? new PatPayload() : payload;
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
