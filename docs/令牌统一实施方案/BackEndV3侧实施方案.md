# BackEndV3 与 UC 令牌统一方案 —— BackEndV3 侧实施方案

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.0 |
| 状态 | 已定稿，可进入实施 |
| 本文范围 | **BackEndV3**：业务后端（外部仓库，包名 `com.lhs`），本仓库不含其源码 |
| 目标读者 | BackEndV3 侧开发、联调、运维 |
| 上游文档 | `.yama/统一令牌改造计划.md`（其目标已被本方案改写，需按本文第 11.2 节回馈项同步修订） |
| 兄弟文档 | `./UC侧实施方案.md`、`./前端实施方案.md` |

> 本文基于上游文档 `统一令牌改造计划.md` 的既有结论推导，实施前需在 BackEndV3 侧逐条核对（尤其是第 5 节各条的文件位置）。

---

## 1. 背景与结论

### 1.1 原始设想

> 在 BackEndV3 与酸橙云都放一个密钥，永远同步 BackEndV3 的 token 到酸橙云充当 refresh_token，目标在半天内全部迁移完 5 万个用户登录态。

### 1.2 结论

方向可行，但形式改为「按需兑换」，且不需要同步任何 token。

| 设想的组成部分 | 结论 |
|---|---|
| 两边共用一个密钥 | 不采用共享密钥。两台服务器跨公网，共享密钥任一侧泄漏即可伪造任意用户请求。已定：Ed25519 非对称签名，BackEndV3 持私钥、UC 只存公钥 |
| 把 BackEndV3 的 token 同步过去当 refresh_token | 技术上成立，但不该这么做：需要全量导出 5 万条 token→uid 映射、逐条对账、长期增量同步；且旧 token 会被升格为 UC 长期凭证，登出与封禁语义纠缠 |
| 半天内迁完 5 万登录态 | 可以达成，但口径须改为「半天内上线迁移通道，且不强制任何人重登」。登录态迁移在本质上是响应式的（见第 10 节），通道上线即生效，活跃用户几分钟内切换完成，长尾用户随回访自动完成 |

### 1.3 一句话方案（BackEndV3 要做什么）

BackEndV3 本身持有「自签 token → uid」的映射。用户带着自签 token 请求时，BackEndV3 现场用 Ed25519 签名向 UC 换取一对全新的 UC 令牌，把 access_token 回带给前端（供前端调用 UC 接口），refresh_token 留在服务端。不导出数据、不对账、不改 UC 表结构，前端凭据也无需更换。

**BackEndV3 在其中的角色**：兑换的唯一执行方（**触发点在 前端**，见 4.1 / 第 6 节）——负责完成兑换、缓存兑换结果、代持 refresh_token、代做刷新、把 access_token 经响应体返回，并在登出时执行双撤。

---

## 2. 术语

| 术语 | 含义 |
|---|---|
| UC | UserCenterV1，OAuth2 授权服务器（本仓库） |
| BackEndV3 | 业务后端（外部仓库） |
| 自签 token | BackEndV3 登录令牌，`AES("{id:X}.X.timestamp")` 自签形态，TTL 90 天，Redis 映射 `loginToken:{token} → uid` |
| 迁移兑换 | BackEndV3 凭 uid 现场向 UC 换取一对全新 UC 令牌的动作 |
| 双撤 | 登出 / 封禁时必须同时撤销 UC 授权与本地 `loginToken` |

---

## 3. 权威边界与鉴权路径声明

### 3.1 权威边界

| 事项 | 权威方 | 说明 |
|---|---|---|
| 会话有效性（这台设备是否仍登录） | BackEndV3 自签 token | 长期常态，非过渡期约束 |
| 授权范围（该 uid 拥有哪些权限） | UC | 由 UC 签发与撤销 |
| 登出 / 封禁 | 双方 | 必须双撤，缺一即为安全漏洞（R14） |

### 3.2 鉴权路径声明（BackEndV3 侧，全程不变）

- BackEndV3 的请求鉴权**始终**依据本地 Redis `loginToken:{token} → uid`（命中即通过），**不调用 UC、不校验 UC 令牌**。
- UC access_token 只用于前端调用 UC 接口，**不参与 BackEndV3 的任何鉴权判断**。
- 由此 BackEndV3 不依赖 UC 的可用性：UC 宕机或两端网络中断时，BackEndV3 的业务与用户登录态不受影响（对应用户侧表现见第 4.1 节链路图第二段）。
- 这是本轮方案与上游计划差异最大的一点：上游要求 BackEndV3 改为校验 UC 令牌（上游 §5.2 B5/B6/B14），**本次不做**（见 11.2 第 3、4 条）。
- 直接推论：UC 侧 introspection 端点（RFC 7662）**本次不实现**——它唯一的用途是供 BackEndV3 校验 UC 令牌，该调用方已不存在。

