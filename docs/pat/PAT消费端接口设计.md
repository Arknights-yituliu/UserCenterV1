# PAT 消费端接口设计

本文是《PAT 改造方案-最终版》第 5.1 节「消费端接口」的落地设计：PAT 持有者用它读取干员数据。**目标账号的选法按令牌前缀分两代**：带 `tlc_pat_` 前缀的新令牌读 payload 里的 `ak_uid`；不带前缀的旧令牌没有 payload 约定，按「用户默认来源 → 最近变更」取数，与 OAuth 版不传 `akUid` 一致。

签发 / 列表 / 撤销见《PAT 改造方案-最终版》第 3 节，本文不重复。

> **变更记录（2026-11-18）：PAT 全链路明文，不再做摘要。**
> `tlc_pat.token` 直接存令牌明文，Redis 预留键也用明文，迁移进来的旧令牌保留原样明文，
> 用户侧与应用侧列表均回传完整明文。消费端的校验因此简化为「按明文精确查一次库」，
> 不再计算 SHA-256；两代令牌的格式分流判据不变。这是明确的业务取舍，代价是库、备份、
> Redis（若启用缓存）与列表响应都成为敏感数据源，须配套库表最小权限、备份加密、TLS、
> 日志/异常/监控不落明文。同批删除了 PAT 轮换接口：明文可回看后「撤销 + 重新签发」等价，
> 且轮换 legacy 行会生成带前缀的新令牌、触发 `payload.ak_uid` 强制校验（legacy 无 payload，
> 新令牌直接 10002）。以下各节已按此口径修订。
>
> **后续调整（2026-11-18）：消费端接口改为不接收任何请求参数。** 控制器方法不再声明
> `akUid` 占位参数，也不再对「传了 `akUid`」返回 `10002`；多余查询参数由框架忽略，账号
> 完全由 PAT 决定。§3.6 错误码表、§4 契约与 §6 验证清单已同步。

## 1. 结论速览

| 项目 | 取值 |
| --- | --- |
| 路径 | `GET /pat/ak-accounts/operators` |
| 控制器 | `com.orange.controller.pat.PatAkAccountController` |
| 拦截器 | `com.orange.interceptor.PatAuthInterceptor`，注册 `"/pat/**"` |
| 凭证 | `Authorization: Bearer <pat>`，新旧两代格式都接受 |
| 账号来源 | 新令牌（`tlc_pat_` 前缀）→ `payload.ak_uid`；旧令牌（64 位 hex，无前缀）→ 默认来源/最近变更 |
| 归属校验 | 新令牌走 `ak_account_binding`；旧令牌经默认来源链路，数据同样只来自该用户自己的条目 |
| 所需 scope | `ak-data.read`（`gama-data.read`、`all` 可覆盖） |
| 响应 | 与 `/oauth2/ak-accounts/operators` 逐字段一致 + `Cache-Control: private, no-cache` |

## 2. 路径与包

一级路径在本项目表示认证域：`/user/**` 会话、`/oauth2/**` access_token、`/open/**` 完全公开、`/internal/**` 不对外。PAT 是第三种凭证，独占 `/pat/**`，控制器包同步为 `controller/pat`。

三个被否决的方案：

| 备选 | 问题 |
| --- | --- |
| `/oauth2/ak-accounts/pat-operators` | `addPathPatterns("/oauth2/ak-accounts/**")` 会先被 `OAuthAuthInterceptor` 截获；它查的是 `uc:oauth:access:{token}`，PAT 必然查不到，请求在进入控制器前就返回 `80001`。 |
| `/oauth2/pat/ak-accounts/operators` | `/oauth2/pat/**` 是管理面（凭证是 access_token + `direct_auth_enabled=1` 准入），与消费面共用前缀会让「这个前缀认哪种凭证」失去唯一答案。 |
| `/open/**` | 该前缀语义是「完全公开、不鉴权」，放鉴权接口进去等于埋一个鉴权绕过。 |

## 3. 凭证与账号定位

### 3.1 两代令牌分流

`tlc_pat` 里同时存在两种来源的行，靠明文是否带前缀区分：

| 令牌 | 形态 | `source` | payload | 选账号规则 |
| --- | --- | --- | --- | --- |
| 新令牌 | `tlc_pat_` + 64 位小写 hex（72 字符） | `session` | 签发时写入 | 读 `payload.ak_uid`，必须存在，否则 `10002` |
| 旧令牌 | 64 位小写 hex（无前缀） | `legacy` | 迁移时固定为 `null` | 不指定账号，走 `listDefaultOperators` |

