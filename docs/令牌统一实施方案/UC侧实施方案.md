# BackEndV3 与 UC 令牌统一方案 —— UC 侧实施方案

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.0 |
| 状态 | 已定稿，可进入实施 |
| 本文范围 | **UC（UserCenterV1）**：OAuth2 授权服务器，包名 `com.orange`，本仓库 |
| 目标读者 | UC 侧开发、联调、运维 |
| 上游文档 | `docs/BackEndV3与UC令牌统一方案.md`（总方案，本文为其拆分件之一） |
| 兄弟文档 | `./BackEndV3侧实施方案.md`、`./前端实施方案.md` |

---

## 1. 背景与结论

### 1.1 原始设想

> 在 BackEndV3 与酸橙云都放一个密钥，永远同步 BackEndV3 的 token 到酸橙云充当 refresh_token，目标在半天内全部迁移完 5 万个用户登录态。

### 1.2 结论

方向可行，但形式改为「按需兑换」，且不需要同步任何 token。

| 设想的组成部分 | 结论 |
|---|---|
| 两边共用一个密钥 | 不采用共享密钥。两台服务器跨公网，共享密钥任一侧泄漏即可伪造任意用户请求。已定：Ed25519 非对称签名，BackEndV3 持私钥、UC 只存公钥（见 5.3） |
| 把 BackEndV3 的 token 同步过去当 refresh_token | 技术上成立（见 3.1），但不该这么做：需要全量导出 5 万条 token→uid 映射、逐条对账、长期增量同步；且旧 token 会被升格为 UC 长期凭证，登出与封禁语义纠缠 |
| 半天内迁完 5 万登录态 | 可以达成，但口径须改为「半天内上线迁移通道，且不强制任何人重登」。登录态迁移在本质上是响应式的（见 12），通道上线即生效，活跃用户几分钟内切换完成，长尾用户随回访自动完成 |

### 1.3 一句话方案

BackEndV3 本身持有「自签 token → uid」的映射。用户带着自签 token 请求时，BackEndV3 现场用 Ed25519 签名向 UC 换取一对全新的 UC 令牌，把 access_token 回带给前端（供前端调用 UC 接口），refresh_token 留在服务端。不导出数据、不对账、不改 UC 表结构，前端凭据也无需更换。

**UC 在其中的角色**：提供唯一的新增能力——一个跨公网可达、以 Ed25519 验签为唯一准入屏障的「凭 uid 现场签发 UC 令牌」内部端点（见 5.2），其余协议端点行为一律不变。

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

## 3. 方案选型：为什么不做全量同步

### 3.1 全量同步在技术上确实成立

UC 的 refresh_token 就是「一段不透明字符串当作 Redis key 后缀」，UC 全程不校验其格式：

```
uc:oauth:refresh:{token}  →  {"uid":..,"clientId":..,"scope":..}   TTL
```

key 形态见 [RedisKeyUtil.java](../src/main/java/com/orange/common/util/RedisKeyUtil.java)，写入见 [OAuthTokenServiceImpl.java](../src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java)。因此把外部字符串塞进去当作 refresh_token 是能运行的。

但一条可用的 refresh 记录是三处一并写入，漏写一处即为脏数据：

| 必写项 | 位置 | 漏写的后果 |
|---|---|---|
| Redis 记录 `{uid,clientId,scope}` 与 TTL | `OAuthTokenServiceImpl.issueDirectToken` | 刷新直接返回 90009 |
| 反向索引 Set `uc:uid:oauth:{uid}` 成员 `refresh:{token}` | 同上 | 「我的授权」整体撤销与惰性清理会漏掉这批令牌 |
| 台账 `oauth_grant`（`token_hash = sha256(token)`、`expire_time`） | `OAuthTokenServiceImpl` 台账写入段 | 用户看不到、也无法撤销这条授权 |

### 3.2 全量同步的四项实际成本

