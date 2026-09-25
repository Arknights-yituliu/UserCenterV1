# BackEndV3 与 UC 令牌统一方案

| 项目 | 内容 |
|---|---|
| 文档版本 | V1.0 |
| 状态 | 已定稿，可进入实施 |
| 涉及系统 | 酸橙云 / UC（UserCenterV1，OAuth2 授权服务器，包名 `com.orange`）<br>BackEndV3（业务后端，包名 `com.lhs`） |
| 目标读者 | 方案实施者、UC 与 BackEndV3 双方开发、运维 |
| 上游文档 | `.yama/统一令牌改造计划.md`（其目标已被本方案改写，需按第 12 节回馈项同步修订） |

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
| 半天内迁完 5 万登录态 | 可以达成，但口径须改为「半天内上线迁移通道，且不强制任何人重登」。登录态迁移在本质上是响应式的（见 9.1），通道上线即生效，活跃用户几分钟内切换完成，长尾用户随回访自动完成 |

### 1.3 一句话方案

BackEndV3 本身持有「自签 token → uid」的映射。用户带着自签 token 请求时，BackEndV3 现场用 Ed25519 签名向 UC 换取一对全新的 UC 令牌，把 access_token 回带给前端（供前端调用 UC 接口），refresh_token 留在服务端。不导出数据、不对账、不改 UC 表结构，前端凭据也无需更换。

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

key 形态见 `src/main/java/com/orange/common/util/RedisKeyUtil.java`，写入见 `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java`。因此把外部字符串塞进去当作 refresh_token 是能运行的。

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

## 4. 目标架构

### 4.1 整体链路

```
【存量用户首次请求（迁移发生在这里）】
前端 ──Authorization: Bearer <BackEndV3 自签 token>──▶ BackEndV3
BackEndV3 拦截器：
  ① 查本地 Redis loginToken:{token} → uid（现有逻辑，零改动）
  ② 查 uc:migrate:issued:{uid} 是否已兑换过（uid 维度，与 UC 侧幂等模型对齐，见 B4）
       命中   → 复用上次取得的 UC 令牌，不重复调用 UC
       未命中 → 调 UC POST /oauth2/internal/migrate-token（Ed25519 签名 + IP 白名单）
                UC：校验签名/时效/来源 → 校验用户状态 → 撤销上一轮迁移凭证 → 签发全新 UC 令牌对
                ◀── access_token + refresh_token（仅服务端间传输）
                refresh_token 加密落库；写 uc:migrate:issued:{uid} 缓存
  ③ 响应头回带 X-UC-Access-Token（供前端调 UC 接口；refresh_token 不下发浏览器）
  ④ 自签 token 继续作为 BackEndV3 的会话凭据，长期保留、不删除（见 7.1）
前端：保存回带的 UC access_token（调 UC 用），调 BackEndV3 仍用自签 token —— 用户全程无感

【此后：两套令牌各司其职】
前端 ──Bearer <自签 token>──▶ BackEndV3 → 查本地 loginToken → uid
       （不经 UC 校验，UC 挂机不影响 BackEndV3 业务）
前端 ──Bearer <UC access_token>──▶ UC 接口（/oauth2/userinfo、干员数据等）

【UC access_token 过期（2h）】
前端 ──POST /auth/token/refresh（带自签 token）──▶ BackEndV3
BackEndV3：查 loginToken:{token} → uid → 取服务端 refresh_token → UC /oauth2/token
           ◀── 新 UC access_token → 回带前端
           （refresh_token 不轮换，本地记录无需更新）

【登出】BackEndV3 → 删 loginToken + UC /oauth2/revoke（双撤，见 R14）
```

### 4.2 权威边界

| 事项 | 权威方 | 说明 |
|---|---|---|
| 会话有效性（这台设备是否仍登录） | BackEndV3 自签 token | 长期常态，非过渡期约束 |
| 授权范围（该 uid 拥有哪些权限） | UC | 由 UC 签发与撤销 |
| 登出 / 封禁 | 双方 | 必须双撤，缺一即为安全漏洞（R14） |

**鉴权路径声明（BackEndV3 侧，全程不变）**