---

## 4. 目标架构

### 4.1 整体链路

```
【存量用户首次进入（迁移发生在这里，由前端主动触发）】
前端：应用启动 / 进入业务页 → 读 localStorage 的 UC_ACCESS_TOKEN
      存在   → 直接使用，不请求兑换
      不存在 → 主动触发一次兑换（前端唯一新增的触发点）
前端 ──Authorization: Bearer <自签 token>──▶ BackEndV3 POST /auth/uc-token/issue
BackEndV3：
  ① 查本地 Redis loginToken:{token} → uid（现有鉴权逻辑，零改动）
  ② 查 uc:migrate:issued:{uid} 是否已兑换过（uid 维度，与 UC 侧幂等模型对齐，见 B4）
       命中   → 直接复用缓存中的 UC 令牌，不重复调用 UC
       未命中 → 调 UC POST /oauth2/internal/migrate-token（Ed25519 签名 + IP 白名单）
                UC：校验签名/时效/来源 → 校验用户状态 → 撤销上一轮迁移凭证 → 签发全新 UC 令牌对
                ◀── access_token + refresh_token（仅服务端间传输）
                refresh_token 加密落库；写 uc:migrate:issued:{uid} 缓存
  ③ 把 UC access_token 放进接口响应体返回（refresh_token 不下发浏览器）
前端：将 UC access_token 写入 localStorage（key：UC_ACCESS_TOKEN）
      —— 调 BackEndV3 仍用自签 token，用户全程无感

【此后：两套令牌各司其职】
前端 ──Bearer <自签 token>──▶ BackEndV3 → 拦截器只查本地 loginToken → uid
       （不经 UC 校验、不触发兑换，UC 挂机不影响 BackEndV3 业务）
前端 ──Bearer <UC access_token>──▶ UC 接口（/oauth2/userinfo、干员数据等）

【UC access_token 过期（2h）】
前端 ──Bearer <UC access_token>──▶ UC 接口 → 401
前端 ──POST /auth/token/refresh（带自签 token）──▶ BackEndV3
BackEndV3：查 loginToken:{token} → uid → 取服务端 refresh_token → UC /oauth2/token
           ◀── 新 UC access_token → 接口响应体返回
           （refresh_token 不轮换，本地记录无需更新）
前端：更新 localStorage 的 UC_ACCESS_TOKEN
刷新失败（如 90009）→ 前端删除本地 UC_ACCESS_TOKEN，下次启动重新触发兑换自愈

【登出】BackEndV3 → 删 loginToken + UC /oauth2/revoke（双撤，见 R14）
前端：清除本地存储（自签 token + UC_ACCESS_TOKEN）
```

### 4.2 时序要点（BackEndV3 侧）

| 时刻 | BackEndV3 动作 | 不得做的事 |
|---|---|---|
| 普通业务请求 | 拦截器只查本地 `loginToken`，命中即放行 | 不要在拦截器里触发兑换或与 UC 交互（自动兑换已决策移除，见 B2） |
| 收到 `POST /auth/uc-token/issue` | 查 `loginToken` → uid；按 uid 查兑换缓存，未命中才签名调用 UC | 不要导出 / 同步任何 token；不要改动鉴权逻辑 |
| 兑换成功 | refresh_token 加密落库，写 `uc:migrate:issued:{uid}`，令牌放响应体返回 | 不要把 refresh_token 下发浏览器 |
| 兑换接口被重复调用 | 直接命中缓存分支 | 不要重复调用 UC（会击穿 UC 侧 5/分钟限流） |
| UC access 过期 | 按请求携带的自签 token 反查 uid → 取服务端 refresh_token → 调 UC 刷新 | 不要把 UC access_token 当刷新凭据 |
| 登出 / 封禁 | 删 `loginToken` + 调 UC `/oauth2/revoke` | 不要只撤一侧 |

---

## 5. BackEndV3 侧改动清单

> 本仓库不含 BackEndV3 源码，以下基于上游文档 `统一令牌改造计划.md` 的既有结论推导，实施前需在 BackEndV3 侧逐条核对。