1. 需要导出并逐条对账 5 万条 `loginToken:{token} → uid`（BackEndV3 的 Redis 与 `token_record`）。任何一条映射错误即导致一个用户掉登录，而这类错误在 5 万条规模上几乎必然出现。
2. 需要长期维持增量同步任务（新登录、登出、封禁均需同步）。而按本方案，切换后新登录直接走新链路，根本不存在需要同步的新数据。
3. 5 万条一次性写入会与线上流量争抢 Redis 资源，台账一次新增 5 万行。
4. 旧 token 被升格为 UC 长期凭证：自签 token 同时成为「BackEndV3 的登录凭证」与「UC 的 refresh_token」，等于一个字符串两个权威，登出必须两处都撤，封禁也必须两处都撤。

### 3.3 预热同样没有意义

若提前为 5 万个 uid 预建 UC 凭证，前端手中的旧 token 并未改变，用户回访时仍要走一遍兑换。除了白白产生 5 万条凭证与无用的台账记录，没有任何提前收益。**迁移必然是响应式的。**

### 3.4 两条路线的职责对比

| 维度 | 全量同步方案 | 按需兑换方案（本方案） |
|---|---|---|
| UC 是否需要知道旧 token | 需要（5 万条） | 不需要 |
| 数据导出 / 对账 | 需要 | 不需要 |
| 增量同步任务 | 需要，长期 | 不需要 |
| refresh_token 来源 | 旧 token（复用品） | UC 现场新签（不复用） |
| UC 侧 DDL 改动 | 无 | 无 |
| 用户可见影响 | 无 | 无 |
| 失败面 | 单条映射错误导致单用户掉登录 | 兑换接口不可用导致该用户回退到「重新登录」 |

> 关键判断：**迁移只需要 uid，不需要旧 token 本身**。uid 在 BackEndV3 侧现成可查，因此「同步 token」是纯粹的多余动作。

---

## 4. UC 侧职责与权威边界

### 4.1 权威边界

| 事项 | 权威方 | 说明 |
|---|---|---|
| 会话有效性（这台设备是否仍登录） | BackEndV3 自签 token | 长期常态，非过渡期约束 |
| 授权范围（该 uid 拥有哪些权限） | UC | 由 UC 签发与撤销 |
| 登出 / 封禁 | 双方 | 必须双撤，缺一即为安全漏洞（R14） |

**鉴权路径声明（BackEndV3 侧，全程不变）**

- BackEndV3 的请求鉴权**始终**依据本地 Redis `loginToken:{token} → uid`（命中即通过），**不调用 UC、不校验 UC 令牌**。
- UC access_token 只用于前端调用 UC 接口，**不参与 BackEndV3 的任何鉴权判断**。
- 由此 BackEndV3 不依赖 UC 的可用性：UC 宕机或两端网络中断时，BackEndV3 的业务与用户登录态不受影响。
- 直接推论：UC 侧 introspection 端点（RFC 7662）**本次不实现**——它唯一的用途是供 BackEndV3 校验 UC 令牌，该调用方已不存在（见 5.1）。

### 4.2 UC 侧职责边界（做什么 / 不做什么）

| 做 | 不做 |
|---|---|
| 新增迁移专用兑换端点，凭 uid 现场签发一对 UC 令牌 | 不校验、不认识 BackEndV3 的自签 token 内容 |
| 建立跨公网认证层（Ed25519 验签 + IP 白名单 + 时间窗 + nonce 防重放） | 不做全量数据导入 / 对账 / 增量同步 |
| 维护「同一 `(uid, clientId)` 恒一条迁移凭证」的幂等语义 | 不做 DDL 改动、不新增业务表字段 |
| 提供刷新（`/oauth2/token`）与吊销（`/oauth2/revoke`）能力 | 不实现 introspection |
| 记录迁移审计（不含令牌明文与签名原文） | 不修改任何既有协议端点行为 |

---

## 5. UC 侧接口定义（核心）

### 5.1 端点总览

| 端点 | 变更 | 说明 |
|---|---|---|
| `POST /oauth2/internal/migrate-token` | 新增 | 迁移专用兑换：凭 uid 现场签发一对 UC 令牌 |
| `POST /oauth2/direct-*` | 不变 | 新登录链路继续使用 |
| `POST /oauth2/token` | 不变 | 刷新继续使用 |
| `POST /oauth2/revoke` | 不变 | 登出继续使用 |
| `POST /oauth2/introspect` | 本次不实现 | 仅当 BackEndV3 需校验 UC 令牌时才必要；本轮 BackEndV3 仍以本地 `loginToken` 鉴权，**两边均无调用方**（见 4.1） |