- BackEndV3 的请求鉴权**始终**依据本地 Redis `loginToken:{token} → uid`（命中即通过），**不调用 UC、不校验 UC 令牌**。
- UC access_token 只用于前端调用 UC 接口，**不参与 BackEndV3 的任何鉴权判断**。
- 由此 BackEndV3 不依赖 UC 的可用性：UC 宕机或两端网络中断时，BackEndV3 的业务与用户登录态不受影响（对应用户侧表现见 4.1 链路图第二段）。
- 这是本轮方案与上游计划差异最大的一点：上游要求 BackEndV3 改为校验 UC 令牌（上游 §5.2 B5/B6/B14），**本次不做**（见 13.2 第 3、4 条）。
- 直接推论：UC 侧 introspection 端点（RFC 7662）**本次不实现**——它唯一的用途是供 BackEndV3 校验 UC 令牌，该调用方已不存在（见 5.1）。

---

## 5. UC 侧接口定义（核心）

### 5.1 端点总览

| 端点 | 变更 | 说明 |
|---|---|---|
| `POST /oauth2/internal/migrate-token` | 新增 | 迁移专用兑换：凭 uid 现场签发一对 UC 令牌 |
| `POST /oauth2/direct-*` | 不变 | 新登录链路继续使用 |
| `POST /oauth2/token` | 不变 | 刷新继续使用 |
| `POST /oauth2/revoke` | 不变 | 登出继续使用 |
| `POST /oauth2/introspect` | 本次不实现 | 仅当 BackEndV3 需校验 UC 令牌时才必要；本轮 BackEndV3 仍以本地 `loginToken` 鉴权，**两边均无调用方**（见 4.2 鉴权路径声明、13.2 第 3 条） |

> 注意：上述 `/oauth2/internal/migrate-token` 端点**跨公网可达**（BackEndV3 与 UC 是两台不同服务器，无法只走内网）。因此**认证层是唯一的准入屏障**（见 5.3）；网络层只做加固（TLS + IP 白名单 + 网关限流），不能视为屏障。UC 自身不为该路径注册任何拦截器（`src/main/java/com/orange/interceptor/WebConfig.java` 当前仅注册 `/user/**`、`/auth/logout`、`/oauth2/userinfo` 等），认证完全由端点自带。

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

**响应**（HTTP 200）：这是 UC → BackEndV3 的服务端间响应（面向浏览器的响应头契约另见 7.1）。为缩小公网暴露下的响应面，不复用 `ServerLoginVO`，改用最小响应 VO `MigrateTokenVO{uid, access_token, token_type, expires_in, refresh_token, scope}`，**不回带昵称 / 头像 / 邮箱**。用户资料由 BackEndV3 持新 access_token 调 `/oauth2/userinfo` 获取（该端点已有拦截器保护）。

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
| 7 | 客户端校验：`oauth_client` 存在、`owner_enabled=1`、`admin_approved=1`、`direct_auth_enabled=1`（复用现有直连开关，见 10.2） | 90001 / 90013 / 90014 |
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
- 上述「查映射 → 撤旧 → 签新 → 写映射」由新增 Lua 脚本 `src/main/resources/scripts/oauth-migrate-issue.lua` 原子完成（与现有 `oauth-refresh-issue.lua` 同一套路）。
- 只替换迁移专用的那一条：映射 key 不存在时不触碰该用户的其他授权记录，因此不影响通过 `/oauth2/direct-user` 正常登录产生的凭证。

结果：`(uid, clientId)` 上的迁移凭证恒为最新一条，重复兑换不产生堆积。

### 5.5 新增错误码

延续 `src/main/java/com/orange/common/enums/ResultCode.java` 的 9xxxx 段：

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
| M13 | 网关配置（运维项） | Nginx / 网关 | 强制 HTTPS；收紧 `client_max_body_size`；接入 WAF 与按 IP 限流（见 10.3） |
| M14 | 脱敏与审计 | 新增审计落库 + `LogUtil` | 只记 `uid / client_id / origin / legacy_token_hash / IP / kid / 结果`；签名原文、令牌明文一律不入日志 |
| M15 | 文档同步 | `docs/OAuth2与标准协议差距清单.md` | 记录该非标准内部端点、认证方式，以及公网可达这一事实与其加固要求 |
| M16 | 不动 | `OAuthController`、`AuthServiceImpl.directUser`、admin / open-api 体系 | 迁移不改变任何既有协议端点的行为 |