分流判据是**明文格式**而不是库里的 `source`：`PatUtil.isPat(raw)` 判新令牌，`PatUtil.isLegacyToken(raw)` 判旧令牌，两者都不匹配直接 `80001` 且不查库。结果通过 `PatPrincipal#isPrefixed()` 传到控制器，控制器据此决定是否读 payload。

这样处理的原因：旧令牌迁移前由 `SignUtil.generateToken()` 生成（`POST /user/oauth/custom-tokens`、`POST /oauth2/token/from-refresh` 走的就是这条），本来就是无前缀的 64 位 hex；迁移只换了存储位置，没有换明文。要求它们补 payload 是不现实的——它们已经发出去好几年了。全链路明文取舍下，旧令牌明文原样写入 `tlc_pat.token`，消费端按同一串明文即可查到，无需任何换算。

### 3.2 新令牌：ak_uid 放在 payload，是方案的一处例外

方案 §2.1 定义 payload 为「调用方自定义 KV，服务端不解读键含义、不做键名白名单、不据其分支」。本接口打破该约定：`payload.ak_uid` 是**唯一被服务端解读的键**，并直接决定读取哪个账号。

三点注意：

- 方案文档 §2.1 需补一句例外说明，否则后续有人按「payload 不解读」优化字段时会直接打断本接口。
- `ak_uid` 必须在**签发时**确定：`POST /user/pat` 的 `payload` 里写入，之后没有修改接口，改了只能撤销后重新签发（轮换接口已删除）。
- 长期看，如果 `/pat/**` 还要支持终末地等第二类资源，payload 会变成「每个消费端各占一个键」的事实配置表。到那一步应改为独立列（`tlc_pat.ak_uid`），而不是继续往 payload 里加键。

### 3.3 解析链路

```
Authorization: Bearer <token>
  → RequestUtil.resolveToken            // 复用现有头解析，兼容 UC-Token
  → PatUtil.isPat / isLegacyToken       // 两代格式之一，否则 80001，不查库
  → 不计算摘要（全链路明文，业务取舍）
  → SELECT * FROM tlc_pat WHERE token = ? AND expire_time > NOW()
  → 身份三元组写入 UserContext（uid / clientId / scope）
  → 整个 PatPrincipal 写入 PatContext（payload、prefixed、以及以后的扩展字段）
```

默认**不加缓存**。`RedisKeyUtil` 里的 `tlc:pat:{token}` 是方案 §2.2 的按需缓存，目前没有任何写入点；payload 本来就在 `tlc_pat` 行里，一次查询即可拿到，不需要为拿 payload 额外读 Redis。启用缓存反而要处理撤销后的失效。

令牌无效、格式不符、已过期统一返回 `80001`，不区分原因。

### 3.4 payload 取值口径（仅新令牌）

`PatPayloadUtil.requireAkUid` 是唯一的取值入口，只在 `patPrefixed=true` 时调用：

| 情况 | 结果 |
| --- | --- |
| payload 为 null 或无 `ak_uid` 键 | `10002` PAT 未配置 ak_uid |
| 值为字符串 | 去首尾空白后使用 |
| 值为整数（Integer/Long/Short/BigInteger） | 转十进制字符串，兼容 `{"ak_uid": 123456789}` |
| 值为空白字符串 | `10002` PAT 的 ak_uid 不能为空 |
| 值为布尔、浮点、对象、数组 | `10002` PAT 的 ak_uid 必须为字符串或整数 |

**绝不放行空值**：业务层把「akUid 为空」解释为「读用户设置的默认来源」，空串一旦传下去，请求会静默读到另一个账号——旧令牌走这条回落是有意的，新令牌因为没有配置而走同一条回落则是越权，所以取值层就把它堵死。

### 3.5 归属校验

**新令牌**：payload 由调用方自己填，可以填任意字符串，因此必须校验账号归属：

```java
akAccountService.listOperators(uid, payloadAkUid, AkAccountVO.SOURCE_SKLAND)
```

显式传 `source=skland` 是关键。业务层在 `akUid` 等于本人 `uid` 时会先判断「是否读手动版」，传了 `skland` 就跳过这条撞号分支，强制走 `requireBinding`：

```sql
SELECT COUNT(1) FROM ak_account_binding WHERE ak_uid = ? AND owner_uid = ?
```