> 注意：上述 `/oauth2/internal/migrate-token` 端点**跨公网可达**（BackEndV3 与 UC 是两台不同服务器，无法只走内网）。因此**认证层是唯一的准入屏障**（见 5.3）；网络层只做加固（TLS + IP 白名单 + 网关限流），不能视为屏障。UC 自身不为该路径注册任何拦截器（[WebConfig.java](../src/main/java/com/orange/interceptor/WebConfig.java) 当前仅注册 `/user/**`、`/auth/logout`、`/oauth2/userinfo` 等），认证完全由端点自带。

### 5.2 `POST /oauth2/internal/migrate-token`

**用途**：BackEndV3 在识别出「用户持有旧自签 token」后，为该 token 对应的 uid 换发一对全新的 UC 令牌。

**请求**：`Content-Type: application/x-www-form-urlencoded`，仅接受 HTTPS。

| 参数 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `client_id` | 是 | string | 目标 OAuth 客户端（`arknights-yituliu-web`） |
| `uid` | 是 | long | uid，由 BackEndV3 从自签 token 反查得到 |
| `ts` | 是 | long | 请求时间戳，Unix 秒 |
| `nonce` | 是 | string | 16–32 位随机串，一次性 |
| `kid` | 是 | string | 公钥标识，对应 UC 侧 `kid → 公钥` 映射表，用于轮换 |
| `sig` | 是 | string | Ed25519 签名 `base64(Ed25519_sign(privateKey, canonical))`，算法见 5.3 |
| `origin` | 否 | string | 来源标识，仅审计落库，如 `backendv3-legacy` |
| `legacy_token_hash` | 否 | string | 旧 token 的 sha256 十六进制，仅审计，不参与鉴权与幂等判断 |

**响应**（HTTP 200）：这是 UC → BackEndV3 的服务端间响应（令牌下发契约见 `./BackEndV3侧实施方案.md` 第 6 节：前端主动触发兑换，BackEndV3 经响应体返回 access_token）。为缩小公网暴露下的响应面，不复用 `ServerLoginVO`，改用最小响应 VO `MigrateTokenVO{uid, access_token, token_type, expires_in, refresh_token, scope}`，**不回带昵称 / 头像 / 邮箱**。用户资料由 BackEndV3 持新 access_token 调 `/oauth2/userinfo` 获取（该端点已有拦截器保护）。

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

**处理步骤（严格按序）**

| 步 | 处理 | 失败返回 |
|---|---|---|
| 1 | 协议校验：非 HTTPS 直接拒绝 | — |
| 2 | 开关校验：`user-center.oauth.migrate.enabled=1` | 90015 |
| 3 | 来源校验：请求 IP 在 `ip-allowlist` 内（BackEndV3 出网 IP 已确认固定，此项必配） | 80008 |
| 4 | 时效校验：`\|now - ts\| <= clock-skew-seconds`（默认 120s） | 90017 |
| 5 | 防重放：`SET NX EX uc:oauth:migrate:nonce:{nonce}`（TTL = `clock-skew-seconds × 2`），失败即拒绝 | 90017 |
| 6 | 认证层校验：取 `kid` 对应公钥，对 5.3 的 canonical 串做 Ed25519 验签 | 90016 |
| 7 | 客户端校验：`oauth_client` 存在、`owner_enabled=1`、`admin_approved=1`、`direct_auth_enabled=1`（复用现有直连开关，见 7.2） | 90001 / 90013 / 90014 |
| 8 | 用户校验：`userinfo` 存在且 `status >= 0`。**此步不可省，否则该端点将沦为绕过封禁的后门** | 20001 / 20004 |
| 9 | 限流三层：网关层按 IP、应用层按 uid（默认 5/分钟）与 client（默认 3000/分钟） | 30005 |
| 10 | 替换上一轮迁移凭证（见 5.4） | — |
| 11 | 签发：调用现有 `OAuthTokenService.issueDirectToken(clientId, uid)`，它已正确完成 access 记录、refresh 记录、双向索引、台账四件事，直接复用不重写 | — |
| 12 | 审计日志：记录 `client_id / uid / origin / legacy_token_hash / 请求 IP / kid / 结果`；私钥、签名原文、令牌明文一律不入日志 | — |