> 注意：`/oauth2/internal/**` 不要加入 `src/main/java/com/orange/interceptor/WebConfig.java` 的任何拦截器注册列表；认证完全由端点自带（5.3），拦截器只会产生干扰。

---

## 7. BackEndV3 侧改动清单

> 本仓库不含 BackEndV3 源码，以下基于上游文档 `统一令牌改造计划.md` 的既有结论推导，实施前需在 BackEndV3 侧逐条核对。

| # | 事项 | 位置（上游文档所述） | 说明 |
|---|---|---|---|
| B1 | 配置 Ed25519 私钥与 UC 地址 | `application-test.yml` + 环境变量 | 签名私钥（仅本侧持有）与 `kid`；UC 的 HTTPS 地址（`user-center.oauth.migrate.base-url`） |
| B2 | 拦截器新增「触发兑换」分支 | `src/main/java/com/lhs/interceptor/UserInterceptor.java` | 命中 `loginToken:{token}` 后触发首次兑换，此后走 B4 缓存；鉴权路径本身完全不变（仍查本地 `loginToken`，不校验 UC 令牌） |
| B3 | 新增迁移客户端 | 新增 `UcMigrateClient` | 组装 5.3 的 canonical string，用 Ed25519 私钥签名（带 `kid` / `ts` / `nonce`）；HTTPS 调用；超时与重试要短（见 R3） |
| B4 | 兑换结果缓存 | 拦截器 + Redis | 兑换成功写 `uc:migrate:issued:{uid}` → UC 令牌对。**key 必须是 uid 维度，不要用 `sha256(自签 token)`**：UC 侧幂等模型是 `(uid, client_id)` 上恒一条迁移凭证（5.4），每次兑换都会无条件撤销上一轮凭证；缓存若按自签 token 分片，同一用户多设备会各兑换一次并互相撤销，旧设备刷新时拿到 90009 掉登录（见 R17）。uid 维度下同一用户全程只兑换一次，缓存命中直接复用，避免重复兑换击穿 UC 侧 5/分钟限流；access 过期经刷新后需同步更新缓存中的 access_token，否则缓存会一直回带过期令牌 |
| B5 | 令牌下发方式（已定 A） | `controller/UserController.java` + 拦截器 + CORS 配置 | 按 7.1 的响应头契约回带 `X-UC-Access-Token` 等；务必修 `Access-Control-Expose-Headers` |
| B6 | 并发去重 | 同上 | 同一前端同一时刻只允许一次兑换（进程内锁 + Redis 短锁），避免多标签页放大请求量 |
| B7 | 失败降级 | 拦截器 | 兑换失败（如 UC 不可用）时不得影响用户：本次请求照常放行（自签 token 校验本就通过），异步重试兑换；连续失败达阈值再告警。用户不会因此掉登录 |
| B8 | 新登录保持现状并接住 UC 令牌 | `DirectLoginServiceImpl` / `OAuthUserServiceImpl.createSessionByOAuth2Uid` | 继续签发自签 token（会话凭据不变）；同时把 `/oauth2/direct-user` 返回的 UC 令牌接住、refresh_token 加密落库。上游 B1/B2 中「接住令牌」部分仍要做，「停止签发自签 token」部分不做 |
| B9 | 观测埋点 | — | 自签 token 命中量、兑换成功率、兑换耗时、降级次数（见 9.3） |
| B10 | 不再需要增量同步任务 | — | 本方案的核心收益点 |
| B11 | 不再清理自签 token | — | 本次决策的直接后果：自签 token 已是刷新凭据，必须长期保留；仅 `uc:migrate:issued:{uid}` 兑换缓存在存量迁移完成后可清（它只用于防重复兑换，清掉后用户下次请求会重新兑换一次并按 B4 重写缓存，凭证恒一条，无害） |
| B12 | 刷新凭据复用自签 token | `UserController` + `OAuthUserServiceImpl` | `/auth/token/refresh` 按请求携带的自签 token 查 `loginToken:{token}` 得 uid → 取服务端 refresh_token → 调 UC 刷新。不新建凭证表、不改索引、不引入 `sessionId` |