为 0 抛 `80008`，与「账号不存在」同一个响应。因此一枚新 PAT 的可读范围是：**签发它的用户 + 签发时 payload 指定的账号 + 该账号仍在该用户名下**。用户解绑后，同一枚 PAT 立即读不到数据，不需要重新签发。

**旧令牌**：不指定账号，走 `listOperators(uid, null, null)` → `listDefaultOperators`，账号从该用户自己的账号/手动条目列表里选，本身就不存在跨用户读取的可能。排序规则由 `listBoundAccounts` 保证：默认来源在前，其余按最近变更倒序，与 OAuth 版同源同码。

### 3.6 错误码

| 场景 | `code` |
| --- | --- |
| 未带令牌 / 两代格式都不匹配 / 无效 / 已过期 | `80001` |
| PAT scope 不含 `ak-data.read` | `80008` |
| 新令牌的 payload 未配置 `ak_uid` 或取值非法 | `10002` |
| 请求带了 `akUid` 等查询参数 | 参数被忽略（接口不声明参数），仍按 PAT 取数 |
| 新令牌的 `ak_uid` 不属于当前用户 | `80008` |
| `ak_uid` 格式非法（非 32 位可见 ASCII） | `10002` |
| 旧令牌的用户没有任何可用数据来源 | `10002`（与 OAuth 不传 `akUid` 一致） |

## 4. 接口契约

```http
GET /pat/ak-accounts/operators
Authorization: Bearer <pat>
```

**无查询参数。** 控制器方法不声明任何参数，账号完全由 PAT 决定；调用方多传的 `akUid` 等查询参数由框架忽略，不参与取数，也不会报错。取舍是「接口契约上根本没有参数」替代「运行时拦截参数」，代价是误传参数时不会报错——因此接入方必须明确：不要依赖请求参数指定账号，以 PAT 的 payload 为准。

两种令牌的行为差异只有一处：

| 令牌 | 读取的账号 |
| --- | --- |
| `tlc_pat_...`（新） | `payload.ak_uid` 指定的账号 |
| 纯 64 位 hex（旧） | 该用户默认来源；未设置或已失效取最近变更的条目 |

成功响应（`200`，`Cache-Control: private, no-cache`）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "akUid": "123456789",
    "items": [
      {
        "id": "char_002_amiya",
        "recordId": 90001,
        "owned": 1,
        "rarity": 5,
        "level": 80,
        "evolvePhase": 2,
        "mainSkillLevel": 7,
        "skill1": 7,
        "skill2": 10,
        "skill3": 0,
        "equipX": 3,
        "equipY": 0,
        "equipD": 0,
        "equipA": 0,
        "equipB": 0,
        "potentialRank": 2
      }
    ]
  }
}
```

字段语义与《OAuth 游戏数据接口文档》3.2 节完全一致：共用 `OperatorListVO`，只返回 `owned=1`，数值列不返回 `null`，无分页。

与 OAuth 版的差异：

| 维度 | `/oauth2/ak-accounts/operators` | `/pat/ak-accounts/operators` |
| --- | --- | --- |
| 凭证 | access_token（Redis，默认 2 小时） | PAT（库内明文，签发时定有效期） |
| 账号来源 | 查询参数 `akUid`，可读本人任一绑定账号 | 新令牌：payload 固定一个账号；旧令牌：默认来源 |
| 手动版数据 | 支持（`source=manual`） | 新令牌固定读自动导入；旧令牌经账号列表可能落到手动条目 |
| scope 来源 | 授权时确定 | 签发时快照 |

> 需要读多个账号的调用方，应签发多枚 payload 不同的 PAT，而不是给接口加参数。PAT 的定位是「一把令牌绑一个用途」。

## 5. 实现落点

| 文件 | 职责 |
| --- | --- |
| `common/util/PatUtil` | `generate` / `isPat` / `isLegacyToken` / `displayPrefix`，两代令牌生成与格式判定（不再提供 `hash`） |
| `common/util/PatPayloadUtil` | 从 payload 取 `ak_uid`，取值口径与 10002 报错 |
| `interceptor/PatAuthInterceptor` | 解析 PAT，写 `UserContext`（身份）与 `PatContext`（令牌属性），请求结束两者都清理 |
| `interceptor/WebConfig` | 注册 `"/pat/**"` |
| `controller/pat/PatAkAccountController` | 取 scope、从 `PatContext` 取 `prefixed` 决定选账号规则 |
| `service/PatService#resolve` | 按明文查库，返回 `PatPrincipal` |
| `service/PatPrincipal` | 令牌属性载体：uid / clientId / scope / payload / prefixed |
| `common/context/PatContext` | PAT 专属请求上下文，存整个 `PatPrincipal` |
| `common/context/UserContext` | 只保留三个认证面共有的 uid / clientId / scope |