### 5.3 跨公网认证层（已定：Ed25519 非对称签名）

端点跨公网可达，网络隔离不可用，认证层是唯一屏障。

**已确认的选型**

- 认证方式 = **Ed25519 非对称签名**：BackEndV3 持私钥签名，UC 只存公钥验签。UC 侧配置即使泄漏也无法伪造签名，密钥无需在两台机器上各放一份。
- 网络条件 = BackEndV3 出网 IP 固定：`ip-allowlist` 可正常生效，作为认证层之外的第二道防线。
- 不采用共享密钥（HMAC）：跨公网场景下任一侧泄漏即可伪造请求、冒充任意用户，风险不可接受。

**统一 canonical 串（待签名 / 待核验内容）**

```
canonical = "POST" + "\n" + "/oauth2/internal/migrate-token" + "\n"
          + kid + "\n" + client_id + "\n" + uid + "\n" + ts + "\n" + nonce
```

- 纳入 HTTP 方法 + 路径：防止同一密钥签出的串被挪用到其他端点。
- 纳入 `kid`：支持公钥轮换（UC 侧维护 `kid → 公钥` 映射表，新旧并行一个窗口后再摘除旧的）。
- 本次实现：`sig = base64(Ed25519_sign(privateKey, canonical))`，UC 用该 `kid` 对应的公钥验签。

**共同约束**

- 认证密钥 / 私钥独立于 `client_secret`，单独轮换，避免「迁移能力」与「刷新能力」共用一把钥匙而放大影响面。
- 必须使用常量时间比较（`MessageDigest.isEqual`），不得使用 `String.equals`。
- `nonce` TTL = `clock-skew-seconds × 2`，覆盖整个可接受时间窗。
- 密钥 / 私钥只从环境变量或密钥管理注入，绝不写入任何 yml，绝不进入日志。
- 支持 `kid` 轮换：新旧密钥并行一个窗口后再摘除旧密钥；怀疑泄漏时先关闭 `enabled` 再轮换。

**备选方案（仅当 Ed25519 落地遇阻时再评估）**

- mTLS 双向证书：UC 侧 Nginx 对该 location 开启 `ssl_verify_client`，协议中无需传递签名参数，但需要证书基建。
- VPN / 专线 + HMAC：若能打通网络层，等价回到内网假设。

> 选型差异只影响认证层：5.2 的业务参数（`client_id / uid / ts / nonce`）与 5.4 的幂等语义完全不变。备选 mTLS 时 canonical 不适用（改由证书主体校验）；备选 HMAC 时为 `hex(HMAC-SHA256(secret, canonical))`。

### 5.4 幂等与「一个用户一个 client 一条凭证」

多标签页并发、网络重试都会对同一 uid 发起多次兑换。若不加约束，每次都会新增一条 refresh 记录与台账行，反向索引持续膨胀。

处理方式（符合上游计划 `uk(uid, client_id)` 语义）：

- 维护映射 key：`uc:oauth:migrate:current:{clientId}:{uid}` → 上一轮迁移签发的 `refresh_token`，TTL = refresh TTL。
- 替换流程：若映射存在 → 删除该 refresh 的 Redis 记录、摘除反向索引成员、`oauthGrantMapper.markRevokedByTokenHash(sha256(old))` 将台账置为已吊销 → 再签发新令牌对 → 更新映射 key。
- 上述「查映射 → 撤旧 → 签新 → 写映射」由新增 Lua 脚本 [oauth-migrate-issue.lua](../src/main/resources/scripts/oauth-migrate-issue.lua) 原子完成（与现有 [oauth-refresh-issue.lua](../src/main/resources/scripts/oauth-refresh-issue.lua) 同一套路）。
- 只替换迁移专用的那一条：映射 key 不存在时不触碰该用户的其他授权记录，因此不影响通过 `/oauth2/direct-user` 正常登录产生的凭证。