### 7.1 令牌下发方式（已定：A. 随业务响应回带）

拦截器在业务响应头回带 UC access_token，前端将其存起来供调 UC 接口使用；调 BackEndV3 的请求不需要切换凭据。

**响应头契约（BackEndV3 → 前端）**

| 响应头 | 说明 |
|---|---|
| `X-UC-Access-Token` | 新签发的 UC access_token（前端调 UC 接口用） |
| `X-UC-Token-Expires-In` | 有效期（秒） |
| `X-UC-Token-Scope` | 可选，scope |

**刷新凭据 = BackEndV3 原有的自签 token（不新建概念）**

- 前端不需要、也不能持有 UC 的 refresh_token：`arknights-yituliu-web` 是机密客户端，`OAuthTokenServiceImpl.refreshToken` 要求 `client_secret`，浏览器调 UC `/oauth2/token` 必然被拒（上游 §2.5 已确认）。刷新只能由 BackEndV3 服务端代做。
- 前端需要一个「让 BackEndV3 认出这是哪条会话」的凭据，直接复用现有自签 token：`loginToken:{token} → uid` 本来就在用，`/auth/token/refresh` 按它反查 uid 即可。
- 不新造 `sessionId`，不改表结构、不动 Redis key 布局——自签 token 天然「一设备一条」，多端登录无需任何改动。

> 顺带绕开了上游计划 B3 的 `uk(uid, client_id)` 陷阱：该唯一约束会把多端登录变成单端互踢（现有 `loginToken:{token} → uid` 支持多端）。复用自签 token 即无需建凭证表、也无需改索引（见 12.3）。

**两个必须注意的实现点**

1. CORS：前端与 BackEndV3 跨域时，必须在 CORS 配置的 `Access-Control-Expose-Headers` 中暴露上述响应头，否则浏览器 JS 读不到自定义头（极易遗漏）。
2. 禁止缓存：响应头携带令牌，必须确保 `Cache-Control: no-store`，中间代理不得缓存这些头。

**两条明确的禁止项**

- 绝不要用 UC access_token 当刷新凭据：access 过期后令牌会轮换、映射断链；且映射条目随刷新无限增长（5 万用户 × 90 天约 5400 万条），不可接受。
- 必须认下代价：自签 token 一旦成为刷新凭据就永远不能删——旧自签体系（`tokenGenerator` / `loginToken` / `token_record(type=login)`）必须长期维护，上游计划「自签链路彻底移除」的目标不再成立（见 12.3）。

> 备选 B（401 触发）未采用：多一次往返，且首次 401 会被日志与监控记为错误。

---

## 8. 前端改动

| # | 事项 | 说明 |
|---|---|---|
| F1 | 登录流程不变 | 仍走 `/direct-session` → UC 登录页 → `/complete-login`；响应中拿到自签 token（同现状）与 UC access_token |
| F2 | 令牌存储 | 自签 token 放 localStorage（会话凭据，与现状一致）；UC access_token 放内存 / sessionStorage（2h，仅用于调 UC 接口）；refresh_token 不下发浏览器 |
| F3 | 识别令牌回带 | 监听响应头 `X-UC-Access-Token`（已定的方式 A），发现即存起来供调 UC 用。不需要重试原请求，也不需要更换发给 BackEndV3 的凭据 |
| F4 | UC 令牌过期处理 | 调 UC 接口遇 401 → 带自签 token 调 BackEndV3 `/auth/token/refresh` → 拿到新 UC access_token → 重试一次；失败则提示需重新登录 |
| F5 | 并发刷新去重 | 同一时刻只允许一个刷新 / 兑换请求，其余排队复用结果 |
| F6 | 登出 | 调 BackEndV3 `/auth/user/logout`，清理本地存储（自签 token + UC access_token） |
| F7 | 不需要清理旧 key | 自签 token 是长期会话凭据，前端无需移除任何既有存储逻辑 |