| # | 事项 | 位置（上游文档所述） | 说明 |
|---|---|---|---|
| B1 | 配置 Ed25519 私钥与 UC 地址 | `application-test.yml` + 环境变量 | 签名私钥（仅本侧持有）与 `kid`；UC 的 HTTPS 地址（`user-center.oauth.migrate.base-url`） |
| B2 | 拦截器保持纯鉴权（**移除原计划的自动兑换分支**） | `src/main/java/com/lhs/interceptor/UserInterceptor.java` | 命中 `loginToken:{token}` 即放行，不查 UC、不触发兑换。兑换改为由前端主动调用 B5 接口触发；鉴权路径本身完全不变（仍查本地 `loginToken`，不校验 UC 令牌） |
| B3 | 新增迁移客户端 | 新增 `UcMigrateClient` | 组装 canonical string（见 7.2），用 Ed25519 私钥签名（带 `kid` / `ts` / `nonce`）；HTTPS 调用；超时与重试要短（见 R3） |
| B4 | 兑换结果缓存 | 兑换接口（B5）+ Redis | 兑换成功写 `uc:migrate:issued:{uid}` → UC 令牌对。**key 必须是 uid 维度，不要用 `sha256(自签 token)`**：UC 侧幂等模型是 `(uid, client_id)` 上恒一条迁移凭证（见 7.3），每次兑换都会无条件撤销上一轮凭证；缓存若按自签 token 分片，同一用户多设备会各兑换一次并互相撤销，旧设备刷新时拿到 90009 掉登录（见 R17）。uid 维度下同一用户全程只兑换一次，缓存命中直接复用，避免重复兑换击穿 UC 侧 5/分钟限流；access 过期经刷新后需同步更新缓存中的 access_token，否则缓存会一直返回过期令牌 |
| B5 | 新增前端调用的 UC 令牌兑换接口（已定：前端主动触发） | `controller/UserController.java`（建议路径 `POST /auth/uc-token/issue`，最终命名待本侧确认） | 请求携带自签 token；内部查 `loginToken` → uid → 查 B4 缓存，未命中才调 UC 兑换；UC access_token 放响应体返回。原「随业务响应回带 `X-UC-Access-Token`」方式作废；该接口需纳入 CORS 放行前端 origin |
| B6 | 并发去重 | 兑换接口（B5） | 同一 uid 同一时刻只允许一次兑换（进程内锁 + Redis 短锁），避免多标签页 / 多组件同时触发放大请求量 |
| B7 | 失败降级 | 兑换接口（B5）+ 前端 | 兑换失败（如 UC 不可用）时不得影响用户：接口返回失败，前端不阻塞业务（自签 token 仍可正常调 BackEndV3），下次启动或下次进入业务页再重试；连续失败达阈值再告警。用户不会因此掉登录 |
| B8 | 新登录保持现状并接住 UC 令牌 | `DirectLoginServiceImpl` / `OAuthUserServiceImpl.createSessionByOAuth2Uid` | 继续签发自签 token（会话凭据不变）；同时把 `/oauth2/direct-user` 返回的 UC 令牌接住、refresh_token 加密落库。上游 B1/B2 中「接住令牌」部分仍要做，「停止签发自签 token」部分不做 |
| B9 | 观测埋点 | — | 自签 token 命中量、兑换接口调用量与触发来源、兑换成功率、兑换耗时、降级次数（见第 10.3 节） |
| B10 | 不再需要增量同步任务 | — | 本方案的核心收益点 |
| B11 | 不再清理自签 token | — | 本次决策的直接后果：自签 token 已是刷新凭据，必须长期保留；仅 `uc:migrate:issued:{uid}` 兑换缓存在存量迁移完成后可清（它只用于防重复兑换，清掉后用户下次触发兑换会重新兑换一次并按 B4 重写缓存，凭证恒一条，无害） |
| B12 | 刷新凭据复用自签 token | `UserController` + `OAuthUserServiceImpl` | `/auth/token/refresh` 按请求携带的自签 token 查 `loginToken:{token}` 得 uid → 取服务端 refresh_token → 调 UC 刷新。不新建凭证表、不改索引、不引入 `sessionId`。刷新失败（90009）时前端删除本地 `UC_ACCESS_TOKEN`，下次启动重新触发兑换自愈（见 4.1、R17） |

---

## 6. 令牌下发方式（已定：前端主动触发 + 专用接口返回）

**触发点在前端，下发由专用接口完成**