结果：`(uid, clientId)` 上的迁移凭证恒为最新一条，重复兑换不产生堆积。

### 5.5 新增错误码

延续 [ResultCode.java](../src/main/java/com/orange/common/enums/ResultCode.java) 的 9xxxx 段：

| 码 | 枚举名 | 消息 |
|---|---|---|
| 90015 | `OAUTH_MIGRATE_DISABLED` | 迁移兑换接口未开启 |
| 90016 | `OAUTH_MIGRATE_SIGN_INVALID` | 迁移请求签名校验失败 |
| 90017 | `OAUTH_MIGRATE_REPLAY` | 迁移请求已过期或已被使用 |

### 5.6 新增配置项

```yaml
user-center:
  oauth:
    migrate:
      # 总开关：默认关闭，上线时显式打开，迁移结束后立即关闭
      enabled: false
      # 认证方式（见 5.3）：本期固定 ed25519
      auth-mode: ed25519
      # 当前生效的公钥标识（轮换用）
      active-kid: k1
      # kid → Ed25519 公钥 映射（轮换期可同时配置多把旧公钥）；UC 只存公钥，从环境变量注入
      verify-keys: ${UC_OAUTH_MIGRATE_VERIFY_KEYS:}
      # 允许的时间偏移（秒）：公网暴露下取小值，同时决定 nonce 的 TTL（该值 ×2）
      clock-skew-seconds: 120
      # 来源 IP 白名单（逗号分隔）：公网暴露下必配，只放行 BackEndV3 固定出口 IP
      ip-allowlist: ""
      # 限流：网关层 per-ip 与应用层 per-uid / per-client
      per-ip-per-minute: 600
      per-uid-per-minute: 5
      per-client-per-minute: 3000
```

---

## 6. UC 侧改动清单

| # | 事项 | 位置 | 说明 |
|---|---|---|---|
| M1 | 新增迁移兑换端点 | 新增 `src/main/java/com/orange/controller/oauth/OAuthInternalController.java`（映射 `/oauth2/internal`） | 独立 Controller，便于网关按路径前缀单独配置 HTTPS / 限流与日志隔离；不要挂到 `OAuthLegacyLoginController` 上 |
| M2 | 新增迁移服务实现 | 新增 `src/main/java/com/orange/service/OAuthMigrateService.java` 与 `service/impl/OAuthMigrateServiceImpl.java` | 完成 5.2 的 12 步编排；认证层核验、防重放、审计集中于此 |
| M3 | 新增 Ed25519 验签工具 | `src/main/java/com/orange/common/util/SignUtil.java` | 新增 `ed25519Verify(publicKey, data, sig)`（JDK 15+ 内置 `Ed25519`，无需第三方库）；现有文件仅有 `sha256` / `generateToken` |
| M4 | 新增原子替换 Lua 脚本 | `src/main/resources/scripts/oauth-migrate-issue.lua` | 查映射 → 撤旧 → 签新 → 写映射，原子完成 |
| M5 | 新增迁移凭证原子存储方法 | `src/main/java/com/orange/service/OAuthTokenStore.java` 与 `service/impl/RedisOAuthTokenStore.java` | 按现有 `issueAccessFromRefresh` 的写法新增 `issueMigratedToken(...)` |
| M6 | 复用令牌签发 | `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java` 的 `issueDirectToken` | 不修改该方法，迁移直接调用 |
| M7 | 新增 3 个错误码 | `src/main/java/com/orange/common/enums/ResultCode.java` | 90015 / 90016 / 90017 |
| M8 | 新增 Redis key 构建 | `src/main/java/com/orange/common/util/RedisKeyUtil.java` | `uc:oauth:migrate:nonce:{nonce}`、`uc:oauth:migrate:current:{clientId}:{uid}`（新增 `PREFIX_OAUTH_MIGRATE_*` 常量，保持本文件「前缀集中管理」的既有约定） |
| M9 | 新增配置项 | `application.yml` 与 `application-test.yml` | 见 5.6；Ed25519 公钥走环境变量 |
| M10 | 台账复用 | `src/main/java/com/orange/mapper/OAuthGrantMapper.java` | 复用已有 `markRevokedByTokenHash`，无需新增方法 |
| M11 | 最小响应 VO | 新增 `src/main/java/com/orange/entity/vo/oauth/MigrateTokenVO.java` | 只回 `uid + 令牌 + scope`，不回带昵称 / 头像 / 邮箱（见 5.2） |
| M12 | 无需 DDL | — | 不新增表、不新增列；复用现有 `oauth_client.direct_auth_enabled` |
| M13 | 网关配置（运维项） | Nginx / 网关 | 强制 HTTPS；收紧 `client_max_body_size`；接入 WAF 与按 IP 限流（见 7.3） |
| M14 | 脱敏与审计 | 新增审计落库 + `LogUtil` | 只记 `uid / client_id / origin / legacy_token_hash / IP / kid / 结果`；签名原文、令牌明文一律不入日志 |
| M15 | 文档同步 | `docs/OAuth2与标准协议差距清单.md` | 记录该非标准内部端点、认证方式，以及公网可达这一事实与其加固要求 |
| M16 | 不动 | `OAuthController`、`AuthServiceImpl.directUser`、admin / open-api 体系 | 迁移不改变任何既有协议端点的行为 |