---

## 9. 迁移流程与「半天」口径

### 9.1 为什么是响应式

登录态迁移只能在用户下一次请求时发生——用户不在线时，没有任何通道能把新令牌塞进他的浏览器。因此：

- 错误口径：「半天内把 5 万个 token 换完」——做不到，也不必要（非活跃用户本就应等他回访）。
- 正确口径：「半天内上线迁移通道，并承诺不强制任何人重登」——按本方案，通道上线即生效。

### 9.2 迁移时间线

| 阶段 | 内容 | 产出 |
|---|---|---|
| T0 | UC 侧 M1–M16 上线（`migrate.enabled` 默认关闭） | 接口就绪但不可用 |
| T1 | 双方联调：Ed25519 签名、错误码、限流、审计、响应头与 CORS | 各环境跑通 |
| T2 | 打开 `migrate.enabled`，BackEndV3 灰度放量（1% → 10% → 100%） | 存量用户随访问自动迁移 |
| T3 | 观察期：看 UC 授权覆盖率（活跃 uid 是否都已建好 UC 授权）与兑换失败率（应约等于 0）。注意自签 token 命中量不会降到 0——它是长期会话凭据，不可用作迁移完成的判据 | 活跃用户已全部持有 UC 授权 |
| T4 | 观察期结束：清 `uc:migrate:issued:*` 兑换缓存；关闭 `migrate.enabled`；摘除网关 location。`loginToken:*` 与 `token_record(type=login)` 保留不动（自签体系长期在役，见 7.1） | 迁移通道下线，自签链路转为常态 |

### 9.3 观测指标

- **UC 授权覆盖率**（应单调上升至约 100%）——本次迁移的唯一完成判据。
- 自签 token 命中量（不会趋 0，仅供容量参考，不应视为指标异常）。
- 兑换请求量、兑换成功率、P99 耗时。
- 兑换降级次数（UC 不可用导致的放行）。
- `nonce` 重放拒绝次数、签名失败次数、IP 白名单拒绝次数——这三个指标异常升高意味着有人在扫描该端点。
- 同一 uid 的重复兑换次数。uid 维度缓存（B4）生效时该值应恒为 0，出现正值意味着缓存丢失（Redis 重启 / 被清）或并发去重失效。
- UC 令牌刷新成功率、刷新失败原因分布（自签 token 失效 vs UC 拒绝）。

---

## 10. 上线前核对

### 10.1 客户端配置（硬前提，配错则刷新必失败）

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

### 10.2 关于开关复用（已定：复用 `direct_auth_enabled`）

迁移端点复用 `direct_auth_enabled` 作为准入开关，不新增字段、不做 DDL。理由：该字段语义已是「允许旧系统直连认证能力」，迁移正是这条链路的一部分；且迁移是一次性动作，不值得为其引入长期字段。

> 独立开关（`oauth_client.migrate_enabled`）已评估并放弃：需要 DDL，且迁移结束后该字段即成为无用负担。

### 10.3 公网暴露加固（必做）

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

## 11. 测试与验收

### 11.1 UC 侧单测

- 认证层：正确签名通过；篡改 `uid` / `client_id` / `ts` / 方法或路径后的签名全部返回 90016；未知 `kid` 拒绝；轮换期内的旧 `kid` 通过、摘除后拒绝。
- 时效与重放：`ts` 恰好在窗口边界内 / 外；同一 `nonce` 第二次使用返回 90017。
- 来源与协议：IP 不在白名单返回 80008；非 HTTPS 直接拒绝。
- 开关：`enabled=false` 返回 90015，且在认证校验之前返回（不泄露任何配置细节）。
- 客户端：`grant_types` 缺 `refresh_token` 时兑换仍成功但后续刷新失败——用例必须覆盖该组合，这是本方案最容易踩的配置陷阱。
- 用户状态：`status < 0` 返回 20004，且不产生任何令牌与台账记录。
- 幂等：同一 `(uid, client_id)` 连续兑换 3 次，台账始终只有 1 条有效记录，旧的被置为已吊销，反向索引无残留。
- 反向验证：迁移不影响该用户通过 `/oauth2/direct-user` 正常登录产生的凭证。