- 前端在应用启动 / 进入业务页时读 localStorage 的 `UC_ACCESS_TOKEN`；**缺失**才带自签 token 调 `POST /auth/uc-token/issue`，BackEndV3 把 UC access_token 放进响应体返回。
- 调 BackEndV3 的请求始终不需要切换凭据（仍是自签 token）；BackEndV3 的拦截器不做任何 UC 交互。
- 仅在「本地无 UC access_token」时多一次显式往返，属一次性 / 偶发动作；常态请求仍是单次往返。

**接口响应契约（BackEndV3 → 前端）**

| 字段 | 说明 |
|---|---|
| `access_token` | UC access_token（前端调 UC 接口用） |
| `expires_in` | 有效期（秒） |
| `token_type` | 固定 `Bearer` |
| `scope` | 可选，scope |

> 契约中**不含 `refresh_token`**（不下发浏览器），也不回带用户资料。

**刷新凭据 = BackEndV3 原有的自签 token（不新建概念）**

- 前端不需要、也不能持有 UC 的 refresh_token：`arknights-yituliu-web` 是机密客户端，`OAuthTokenServiceImpl.refreshToken` 要求 `client_secret`，浏览器调 UC `/oauth2/token` 必然被拒（上游 §2.5 已确认）。刷新只能由 BackEndV3 服务端代做。
- 前端需要一个「让 BackEndV3 认出这是哪条会话」的凭据，直接复用现有自签 token：`loginToken:{token} → uid` 本来就在用，`/auth/token/refresh` 按它反查 uid 即可。
- 不新造 `sessionId`，不改表结构、不动 Redis key 布局——自签 token 天然「一设备一条」，多端登录无需任何改动。

> 顺带绕开了上游计划 B3 的 `uk(uid, client_id)` 陷阱：该唯一约束会把多端登录变成单端互踢（现有 `loginToken:{token} → uid` 支持多端）。复用自签 token 即无需建凭证表、也无需改索引（见 11.2 第 5 条）。

**两个必须注意的实现点**

1. CORS：兑换接口与刷新接口需放行前端 origin。JSON 接口把令牌放在响应体里，**不再需要 `Access-Control-Expose-Headers`**（原响应头方案才需要）。
2. 禁止缓存：接口响应携带令牌，必须确保 `Cache-Control: no-store`，中间代理与浏览器缓存不得缓存该响应。

**两条明确的禁止项**

- 绝不要用 UC access_token 当刷新凭据：access 过期后令牌会轮换、映射断链；且映射条目随刷新无限增长（5 万用户 × 90 天约 5400 万条），不可接受。
- 必须认下代价：自签 token 一旦成为刷新凭据就永远不能删——旧自签体系（`tokenGenerator` / `loginToken` / `token_record(type=login)`）必须长期维护，上游计划「自签链路彻底移除」的目标不再成立（见 11.2）。

> 已作废的备选：① 方式 A「随业务响应回带 `X-UC-Access-Token`」——触发点与业务请求耦合，且所有业务响应都要处理响应头；② 备选 B「401 触发」——首次 401 会被日志与监控记为错误。现方案由前端在启动时主动判断，触发点与业务解耦、失败不影响业务请求；401 仅作为 access 过期后的兜底路径（见 4.1 链路图第三段）。

---

## 7. 与 UC 的接口契约摘要

> 完整定义见 `./UC侧实施方案.md` 第 5 节。以下为 BackEndV3 侧对接所需的最小信息。

### 7.1 端点与请求

`POST /oauth2/internal/migrate-token`，`Content-Type: application/x-www-form-urlencoded`，仅接受 HTTPS。

| 参数 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `client_id` | 是 | string | 目标 OAuth 客户端（`arknights-yituliu-web`） |
| `uid` | 是 | long | uid，由 BackEndV3 从自签 token 反查得到 |
| `ts` | 是 | long | 请求时间戳，Unix 秒 |
| `nonce` | 是 | string | 16–32 位随机串，一次性 |
| `kid` | 是 | string | 公钥标识，对应 UC 侧 `kid → 公钥` 映射表，用于轮换 |
| `sig` | 是 | string | Ed25519 签名 `base64(Ed25519_sign(privateKey, canonical))` |
| `origin` | 否 | string | 来源标识，仅审计落库，如 `backendv3-legacy` |
| `legacy_token_hash` | 否 | string | 旧 token 的 sha256 十六进制，仅审计，不参与鉴权与幂等判断 |