> 注意：`/oauth2/internal/**` 不要加入 [WebConfig.java](../src/main/java/com/orange/interceptor/WebConfig.java) 的任何拦截器注册列表；认证完全由端点自带（5.3），拦截器只会产生干扰。

---

## 7. 上线前核对

### 7.1 客户端配置（硬前提，配错则刷新必失败）

`OAuthTokenServiceImpl.refreshToken` 会依次要求：令牌归属该 client → 客户端启用 → `grant_types` 含 `refresh_token` → `client_secret_post` 校验通过。

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

### 7.2 关于开关复用（已定：复用 `direct_auth_enabled`）

迁移端点复用 `direct_auth_enabled` 作为准入开关，不新增字段、不做 DDL。理由：该字段语义已是「允许旧系统直连认证能力」，迁移正是这条链路的一部分；且迁移是一次性动作，不值得为其引入长期字段。

> 独立开关（`oauth_client.migrate_enabled`）已评估并放弃：需要 DDL，且迁移结束后该字段即成为无用负担。

### 7.3 公网暴露加固（必做）

BackEndV3 与 UC 是两台不同服务器，无法只走内网，网络隔离不可用。以下为硬要求：

| # | 要求 | 说明 |
|---|---|---|
| 1 | 强制 HTTPS | 明文 HTTP 一律拒绝；TLS ≥ 1.2；禁止该路径被 CDN / 缓存层缓存 |
| 2 | 出口 IP 白名单（必配） | BackEndV3 出网 IP 已确认固定，`ip-allowlist` 只放行该 IP，作为认证层之外的第二道防线 |
| 3 | 认证层 = Ed25519（已定） | 按 5.3 实现非对称验签，UC 侧只存公钥；不得退化回共享密钥 |
| 4 | 时间窗收紧 | `clock-skew-seconds` 由 300 降到 120（依赖双方 NTP 对时）；窗口越小，重放窗口越小 |
| 5 | 网关层限流 | 在 WAF / 网关先按 IP 限流（如 600/分钟），应用层限流作为第二道 |
| 6 | 失败即告警 | 认证失败、重放拒绝、白名单拒绝、90015 探测任一指标超阈值立即告警——这是唯一的入侵信号 |
| 7 | 密钥独立、可轮换 | 签名私钥独立于 `client_secret`；UC 侧按 `kid` 维护公钥映射，支持轮换；怀疑私钥泄漏时先关闭 `enabled`，再更换密钥对 |
| 8 | 最小响应 | 只返回令牌，不回带用户资料（见 5.2），缩小响应被截获后的信息面 |
| 9 | 用完即关 | 存量迁移完成后立即关闭 `enabled`，并摘除网关 location——该端点不应长期存在 |

> 密钥要求：Ed25519 密钥对——私钥仅 BackEndV3 持有（32 字节随机生成），UC 侧只存对应公钥。均只从环境变量 / 密钥管理注入，不进仓库、不进日志。

---

## 8. 测试与验收

### 8.1 UC 侧单测