### 11.2 回归

- `/oauth2/direct-session`、`/oauth2/direct-login`、`/oauth2/direct-register`、`/oauth2/direct-user`、`/oauth2/token`、`/oauth2/revoke`、`/oauth2/userinfo`、`/oauth2/authorize`、`/oauth2/consent` 行为不变。
- `migrate.enabled=false` 时该端点整体不可用（90015）。

### 11.3 端到端

1. 用存量自签 token 调 BackEndV3，响应头回带 `X-UC-Access-Token`，原请求成功，用户无感。
2. 该 access_token 调 UC `/oauth2/userinfo` 成功（验证前端直连 UC 可用）。
3. 同一自签 token 再次请求命中 B4 缓存，不产生第二次兑换（UC 审计中仍只有一条）。
4. 跨域场景验证 `Access-Control-Expose-Headers` 已生效，浏览器 JS 能读到该响应头。
5. 等 UC access 过期 → 调 UC 接口 401 → 带自签 token 调 `/auth/token/refresh` 成功；再把 access TTL 临时调成 60s 连续刷 5 次仍成功（验证刷新不依赖 UC access_token、不会断链）。
6. 删除 `loginToken:{token}`（模拟会话被登出）后刷新失败，前端提示重新登录。
7. 多端验证：同一账号两个设备登录，各自的自签 token 都能独立刷新、互不踢下线；第二台设备触发兑换后，第一台的凭证仍能刷新成功（验证 B4 的 uid 维度缓存，见 R17）。
8. UC 侧「我的授权」能看到该 client 的记录，且从 UC 侧撤销后刷新失败。
9. 双撤验证（R14）：登出后自签 token 与 UC 刷新能力都失效。
10. 确认自签 token 长期有效：执行 T4 清理后自签 token 照常可用（与上一版预期相反，务必覆盖）。

---