### 7.2 签名 canonical 串（与 UC 逐字节一致）

```
canonical = "POST" + "\n" + "/oauth2/internal/migrate-token" + "\n"
          + kid + "\n" + client_id + "\n" + uid + "\n" + ts + "\n" + nonce
```

- 纳入 HTTP 方法 + 路径：防止同一密钥签出的串被挪用到其他端点。
- 纳入 `kid`：支持公钥轮换。
- `sig = base64(Ed25519_sign(privateKey, canonical))`。

**共同约束**：私钥独立于 `client_secret`，单独轮换；私钥只从环境变量 / 密钥管理注入，绝不写入任何 yml、绝不进入日志；支持 `kid` 轮换；怀疑泄漏时先关闭 UC 侧 `enabled` 再轮换密钥对。

### 7.3 幂等语义（决定 B4 缓存维度）

- UC 侧维护 `uc:oauth:migrate:current:{clientId}:{uid}` → 上一轮迁移签发的 refresh_token。
- 同一 `(uid, clientId)` 上**恒只保留一条迁移凭证**：每次兑换都会先撤销上一轮迁移凭证，再签发新令牌对。
- 因此 **BackEndV3 的兑换缓存必须按 uid 维度**（`uc:migrate:issued:{uid}`），否则多设备会互相撤销（见 B4 / R17）。
- 附带约束（源于「恒一条凭证」，与缓存维度无关）：任一设备登出触发 UC 撤销后，其他设备的刷新会以 90009 失败，需前端删除本地 `UC_ACCESS_TOKEN` 后重新触发兑换自愈——自签 token 不受影响、用户不掉登录，但该自愈路径必须实现。验收见第 9 节第 7 条。

### 7.4 响应

最小响应 `MigrateTokenVO{uid, access_token, token_type, expires_in, refresh_token, scope}`，**不回带昵称 / 头像 / 邮箱**。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "uid": 1234567890123456789,
    "access_token": "3f1a...（64 位十六进制）",
    "token_type": "Bearer",
    "expires_in": 7200,
    "refresh_token": "9c02...（64 位十六进制）",
    "scope": "user.read"
  }
}
```

用户资料由 BackEndV3 持新 access_token 调 UC `/oauth2/userinfo` 获取（该端点已有拦截器保护）。

### 7.5 错误码对照

| 码 | 含义 | BackEndV3 侧处置建议 |
|---|---|---|
| 90015 | 迁移兑换接口未开启（`enabled=false`） | 视为「通道未上线」，走 B7 降级放行，不告警风暴 |
| 90016 | 迁移请求签名校验失败 | 配置或密钥错误，立即告警（不重试） |
| 90017 | 迁移请求已过期或已被使用 | 检查双方 NTP 对时与 `ts` 生成逻辑 |
| 80008 | IP 不在白名单 | 出口 IP 漂移或配置错误，立即告警 |
| 90001 / 90013 / 90014 | 客户端不存在 / 未审批 / 未开通直连认证 | 客户端配置问题，见第 8 节 |
| 20001 / 20004 | 用户不存在 / 已封禁 | 该 uid 不允许签发，属预期拒绝 |
| 30005 | 触发限流 | 检查是否重复兑换（B4 缓存是否失效） |
| 90009 | （刷新场景）刷新令牌已失效或已被吊销 | 清 B4 缓存重新兑换自愈（见 7.3） |

---

## 8. 上线前核对（BackEndV3 侧须确认的 UC 配置）

以下配置错任一项，兑换会成功但 2 小时后全体掉登录，因此必须纳入灰度前置检查。

```sql
-- 核对 arknights-yituliu-web 的关键配置
SELECT id, auth_methods, grant_types, scopes,
       require_pkce, access_token_ttl, refresh_token_ttl,
       owner_enabled, admin_approved, direct_auth_enabled