- 认证层：正确签名通过；篡改 `uid` / `client_id` / `ts` / 方法或路径后的签名全部返回 90016；未知 `kid` 拒绝；轮换期内的旧 `kid` 通过、摘除后拒绝。
- 时效与重放：`ts` 恰好在窗口边界内 / 外；同一 `nonce` 第二次使用返回 90017。
- 来源与协议：IP 不在白名单返回 80008；非 HTTPS 直接拒绝。
- 开关：`enabled=false` 返回 90015，且在认证校验之前返回（不泄露任何配置细节）。
- 客户端：`grant_types` 缺 `refresh_token` 时兑换仍成功但后续刷新失败——用例必须覆盖该组合，这是本方案最容易踩的配置陷阱。
- 用户状态：`status < 0` 返回 20004，且不产生任何令牌与台账记录。
- 幂等：同一 `(uid, client_id)` 连续兑换 3 次，台账始终只有 1 条有效记录，旧的被置为已吊销，反向索引无残留。
- 反向验证：迁移不影响该用户通过 `/oauth2/direct-user` 正常登录产生的凭证。

### 8.2 回归

- `/oauth2/direct-session`、`/oauth2/direct-login`、`/oauth2/direct-register`、`/oauth2/direct-user`、`/oauth2/token`、`/oauth2/revoke`、`/oauth2/userinfo`、`/oauth2/authorize`、`/oauth2/consent` 行为不变。
- `migrate.enabled=false` 时该端点整体不可用（90015）。

### 8.3 端到端（UC 侧需配合验证的条目）

1. 打开新前端（localStorage 无 `UC_ACCESS_TOKEN`）→ 前端主动调 BackEndV3 `POST /auth/uc-token/issue` → 兑换成功、拿到 UC access_token；同一时刻的 BackEndV3 业务请求照常成功，用户无感。
2. 该 access_token 调 UC `/oauth2/userinfo` 成功（验证前端直连 UC 可用）。
3. 清除本地 `UC_ACCESS_TOKEN` 后再次进入，命中 BackEndV3 侧缓存，不产生第二次兑换（UC 审计中仍只有一条）。
7. 多端验证：同一账号两个设备登录，各自的自签 token 都能独立刷新、互不踢下线；第二台设备触发兑换后，第一台的凭证仍能刷新成功（验证缓存按 uid 维度，见 R17）。
8. UC 侧「我的授权」能看到该 client 的记录，且从 UC 侧撤销后刷新失败。
9. 双撤验证（R14）：登出后自签 token 与 UC 刷新能力都失效。

> 完整端到端清单见 `./BackEndV3侧实施方案.md` 第 9 节。

---

## 9. 风险与缓解（UC 侧相关）

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | 签名私钥泄漏即为任意 uid 发令牌 | 攻击者可冒充全部 5 万用户 | 已用 Ed25519 非对称签名（5.3）：UC 侧只有公钥，UC 配置泄漏不足以伪造，攻击面收敛到 BackEndV3 的单一私钥。配套：IP 白名单、时间窗 + nonce 防重放、三层限流、审计；`kid` 轮换；怀疑泄漏时先关闭 `enabled` 再更换密钥对 |
| R2 | 端点被当成绕过封禁的后门 | 已封禁用户续命 | 5.2 第 8 步强制校验 `userinfo.status`，且该校验在签发之前 |
| R5 | 多标签页 / 重试并发兑换 | refresh 记录与台账堆积 | 5.4 的映射 key + Lua 原子替换；BackEndV3 侧再加并发去重 |
| R8 | 迁移后 5 万用户的 refresh 记录集中过期 | 90 天后集中失效 | 迁移是响应式的、时间天然分散；如仍担心，可对迁移凭证单独设置更长的 `refresh_token_ttl` |
| R9 | 数据落地合规 | 审计表含 uid + 来源 IP | 审计只记 `uid / client_id / origin / legacy_token_hash(摘要) / IP`，不记原始旧 token |
| R10 | 端点被公网扫描 / 爆破 | 持续失败请求、日志噪声、潜在 DoS | 网关按 IP 限流 + 失败告警；`enabled` 默认关闭、迁移结束立即关闭；使用不可猜测路径只能降噪，不构成防护 |
| R11 | 响应在公网被截获 | 泄漏令牌 | 强制 HTTPS；响应最小化（不回带用户资料，见 5.2）；令牌可被撤销，且迁移是一次性动作 |
| R12 | ~~出口 IP 漂移导致白名单失效~~（已关闭） | — | BackEndV3 出网 IP 已确认固定，白名单可生效；且认证层已是 Ed25519，即使白名单误配也不会直接失守（纵深防御） |