## 12. 风险与缓解

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| R1 | 签名私钥泄漏即为任意 uid 发令牌 | 攻击者可冒充全部 5 万用户 | 已用 Ed25519 非对称签名（5.3）：UC 侧只有公钥，UC 配置泄漏不足以伪造，攻击面收敛到 BackEndV3 的单一私钥。配套：IP 白名单、时间窗 + nonce 防重放、三层限流、审计；`kid` 轮换；怀疑泄漏时先关闭 `enabled` 再更换密钥对 |
| R2 | 端点被当成绕过封禁的后门 | 已封禁用户续命 | 5.2 第 8 步强制校验 `userinfo.status`，且该校验在签发之前 |
| R3 | 兑换成为关键路径 | UC 抖动导致用户掉登录 | BackEndV3 侧降级放行（B7）+ 短超时 + 异步重试；连续失败才要求重登 |
| R4 | 自签 token 与 UC 授权长期并存 | 一份凭证两个权威 | 职责分离、长期常态（非过渡期约束）：UC 是授权权威（该 uid 有什么权限），自签 token 是会话权威（该设备是否仍登录）。登出 / 封禁必须双撤（UC `/oauth2/revoke` + 删本地 `loginToken`），见 R14 |
| R5 | 多标签页 / 重试并发兑换 | refresh 记录与台账堆积 | 5.4 的映射 key + Lua 原子替换；BackEndV3 侧再加并发去重（B6） |
| R6 | `grant_types` 漏配 `refresh_token` | 兑换成功但 2 小时后全体掉登录 | 10.1 上线前用 SQL 核对；此项纳入灰度前置检查 |
| R7 | 前端 token 权限过宽 | 浏览器持有 client 全量 scope | 与上游计划 R5 同一问题：收敛该 client 的 `scopes` |
| R8 | 迁移后 5 万用户的 refresh 记录集中过期 | 90 天后集中失效 | 迁移是响应式的、时间天然分散；如仍担心，可对迁移凭证单独设置更长的 `refresh_token_ttl` |
| R9 | 数据落地合规 | 审计表含 uid + 来源 IP | 审计只记 `uid / client_id / origin / legacy_token_hash(摘要) / IP`，不记原始旧 token |
| R10 | 端点被公网扫描 / 爆破 | 持续失败请求、日志噪声、潜在 DoS | 网关按 IP 限流 + 失败告警；`enabled` 默认关闭、迁移结束立即关闭；使用不可猜测路径只能降噪，不构成防护 |
| R11 | 响应在公网被截获 | 泄漏令牌 | 强制 HTTPS；响应最小化（不回带用户资料，见 5.2）；令牌可被撤销，且迁移是一次性动作 |
| R12 | ~~出口 IP 漂移导致白名单失效~~（已关闭） | — | BackEndV3 出网 IP 已确认固定，白名单可生效；且认证层已是 Ed25519，即使白名单误配也不会直接失守（纵深防御） |
| R13 | 前端漏存回带的 UC access_token | 前端调 UC 接口持续 401 / 不可用 | B4 缓存兜底：每次请求只走缓存分支，不会重复调 UC（UC 侧不会被击穿）；前端 401 后走 F4 刷新即可自愈；埋点统计「UC 接口 401 率」即可发现 |
| R14 | 封禁 / 登出只撤了一侧 | 用户被撤权后仍可用另一份凭证继续访问 | 双撤为必要步骤，且是长期常态（见 R4）；纳入验收用例（11.3 第 9 条） |
| R15 | 自签 token 泄漏 | 攻击者可换取 UC access_token，也可直接调 BackEndV3 | 未新增泄漏面（它本来就是会话凭据，风险等级与现状相同）。缓解沿用现状：TTL 90 天、登出即删 `loginToken` + UC 撤销、可绑 UA / IP。注意自签 token 为 `AES("{id:X}.X.timestamp")`，随机性来自时间戳，抗枚举弱于随机串——若要提升需更换凭据形态，本次不做 |
| R16 | 自签体系必须长期维护 | 上游「自签链路彻底移除」目标作废；AES 密钥、`tokenGenerator`、`loginToken`、`token_record(type=login)` 长期在役 | 本次决策的已知代价。必须在上游计划中显式改写目标与验收标准，否则后人会按旧目标误删代码（见 12.3） |
| R17 | 兑换缓存按自签 token 维度分片（多设备） | 同一用户多设备各兑换一次，第二次兑换会撤销第一次的迁移凭证；旧设备 UC access 过期后刷新得 90009，被要求重新登录，与「不强制重登」的承诺冲突。该场景必然发生（不是并发问题），B6 的锁挡不住 | B4 缓存改为 `uc:migrate:issued:{uid}`（uid 维度，与 UC 侧 `(uid, client_id)` 幂等模型对齐）：同一用户全程只兑换一次，UC 侧的撤销分支不再触发。附带约束（源于 5.4 恒一条凭证，与缓存维度无关）：任一设备登出触发 UC 撤销后，其他设备的刷新会以 90009 失败，需清 B4 缓存重新兑换自愈——自签 token 不受影响、用户不掉登录，但该自愈路径必须实现。验收见 11.3 第 7 条 |

---

## 13. 决策记录

### 13.1 已确认决策

| # | 决策 | 依据 |
|---|---|---|
| 1 | 认证层 = Ed25519 非对称签名（UC 侧只存公钥），不采用共享密钥 | 5.3——跨公网下共享密钥任一侧泄漏即可伪造 |
| 2 | BackEndV3 出网 IP 固定，`ip-allowlist` 作为第二道防线必配 | 10.3 |
| 3 | 令牌下发方式 = A. 随业务响应回带（响应头 `X-UC-Access-Token` 等） | 7.1——无额外往返 |
| 4 | 迁移准入开关复用 `oauth_client.direct_auth_enabled`，不新增字段、不做 DDL | 10.2 |
| 5 | BackEndV3 的会话凭据继续用自签 token，并兼作 UC 令牌的刷新凭据；不引入 `sessionId` | 7.1——前端直连 UC 需要 UC access_token，但刷新只能由服务端做；自签 token 现成可用、天然多端 |
| 6 | 自签 token 长期保留（原「观察期后统一删除」作废） | 决策 5 的直接后果——刷新凭据不能删 |