FROM oauth_client
WHERE id = 'arknights-yituliu-web';
```

| 字段 | 要求 | 不满足的后果 |
|---|---|---|
| `auth_methods` | `client_secret_post` | 无法刷新 |
| `grant_types` | 必须含 `refresh_token` | 刷新被直接拒绝（`issueDirectToken` 刻意不校验该 grant，容易漏配） |
| `scopes` | 均为 `OAuthScope` 已登记的合法值 | `normalizeScope` 直接抛 90003 |
| `owner_enabled` / `admin_approved` | 均为 1 | 90001 / 90013 |
| `direct_auth_enabled` | 为 1 | 迁移端点返回 90014 |
| `refresh_token_ttl` | 建议显式设置 | 留空则取全局默认 90 天 |

> 迁移端点的准入开关复用 `oauth_client.direct_auth_enabled`（不新增字段、不做 DDL），详见 `./UC侧实施方案.md` 第 7.2 节。

---

## 9. 测试与验收

### 9.1 端到端

1. 打开新前端（localStorage 无 `UC_ACCESS_TOKEN`）→ 前端自动调 `POST /auth/uc-token/issue` → 拿到 UC access_token 并写入 localStorage；同一时刻的 BackEndV3 业务请求照常成功，用户无感。
2. 该 access_token 调 UC `/oauth2/userinfo` 成功（验证前端直连 UC 可用）。
3. 已有 `UC_ACCESS_TOKEN` 时再次进入不再调用兑换接口；手动清除本地令牌后再进入会命中 B4 缓存，不产生第二次兑换（UC 审计中仍只有一条）。
4. 跨域场景验证兑换接口的 CORS 放行生效，前端能正常调用并读到响应体中的令牌（不再依赖 `Access-Control-Expose-Headers`）。
5. 等 UC access 过期 → 调 UC 接口 401 → 带自签 token 调 `/auth/token/refresh` 成功；再把 access TTL 临时调成 60s 连续刷 5 次仍成功（验证刷新不依赖 UC access_token、不会断链）。
6. 删除 `loginToken:{token}`（模拟会话被登出）后刷新失败，前端提示重新登录。
7. 多端验证：同一账号两个设备登录，各自的自签 token 都能独立刷新、互不踢下线；第二台设备触发兑换后，第一台的凭证仍能刷新成功（验证 B4 的 uid 维度缓存，见 R17）。
8. UC 侧「我的授权」能看到该 client 的记录，且从 UC 侧撤销后刷新失败。
9. 双撤验证（R14）：登出后自签 token 与 UC 刷新能力都失效。
10. 确认自签 token 长期有效：执行 T4 清理后自签 token 照常可用（与上一版预期相反，务必覆盖）。
11. 未加载新前端的旧版本前端（不调兑换接口）不受影响：业务请求正常，UC 相关能力按旧路径不可用（见 R18）。

### 9.2 联调关注点

- Ed25519 签名逐字节一致性（canonical 串换行、字段顺序、`uid` / `ts` 原始文本）。
- 错误码分布是否符合第 7.5 节的处置建议。
- 限流命中情况：uid 维度 5/分钟是否被 B4 缓存有效规避。
- 响应体契约（不含 `refresh_token`、不含用户资料）、兑换接口的 CORS 放行、`Cache-Control: no-store`。

---

## 10. 迁移流程与「半天」口径

### 10.1 为什么是响应式

登录态迁移只能在用户下一次请求时发生——用户不在线时，没有任何通道能把新令牌塞进他的浏览器。因此：

- 错误口径：「半天内把 5 万个 token 换完」——做不到，也不必要（非活跃用户本就应等他回访）。
- 正确口径：「半天内上线迁移通道，并承诺不强制任何人重登」——按本方案，通道上线即生效。
- **触发点已移至前端**（应用启动 / 进入业务页时检测本地 `UC_ACCESS_TOKEN` 缺失），因此迁移进度取决于前端发版的覆盖度：通道就绪后，加载到新前端的用户才会迁移；旧版本前端不迁移也不受影响（见 R18）。

### 10.2 迁移时间线

| 阶段 | 内容 | 产出 |
|---|---|---|
| T0 | UC 侧 M1–M16 上线（`migrate.enabled` 默认关闭） | 接口就绪但不可用 |
| T1 | 双方联调：Ed25519 签名、错误码、限流、审计、兑换接口契约与 CORS | 各环境跑通 |
| T2 | 打开 `migrate.enabled`，**前端灰度发版**（1% → 10% → 100%）：触发点在前端，发版进度即迁移进度 | 加载到新前端的存量用户自动完成迁移 |
| T3 | 观察期：看 UC 授权覆盖率（活跃 uid 是否都已建好 UC 授权）与兑换失败率（应约等于 0）。注意自签 token 命中量不会降到 0——它是长期会话凭据，不可用作迁移完成的判据 | 活跃用户已全部持有 UC 授权 |
| T4 | 观察期结束：清 `uc:migrate:issued:*` 兑换缓存；关闭 `migrate.enabled`；摘除网关 location。`loginToken:*` 与 `token_record(type=login)` 保留不动（自签体系长期在役，见第 6 节） | 迁移通道下线，自签链路转为常态 |

### 10.3 观测指标

- **UC 授权覆盖率**（应单调上升至约 100%）——本次迁移的唯一完成判据。
- 自签 token 命中量（不会趋 0，仅供容量参考，不应视为指标异常）。
- 兑换接口调用量（前端触发）与兑换成功率、P99 耗时。
- 兑换降级次数（UC 不可用导致的失败返回）。
- `nonce` 重放拒绝次数、签名失败次数、IP 白名单拒绝次数——这三个指标异常升高意味着有人在扫描该端点。
- 同一 uid 的重复兑换次数。uid 维度缓存（B4）生效时该值应恒为 0，出现正值意味着缓存丢失（Redis 重启 / 被清）或并发去重失效。
- UC 令牌刷新成功率、刷新失败原因分布（自签 token 失效 vs UC 拒绝）。

---

## 11. 决策记录与回馈上游

### 11.1 已确认决策

| # | 决策 | 依据 |
|---|---|---|
| 1 | 认证层 = Ed25519 非对称签名（UC 侧只存公钥），不采用共享密钥 | 跨公网下共享密钥任一侧泄漏即可伪造 |
| 2 | BackEndV3 出网 IP 固定，`ip-allowlist` 作为第二道防线必配 | 见 `./UC侧实施方案.md` 第 7.3 节 |
| 3 | 令牌下发方式 = 前端主动触发 + 专用接口响应体返回（`POST /auth/uc-token/issue`） | 第 6 节——触发点与业务解耦、失败不影响业务请求；原「随业务响应回带 `X-UC-Access-Token`」方式作废 |
| 4 | 迁移准入开关复用 `oauth_client.direct_auth_enabled`，不新增字段、不做 DDL | 见 `./UC侧实施方案.md` 第 7.2 节 |
| 5 | BackEndV3 的会话凭据继续用自签 token，并兼作 UC 令牌的刷新凭据；不引入 `sessionId` | 第 6 节——前端直连 UC 需要 UC access_token，但刷新只能由服务端做；自签 token 现成可用、天然多端 |
| 6 | 自签 token 长期保留（原「观察期后统一删除」作废） | 决策 5 的直接后果——刷新凭据不能删 |

待定项：无，可进入实施。

### 11.2 需回馈上游计划的修订项

上游文档 `统一令牌改造计划.md` 的目标已被本轮决策大幅改写，实施前需同步修订：

| # | 上游位置 | 原内容 | 需如何改写 |
|---|---|---|---|
| 1 | §1.2 目标 1、§3.2 | 「前端只持 UC 令牌，不再携带自签 token」 | 改为「前端持两种令牌、各司其职」：自签 token 调 BackEndV3 并兼作刷新凭据；UC access_token 调 UC 接口。「前端只持一种令牌」这一目标取消 |
| 2 | §1.2 目标 2、§5.2 B2/B4/B17、§6-4、§9 M4 | 「不再生成 / 存储自签 token」「自签链路彻底移除」 | 全部作废：`tokenGenerator` / `loginToken` / `token_record(type=login)` 长期在役（见 R16） |
| 3 | §4.1 决策 1、§5.1 U1–U3、§5.2 B7、§8 introspection 单测 | 新增 UC introspection 供 BackEndV3 校验令牌 | 不需要做了：BackEndV3 仍查本地 `loginToken`，不校验 UC 令牌，消费方 `UcTokenIntrospector` 随之取消（见 3.2）。附带消除上游 R2（UC 挂机导致全站 401） |
| 4 | §5.2 B5/B6/B14 | 拦截器改为校验 UC 令牌、`extractToken` 重写、封禁以 UC 为单一事实来源 | 保留现状；但封禁 / 登出需双撤（UC 侧 + 删本地 `loginToken`），见 R4 / R14 |
| 5 | §5.2 B3、§4.2 | 新增凭证表、`uk(uid, client_id)` | 复用自签 token 后不需要建表，该 `uk` 陷阱自动规避（见第 6 节） |
| 6 | §5.2 B8、§10 第 5 条 | 刷新接口只说「读本地加密凭证」；刷新触发方式未定 | 明确：`/auth/token/refresh` 按请求携带的自签 token 反查 uid（见 B12） |
| 7 | §5.2 B1 | 补全 `DirectLoginUserVO` 接住令牌 | 仍要做；且新登录时也要把 refresh_token 加密落库（见 B8） |
| 8 | §5.2 B4 | 兑换结果缓存 key 取 `sha256(自签 token)` | 改为 `uc:migrate:issued:{uid}`：UC 侧迁移凭证是 `(uid, client_id)` 维度的幂等模型，缓存必须同维度，否则同一用户多设备互相撤销、旧设备掉登录（见 B4 / R17） |

> 结论：上游计划「前端统一使用 UC 令牌」这一目标已由本轮决策取代；仍然保留的部分只有「接住 UC 令牌 + 服务端代持 refresh_token + 用一条链路统一刷新」。

---

## 12. 风险与缓解（BackEndV3 侧相关）

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R3 | 兑换成为关键路径 | UC 抖动导致用户掉登录 | BackEndV3 侧降级放行（B7）+ 短超时 + 异步重试；连续失败才要求重登 |
| R4 | 自签 token 与 UC 授权长期并存 | 一份凭证两个权威 | 职责分离、长期常态（非过渡期约束）：UC 是授权权威（该 uid 有什么权限），自签 token 是会话权威（该设备是否仍登录）。登出 / 封禁必须双撤（UC `/oauth2/revoke` + 删本地 `loginToken`），见 R14 |
| R6 | `grant_types` 漏配 `refresh_token` | 兑换成功但 2 小时后全体掉登录 | 第 8 节上线前用 SQL 核对；此项纳入灰度前置检查 |
| R7 | 前端 token 权限过宽 | 浏览器持有 client 全量 scope | 与上游计划 R5 同一问题：收敛该 client 的 `scopes` |
| R13 | 前端未保存兑换返回的 UC access_token | 前端调 UC 接口持续 401 / 不可用 | B4 缓存兜底：前端再次触发兑换只会命中缓存分支，不会重复调 UC（UC 侧不会被击穿）；前端 401 后走刷新即可自愈、刷新成功后重新写入；埋点统计「UC 接口 401 率」即可发现 |
| R14 | 封禁 / 登出只撤了一侧 | 用户被撤权后仍可用另一份凭证继续访问 | 双撤为必要步骤，且是长期常态（见 R4）；纳入验收用例（第 9.1 节第 9 条） |
| R15 | 自签 token 泄漏 | 攻击者可换取 UC access_token，也可直接调 BackEndV3 | 未新增泄漏面（它本来就是会话凭据，风险等级与现状相同）。缓解沿用现状：TTL 90 天、登出即删 `loginToken` + UC 撤销、可绑 UA / IP。注意自签 token 为 `AES("{id:X}.X.timestamp")`，随机性来自时间戳，抗枚举弱于随机串——若要提升需更换凭据形态，本次不做 |
| R16 | 自签体系必须长期维护 | 上游「自签链路彻底移除」目标作废；AES 密钥、`tokenGenerator`、`loginToken`、`token_record(type=login)` 长期在役 | 本次决策的已知代价。必须在上游计划中显式改写目标与验收标准，否则后人会按旧目标误删代码（见 11.2） |
| R17 | 兑换缓存按自签 token 维度分片（多设备） | 同一用户多设备各兑换一次，第二次兑换会撤销第一次的迁移凭证；旧设备 UC access 过期后刷新得 90009，被要求重新登录，与「不强制重登」的承诺冲突。该场景必然发生（不是并发问题），B6 的锁挡不住 | B4 缓存改为 `uc:migrate:issued:{uid}`（uid 维度，与 UC 侧 `(uid, client_id)` 幂等模型对齐）：同一用户全程只兑换一次，UC 侧的撤销分支不再触发。附带约束（源于「恒一条凭证」，与缓存维度无关）：任一设备登出触发 UC 撤销后，其他设备的刷新会以 90009 失败，需清 B4 缓存重新兑换自愈——自签 token 不受影响、用户不掉登录，但该自愈路径必须实现。验收见第 9.1 节第 7 条 |

---

## 13. 附录：BackEndV3 侧关键代码位置索引

> 外部仓库，需另行核对。

| 用途 | 位置 |
|---|---|
| 自签 token 校验与新增兑换分支 | `src/main/java/com/lhs/interceptor/UserInterceptor.java` |
| 会话创建、令牌反查、登出 | `src/main/java/com/lhs/service/user/impl/OAuthUserServiceImpl.java`（`createSessionByOAuth2Uid`、`getUserInfoPOByToken`、`logout`） |
| UC 相关配置 | `src/main/resources/application-test.yml`（`user-center.oauth.*`） |