---

## 10. 迁移时间线（UC 侧视角）

| 阶段 | 内容 | UC 侧产出 |
|---|---|---|
| T0 | UC 侧 M1–M16 上线（`migrate.enabled` 默认关闭） | 接口就绪但不可用 |
| T1 | 双方联调：Ed25519 签名、错误码、限流、审计、兑换接口契约与 CORS | 各环境跑通 |
| T2 | 打开 `migrate.enabled`，**前端灰度发版**（1% → 10% → 100%，触发点在前端） | 加载到新前端的存量用户自动完成迁移 |
| T3 | 观察期：看 UC 授权覆盖率（活跃 uid 是否都已建好 UC 授权）与兑换失败率（应约等于 0） | 活跃用户已全部持有 UC 授权 |
| T4 | 观察期结束：清 BackEndV3 侧 `uc:migrate:issued:*` 兑换缓存；关闭 `migrate.enabled`；摘除网关 location | 迁移通道下线，UC 协议端点回到既有状态 |

> `loginToken:*` 与 `token_record(type=login)` 保留不动（自签体系长期在役）。

---

## 11. 决策记录（UC 侧相关）

| # | 决策 | 依据 |
|---|---|---|
| 1 | 认证层 = Ed25519 非对称签名（UC 侧只存公钥），不采用共享密钥 | 5.3——跨公网下共享密钥任一侧泄漏即可伪造 |
| 2 | BackEndV3 出网 IP 固定，`ip-allowlist` 作为第二道防线必配 | 7.3 |
| 3 | 令牌下发方式 = 前端主动触发 + 专用接口响应体返回（`POST /auth/uc-token/issue`） | 详见 `./BackEndV3侧实施方案.md` 第 6 节——触发点与业务解耦、失败不影响业务请求 |
| 4 | 迁移准入开关复用 `oauth_client.direct_auth_enabled`，不新增字段、不做 DDL | 7.2 |
| 5 | UC 不实现 introspection 端点 | 4.1——BackEndV3 不校验 UC 令牌，消费方不存在 |

---

## 12. 附录：UC 侧关键代码位置索引

| 用途 | 位置 |
|---|---|
| 直连登录四端点 | `src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java` |
| 直连兑换实现（迁移要复用的签发路径） | `src/main/java/com/orange/service/impl/AuthServiceImpl.java` 的 `directUser` |
| 令牌签发核心（access / refresh / 索引 / 台账） | `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java` 的 `issueDirectToken` |
| 刷新链路校验顺序（决定 7.1 的前置条件） | `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java` 的 `refreshToken` |
| 台账写入与摘要 | `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java` 台账写入段 |
| 整体撤销（替换语义参考） | `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java` 授权撤销段 |
| 一次性凭证原子存储 | `src/main/java/com/orange/service/OAuthTokenStore.java`、`src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java` |
| 现有 Lua 脚本（新脚本的写法参考） | `src/main/resources/scripts/oauth-refresh-issue.lua`、`src/main/resources/scripts/oauth-code-consume.lua` |
| Redis key 前缀集中管理 | `src/main/java/com/orange/common/util/RedisKeyUtil.java` |
| 签名 / 摘要工具 | `src/main/java/com/orange/common/util/SignUtil.java` |
| 错误码分段 | `src/main/java/com/orange/common/enums/ResultCode.java`（9xxxx 段） |
| 客户端实体（含 `directAuthEnabled`） | `src/main/java/com/orange/entity/po/OAuthClient.java` |
| 台账实体 | `src/main/java/com/orange/entity/po/OAuthGrant.java` |
| 拦截器注册（不得纳入 internal 端点） | `src/main/java/com/orange/interceptor/WebConfig.java` |
