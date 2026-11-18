package com.orange.service;

import com.orange.entity.dto.pat.PatIssueRequest;
import com.orange.entity.vo.pat.PatIssueVO;
import com.orange.entity.vo.pat.PatVO;

import java.util.List;

/**
 * 个人访问令牌（PAT）服务
 *
 * <p>PAT 有两种使用方式，互不影响：管理面（签发 / 列表 / 撤销）把 PAT 当作
 * 「被管理的对象」，由会话或 OAuth access_token 的持有者调用；消费面（{@code /pat/**}）
 * 把 PAT 当作请求凭证，由 {@link #resolve(String)} 解析。{@code /oauth2/**} 链路始终不认 PAT。</p>
 *
 * <p>语义基线：撤销 = 删行；{@code scope} 是签发时快照、只能收敛；{@code payload} 默认不解读，
 * 唯一例外是消费端约定的 {@code ak_uid}；{@code last_used_at} 消费端暂不回写；
 * <strong>全链路明文</strong>——不做摘要，库、Redis 预留键与列表接口都用令牌明文本身，
 * 这是明确的业务取舍，配套要求是日志/异常不落明文与存储侧最小权限。</p>
 *
 * @author UserCenter
 */
public interface PatService {

    /** 个人令牌固定使用的客户端标识（与 OAuth 个人令牌口径一致） */
    String PERSONAL_CLIENT_ID = "this_land_cloud";

    /**
     * 签发一枚 PAT
     *
     * <p>{@code name}、{@code expires_in} 必填且有效期有上限；{@code scope} 只能在系统可自助
     * 范围内收敛（元范围 {@code all} 不可自助签发）；{@code payload} 必须是 JSON object 且
     * 序列化后 ≤ 4KB。明文原样入库，后续列表接口仍可回看。</p>
     *
     * @param uid     登录用户 uid
     * @param request 签发参数
     * @return 含明文令牌的签发结果
     */
    PatIssueVO issue(Long uid, PatIssueRequest request);

    /**
     * 列出当前用户的全部 PAT（含展示前缀与令牌明文）
     *
     * @param uid 用户 uid
     * @return 按签发时间倒序的令牌列表
     */
    List<PatVO> list(Long uid);

    /**
     * 按 id 撤销本人名下的一条 PAT（删除行）
     *
     * <p>幂等：id 不存在或不属于本人时视为成功，不抛异常，也不会误删他人记录。</p>
     *
     * @param uid 用户 uid
     * @param id  令牌记录主键
     */
    void revoke(Long uid, Long id);

    /**
     * 作废本人名下全部 PAT（用户自助）
     *
     * <p>只由 {@code POST /user/pat/revoke-all} 调用。账号侧安全事件（改密、删应用）
     * 不联动 PAT，见方案第 6 节。</p>
     *
     * @param uid 用户 uid
     */
    void revokeAllByUid(Long uid);

    /**
     * 应用视角列出本应用为该用户签发的 PAT
     *
     * <p>准入：客户端必须启用、已审批且已开通直连认证能力（{@code direct_auth_enabled=1}），
     * 未开通抛 90014。强制按 {@code uid + clientId} 过滤，调用方读不到其它应用名下的记录。</p>
     *
     * @param uid      用户 uid（由 access_token 解析得出）
     * @param clientId 调用方应用 ID（由 access_token 解析得出）
     * @return 令牌列表（含明文）
     */
    List<PatVO> listByClient(Long uid, String clientId);

    /**
     * 应用视角按明文吊销本应用为该用户签发的一条 PAT
     *
     * <p>调用方手里只有令牌本身，因此按明文提交、服务端按
     * {@code uid + client_id + token} 三条件精确删除，避免跨用户、跨应用越权吊销。
     * 准入同 {@link #listByClient}；幂等，删不到也视为成功。</p>
     *
     * @param uid      用户 uid（由 access_token 解析得出）
     * @param clientId 调用方应用 ID（由 access_token 解析得出）
     * @param token    PAT 明文
     */
    void revokeByClient(Long uid, String clientId, String token);

    /**
     * 校验 PAT 明文并解析出令牌主体（仅消费端 {@code /pat/**} 使用）
     *
     * <p>接受两代令牌：带 {@code tlc_pat_} 前缀的新令牌，以及迁移进来的、不带前缀的
     * 64 位小写 hex 旧令牌。两者的区别通过 {@link PatPrincipal#isPrefixed()} 传给消费端。</p>
     *
     * <p>按令牌明文单次查库，命中且未过期才返回；格式不符、未命中、已过期一律返回
     * {@code null}，由调用方统一转成 80001，不区分具体原因、不暴露令牌状态。</p>
     *
     * <p>默认不加缓存：PAT 调用量远低于普通接口，直接读库即可；{@code tlc:pat:*} 系列键
     * 是为将来出现性能压力准备的，启用后必须同步处理撤销后的失效。</p>
     *
     * @param raw 明文令牌
     * @return 令牌主体（含 payload 解析结果）；无效或已过期时返回 null
     */
    PatPrincipal resolve(String raw);
}