**为什么分两个上下文**：`UserContext` 是会话、access_token、PAT 三条链路共用的身份容器，加 PAT 专属字段会让每个非 PAT 请求都携带空值，也会让业务层误以为 `getPayload()` 是通用能力。PAT 的属性随令牌增长，收进 `PatPrincipal` 后，新增字段只改「`tlc_pat` 加列 → `TlcPat` 加属性 → `PatPrincipal` 加属性」，`UserContext` 与清理逻辑都不用动。清理点只有 `PatAuthInterceptor.afterCompletion` 一处，两行 `clear()`。

业务层零改动：`AkAccountService#listOperators(Long, String, String)` 原样复用，绑定校验、默认来源、`owned` 过滤、数值补零都在原有实现里。

## 6. 验证清单

自动化覆盖：`PatAkAccountMvcTest`（10 例，两代令牌分流、拦截器注册、payload 取值、多余参数不影响取数、scope 与归属失败）、`PatPayloadUtilTest`（5 例）、`PatUtilTest`（6 例，移除摘要一致性用例）、`PatContextTest`（3 例，上下文存取与清理）、`PatServiceImplTest`（19 例，含 5 个 `resolve` 用例、明文原样落库、列表回传明文）。

| 场景 | 期望 |
| --- | --- |
| 不带凭证访问 | `80001`，`msg` 为「缺少 PAT」 |
| 带非两代格式的随机串 | `80001`，且不产生数据库查询 |
| 带已过期 / 已撤销的新令牌 | `80001` |
| 签发时未写 payload | `10002` PAT 未配置 ak_uid |
| payload 的 `ak_uid` 为空串或空白 | `10002`，**不得**回落到默认来源 |
| payload 的 `ak_uid` 为整数 JSON | 正常读取，响应 `akUid` 为十进制字符串 |
| payload 的 `ak_uid` 是他人账号 | `80008`，与账号不存在无法区分 |
| 用户解绑该账号后再用同一新令牌读 | `80008` |
| 请求带 `?akUid=` | `200`，参数被忽略，仍按 PAT 的 payload 取数 |
| scope 只有 `ak-data.write` | `80008` |
| scope 为 `gama-data.read` | `200` |
| 带已迁移的旧令牌（无前缀） | `200`，按默认来源/最近变更取账号 |
| 旧令牌库里残留 payload | 不参与选账号，仍按默认来源 |
| 旧令牌的用户无任何数据来源 | `10002` |
| 无前缀但库里查不到明文的 64 位 hex | `80001` |
| 新令牌正常读取 | 与 OAuth 版同一账号的响应逐字段一致 |

最后一条建议做成自动化对比断言：两个入口共用同一个 service，响应一旦出现差异，说明有人在其中一条链路上加了分叉。

## 7. 与改造方案的衔接

- **本接口是方案 §5.1 所说的消费端**，也是 §7「PAT 暂无消费方」的闭合点；接口已落地，PAT 现可正常用于取数。面向调用方的完整契约见 `docs/pat/PAT接口文档.md`。
- **旧令牌先于迁移可用**：迁移前它们靠 `uc:oauth:access:` 在 `/oauth2/**` 生效；迁移把 Redis key 删掉后，本接口按明文查 `tlc_pat` 仍能识别它们，因此「迁移即失效」的窗口不存在。前提是本接口与迁移同批上线。
- **阶段二自此具备条件**：5.3 的迁移、5.4 的对账、旧 6 端点的 410 下线可按批次排期。
- **payload 语义例外需回写方案 §2.1**：`ak_uid` 是唯一被解读的键，其余键仍按「不解读」处理。
- **`last_used_at` 仍未回写**：校验路径已存在，但每请求一次写库会放大写压力，建议按「距上次使用超过 5 分钟才更新」另起一批做。
- **不改动的部分**：`OAuthTokenResolver`、`OAuthAuthInterceptor`、`WebConfig` 中既有的 `/oauth2/**` 注册、全部 `/oauth2/**` 资源端点。