待定项：无，可进入实施。

### 13.2 需回馈上游计划的修订项

上游文档 `统一令牌改造计划.md` 的目标已被本轮决策大幅改写，实施前需同步修订：

| # | 上游位置 | 原内容 | 需如何改写 |
|---|---|---|---|
| 1 | §1.2 目标 1、§3.2 | 「前端只持 UC 令牌，不再携带自签 token」 | 改为「前端持两种令牌、各司其职」：自签 token 调 BackEndV3 并兼作刷新凭据；UC access_token 调 UC 接口。「前端只持一种令牌」这一目标取消 |
| 2 | §1.2 目标 2、§5.2 B2/B4/B17、§6-4、§9 M4 | 「不再生成 / 存储自签 token」「自签链路彻底移除」 | 全部作废：`tokenGenerator` / `loginToken` / `token_record(type=login)` 长期在役（见 R16） |
| 3 | §4.1 决策 1、§5.1 U1–U3、§5.2 B7、§8 introspection 单测 | 新增 UC introspection 供 BackEndV3 校验令牌 | 不需要做了：BackEndV3 仍查本地 `loginToken`，不校验 UC 令牌，消费方 `UcTokenIntrospector` 随之取消（见 4.2 鉴权路径声明）。附带消除上游 R2（UC 挂机导致全站 401） |
| 4 | §5.2 B5/B6/B14 | 拦截器改为校验 UC 令牌、`extractToken` 重写、封禁以 UC 为单一事实来源 | 保留现状；但封禁 / 登出需双撤（UC 侧 + 删本地 `loginToken`），见 R4 / R14 |
| 5 | §5.2 B3、§4.2 | 新增凭证表、`uk(uid, client_id)` | 复用自签 token 后不需要建表，该 `uk` 陷阱自动规避（见 7.1） |
| 6 | §5.2 B8、§10 第 5 条 | 刷新接口只说「读本地加密凭证」；刷新触发方式未定 | 明确：`/auth/token/refresh` 按请求携带的自签 token 反查 uid（见 B12） |
| 7 | §5.2 B1 | 补全 `DirectLoginUserVO` 接住令牌 | 仍要做；且新登录时也要把 refresh_token 加密落库（见 B8） |
| 8 | §5.2 B4 | 兑换结果缓存 key 取 `sha256(自签 token)` | 改为 `uc:migrate:issued:{uid}`：UC 侧迁移凭证是 `(uid, client_id)` 维度的幂等模型，缓存必须同维度，否则同一用户多设备互相撤销、旧设备掉登录（见 B4 / R17） |

> 结论：上游计划「前端统一使用 UC 令牌」这一目标已由本轮决策取代；仍然保留的部分只有「接住 UC 令牌 + 服务端代持 refresh_token + 用一条链路统一刷新」。

---

## 14. 附录：关键代码位置索引

### 14.1 UC（本仓库）

| 用途 | 位置 |
|---|---|
| 直连登录四端点 | `src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java` |
| 直连兑换实现（迁移要复用的签发路径） | `src/main/java/com/orange/service/impl/AuthServiceImpl.java` 的 `directUser` |
| 令牌签发核心（access / refresh / 索引 / 台账） | `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java` 的 `issueDirectToken` |
| 刷新链路校验顺序（决定 10.1 的前置条件） | `src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java` 的 `refreshToken` |
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

### 14.2 BackEndV3（外部仓库，需另行核对）

| 用途 | 位置 |
|---|---|
| 自签 token 校验与新增兑换分支 | `src/main/java/com/lhs/interceptor/UserInterceptor.java` |
| 会话创建、令牌反查、登出 | `src/main/java/com/lhs/service/user/impl/OAuthUserServiceImpl.java`（`createSessionByOAuth2Uid`、`getUserInfoPOByToken`、`logout`） |
| UC 相关配置 | `src/main/resources/application-test.yml`（`user-center.oauth.*`） |
