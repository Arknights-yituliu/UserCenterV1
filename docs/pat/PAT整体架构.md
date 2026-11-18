# PAT 整体架构

> 生成日期：2026-11-18
> 定位：把散在《PAT改造方案-最终版》《PAT消费端接口设计》《PAT接口文档》《PAT落地核对报告》四份文档里的
> 决策收敛成一张全貌；细节仍以各专题文档为准，索引见 §11。
> 口径：与当前代码一致——**全链路明文、无轮换接口、迁移端点随应用启动注册**。

## 1. PAT 是什么，不是什么

**PAT（Personal Access Token）是「用户自签的长期凭证」，与用户会话、OAuth access_token 并列的第三类凭证。**

| 维度 | 结论 |
| --- | --- |
| 谁签发 | 用户自助（`/user/pat`），或应用后台凭 refresh_token 代签（`POST /oauth2/pat`）；两种签发都落在同一张 `tlc_pat` |
| 谁使用 | 调用方把它当请求凭证打 `/pat/**`；服务端按明文单次查库校验 |
| 有效期 | 签发时指定，上限 10 年零 1 个月；`scope` 是签发时快照，不随授权变化回溯 |
| 不是 access_token | `/oauth2/**` 链路**完全不认 PAT**；PAT 也不能拿去向 OAuth 资源端点取数 |
| 不是会话 | 它不携带登录态，不能调 `/user/**`；撤销 PAT 不影响登录 |
| 不是「只能看一次」的密钥 | 明文入库、列表回传明文，可随时回看（明确的业务取舍，见 §9） |

关键边界只有一条：**PAT 只在 `/pat/**` 被当作凭证，在其他任何地方都只是「被管理的对象」**。

## 2. 全景

```
管理面（PAT 是「被管理的对象」）
  浏览器     --会话---------->  /user/pat/**                 UserPatController   ─┐
  第三方前端 --access_token-->  /oauth2/pat/{list,revoke}     OAuthPatController  ─┤
  第三方后端 --refresh_token->  POST /oauth2/pat              OAuthPatController  ─┼─> PatService ─> tlc_pat (MySQL)
  运维       --无鉴权-------->  /internal/pat-migration/run   PatMigrationService ─┘

消费面（PAT 是「请求的凭证」）
  调用方     --PAT----------->  /pat/**  PatAuthInterceptor ─> PatService#resolve ─> PatAkAccountController
                                                                                        └─> AkAccountService ─> ak_* 表
```

三条链路互不纠缠：

- **会话链路**（`UserAuthInterceptor` → `/user/**`、`/auth/logout`）
- **OAuth 链路**（`OAuthAuthInterceptor` → `/oauth2/**` 资源端点）
- **PAT 链路**（`PatAuthInterceptor` → `/pat/**`）

`WebConfig` 用「URL 一级前缀 = 认证域」把三者切开，任何一个前缀都不落在两个拦截器的交集里（例外只有 `POST /oauth2/pat`，见 §5.1）。

## 3. 数据模型

### 3.1 表 `tlc_pat`

| 列 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT | 主键，用户侧撤销按它定位 |
| `uid` | BIGINT | 所属用户 |
| `client_id` | VARCHAR(128) | 签发归属：用户自助固定 `this_land_cloud`；应用侧签发写 refresh_token 所属应用 ID |
| `name` | VARCHAR(50) | 用户命名的用途 |
| `token` | VARCHAR(128) **COLLATE utf8mb4_bin** | **令牌明文**，不做摘要 |
| `token_prefix` | VARCHAR(16) | 前 12 位，仅用于列表展示 |
| `scope` | VARCHAR(256) | 签发时权限快照，英文逗号分隔 |
| `expires_in` | BIGINT | 签发时指定的秒数（回显用） |
| `expire_time` | DATETIME | 到期时间 |
| `payload` | TEXT NULL | `PatPayload` 序列化后的 JSON 文本，不参与鉴权 |
| `last_used_at` | DATETIME NULL | 预留列，**当前没有写入点** |
| `create_time` / `update_time` | DATETIME | 审计字段 |

索引与约束：

- `uk_tlc_pat_token (token)`：明文唯一，用于「按明文精确定位」与迁移 `INSERT IGNORE` 幂等。**唯一键是 `resolve` 能安全用 `selectOne` 的前提。**
- `idx_tlc_pat_uid (uid, create_time)`：用户侧列表（按签发时间倒序）。
- `idx_tlc_pat_client (client_id)`：应用视角列表与按应用统计。
- `token` 列用 `utf8mb4_bin` 不是为了比摘要，而是**不让 `general_ci` 折叠 hex 大小写**。
- 表本身是 `utf8mb4_general_ci`，符合项目「普通业务表用 general_ci、精确比较列列级用 bin」的约定。

### 3.2 两代令牌形态

| 代际 | 形态 | 来源 | payload | 消费端取数口径 |
| --- | --- | --- | --- | --- |
| 新令牌 | `tlc_pat_` + 64 位小写 hex，共 72 字符 | `/user/pat`、`POST /oauth2/pat` | 可能有 | 取 `payload.game_uid`，缺失/空白报 `10002`；强制核对绑定关系 |
| 旧令牌 | 纯 64 位小写 hex，无前缀 | 由 `oauth_custom_token` 迁移而来 | 恒为 `null` | 不指定账号，按「默认来源 → 最近变更」取数 |

判据是**明文形态**（`PatUtil.isPat` / `isLegacyToken`），不是任何数据库列——当初的 `tlc_pat.source` 已删除，来源可由前缀、`client_id`、`payload` 反推。

### 3.3 `payload` 结构

`payload` 是固定实体 `PatPayload`，不是任意 KV：

| JSON 键 | 类型 | 作用 |
| --- | --- | --- |
| `game_uid` | String（别名 `gameUid`） | **唯一被服务端解读的字段**：决定读哪个游戏账号 |
| `game_name` | String（别名 `gameName`） | 纯展示，不参与鉴权与取数 |

规则：

- 所有字段可空：不传、传 `{}`、只填一个都合法；全空等价于 `NULL`。
- 未知键静默忽略（`ignoreUnknown=true`），因此**加字段不需要迁移存量数据**；代价是列表不再回显任意 KV。
- 序列化后 ≤ 4KB。
- 取 `game_uid` 走 `PatPayloadUtil.requireGameUid`：trim 后必须非空，**绝不放行空串**（空串会让业务层回落到默认账号，等于越权）。
- `game_uid` 声明为 `String` 后，JSON 里的整型数字、布尔、浮点会被 Jackson 强转成字符串；对象/数组在请求体绑定阶段直接 `10001`。

### 3.4 存储分布

| 存储 | PAT 相关数据 | 现状 |
| --- | --- | --- |
| MySQL `tlc_pat` | 全部令牌记录 | **唯一事实来源** |
| Redis `tlc:pat:{token}`、`tlc:uid:pat:{uid}`、`tlc:pat:miss:`、`tlc:pat:lock:` | 校验缓存、索引、负缓存、单飞锁 | **只在 `RedisKeyUtil` 里预留，没有任何写入点**；默认不加缓存 |
| Redis `uc:oauth:access:{token}` | 旧链路令牌明细 | 迁移时逐个 `DEL`；PAT 链路的 `resolve` 不读它 |
| MySQL `oauth_custom_token` | 旧自定义令牌记录 | 迁移时**只读**，不删行、不改列 |

## 4. 代码结构

### 4.1 分层与类清单

```
controller/
  user/UserPatController            用户自助：签发 / 列表 / 撤销 / 全撤销（会话）
  oauth/OAuthPatController          应用视角：签发（refresh_token）/ 列表 / 吊销（access_token）
  pat/PatAkAccountController        消费面：干员数据（PAT）
  internal/PatMigrationController   一次性运维：触发迁移（无鉴权）
interceptor/
  PatAuthInterceptor                解析 PAT → 写上下文（仅 /pat/**）
  WebConfig                         三条链路的路径注册
common/context/
  UserContext                       三面共有身份：uid / clientId / scope
  PatContext                        PAT 独有：整个 PatPrincipal
common/util/
  PatUtil                           生成、格式判定、展示前缀
  PatPayloadUtil                    payload 取值（requireGameUid）
service/
  PatService / PatServiceImpl       管理面 + resolve
  PatMigrationService / Impl        旧令牌 → PAT
  PatPrincipal                      令牌主体（uid/clientId/scope/payload/prefixed）
mapper/TlcPatMapper                 BaseMapper + 迁移用 insertIgnore
entity/po/TlcPat                    表实体
entity/dto/pat/PatPayload            payload 实体（请求/响应/消费端共用）
entity/dto/pat/PatIssue*Request     签入参（会话 / refresh_token 两版）
entity/vo/pat/PatIssueVO, PatVO     签发响应 / 列表项（都含明文）
```

### 4.2 依赖与复用

`PatServiceImpl` 只依赖三样东西：`TlcPatMapper`、`OAuthTokenService`、`ObjectMapper`。OAuth 侧被复用的四个方法，都是「规则本来就只有一份」的东西，不复制实现：

| 复用点 | 作用 |
| --- | --- |
| `OAuthTokenService#resolveSelfIssuedScope` | 用户自助签发的权限上限 = 系统可自助范围，复用 `narrowScope` / `requireSelectableScopes` |
| `OAuthTokenService#resolveSelfIssuedScopeWithin` | 应用侧签发的上限 = 「refresh_token 已授予范围 ∩ 可自助范围」，请求只减不增 |
| `OAuthTokenService#requireDirectAuthEnabledClient` | 应用侧三个接口的统一准入：客户端启用、已审批、`direct_auth_enabled=1` |
| `OAuthTokenService#resolveRefreshToken` | 解析请求体 refresh_token（uid / clientId / scope / familyId），不消费、不轮转 |

### 4.3 上下文为什么拆成两个

- `UserContext`：**三面共有**的 `uid` / `clientId` / `scope`，控制器因此能直接复用 `requireScope`，不必为 PAT 写一套权限判断。
- `PatContext`：整个 `PatPrincipal`（额外含 `payload`、`prefixed`）。字段增长时只动「表 → 实体 → principal」，上下文类不用改，也不会有「新增 ThreadLocal 忘记清理」的串号风险。
- 两个上下文都由 `PatAuthInterceptor#afterCompletion` 统一清理。

## 5. 认证与鉴权链路

### 5.1 拦截器注册矩阵

| 路径 | 拦截器 | 说明 |
| --- | --- | --- |
| `/user/**`、`/auth/logout` | `UserAuthInterceptor` | 会话；`/user/pat/**` 由通配覆盖 |
| `/oauth2/pat`（根路径，POST） | **无**（`excludePathPatterns`） | 凭请求体 refresh_token 做协议级认证 |
| `/oauth2/pat/**`（`/list`、`/revoke`） | `OAuthAuthInterceptor` | access_token；服务层再校验直连准入 |
| `/pat/**` | `PatAuthInterceptor` | PAT；服务层按明文查库 |
| `/internal/**` | 无 | 一次性运维端点，只能靠部署侧内网限制 |

`excludePathPatterns` 优先于 `addPathPatterns`，所以「排掉根路径、保留子路径」是可行的；写反或漏配的表现都是**「接口能访问但没校验过」**，验证时必须特意测「不带凭证」这一场景。

### 5.2 消费面时序

```
调用方 ── Authorization: Bearer tlc_pat_xxx ──> GET /pat/ak-accounts/operators
   │
   ├─ PatAuthInterceptor：RequestUtil 取明文（trim）
   ├─ PatUtil.isPat / isLegacyToken        格式不符 → 80001（不产生 SQL）
   ├─ PatService#resolve                   单次查库：WHERE token = ? AND expire_time > now
   │     未命中 / 已过期 → null            → 80001（不区分原因、不暴露令牌状态）
   ├─ 写 UserContext(uid/clientId/scope) + PatContext(principal)
   ├─ 控制器内 UserContext.requireScope(AK_DATA_READ)        不足 → 80008
   ├─ 分流：
   │     带前缀   → payload.game_uid（缺失/空白 10002）→ listOperators(uid, gameUid, "skland")
   │     无前缀   → listOperators(uid, null, null)        → 默认来源 → 最近变更
   └─ AkAccountService：绑定关系不通过 → 80008；通过 → 200 + Cache-Control: private, no-cache
```

要点：

- **不写缓存**：每次消费一次库查询。PAT 调用量远低于普通接口，缓存、负缓存、单飞都是「出现性能压力再加」的预案。
- **不写 `last_used_at`**：当前没有回写点，列表里这一列恒为 `null`。
- **`source` 显式传 `skland`**：绕开业务层「akUid 等于本人 uid 时按手动版读取」的撞号兼容分支，强制核对 `ak_account_binding`。

### 5.3 scope 判定

- 控制器调用 `UserContext.requireScope(OAuthScope)`；判定逻辑是 `OAuthScope.covers(granted, required)`，支持父子覆盖（`gama-data.read` 覆盖 `ak-data.read`，`all` 覆盖一切）。
- 元范围 `all` **不允许自助签发**，只能由管理员写库授予机密客户端。
- PAT 的 scope 是**签发时快照**：用户之后调整应用授权，已签发的 PAT 权限不变；要变更权限就撤销重签。

### 5.4 越权防线（四层）

| 层 | 挡什么 | 失败表现 |
| --- | --- | --- |
| 拦截器 | 没有/格式不对/已过期/已撤销的 PAT | `80001` |
| scope | 令牌没有对应数据权限 | `80008` |
| payload | 新令牌没有可用的 `game_uid` | `10002` |
| 业务归属 | 目标账号不属于该 uid（`ak_account_binding` 校验） | `80008` |

四层都在服务端，任何一层缺失都意味着可以直接读他人数据，因此「payload 空串放行」这类看似无害的宽松处理是被明令禁止的。

## 6. 接口清单

### 6.1 用户自助（会话认证）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/user/pat` | 签发；`name`、`expires_in` 必填，`scope`、`client_id`、`payload` 可选；`client_id` 只接受 `this_land_cloud` |
| GET | `/user/pat/list` | 我的全部令牌（含明文、含已过期，`expiresInSeconds` 体现剩余时间） |
| POST | `/user/pat/revoke` | 按 `id` 删行，幂等 |
| POST | `/user/pat/revoke-all` | 按 uid 删全部 |

### 6.2 应用视角（access_token）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/oauth2/pat` | **协议级例外**：凭请求体 refresh_token 签发，`client_id` 取凭证所属应用 |
| GET | `/oauth2/pat/list` | 本应用为该用户签发的令牌（uid + client_id 双重过滤） |
| POST | `/oauth2/pat/revoke` | 按明文吊销，归属按 uid + client_id + token 三条件 |

三个接口都要求 `direct_auth_enabled=1`（`90014`），且都只对「本应用 + 本用户」生效。

### 6.3 消费面（PAT）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/pat/ak-accounts/operators` | 干员全量数据；账号完全由令牌决定，**接口不声明任何查询参数** |

### 6.4 一次性运维

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/internal/pat-migration/run` | 触发旧令牌迁移；无参数、无鉴权，跑完即删 |

## 7. 生命周期语义

| 动作 | 实现 | 语义要点 |
| --- | --- | --- |
| 签发 | `INSERT tlc_pat` | 明文原样入库；`name` ≤ 50；`expires_in` ∈ (0, 10年1个月]；scope 只能收敛；payload 序列化 ≤ 4KB |
| 使用 | `SELECT ... WHERE token=? AND expire_time>now` | 每次查库；过期与不存在返回同一结果 |
| 列表 | 按 uid（或 uid+client_id）查，不筛过期 | 过期记录仍在列表里，靠 `expiresInSeconds` 表达 |
| 撤销 | `DELETE` | **物理删行**，没有软删、墓碑或禁用态；生效即时 |
| 过期 | 不清理 | 行保留在表里，只被查询条件过滤；需要清理时另做归档 |
| 安全事件 | 不联动 | 改密、删应用、撤销应用授权都不碰 `tlc_pat`（见下表） |

安全事件矩阵：

| 事件 | 会话 | OAuth 令牌 | PAT |
| --- | --- | --- | --- |
| `resetPassword`（邮箱验证码） | 全踢 | 全吊销 | **不动** |
| `updatePassword`（旧密码） | 全踢 | 不动 | 不动 |
| `deleteClient` | — | 按 clientId 清理 | **不动** |
| 用户撤销某应用授权 | — | 按 uid + clientId 清理 | **不动**（已知缺口，见 §12.2） |

这是产品口径，不是遗漏：PAT 只由用户自助入口处置，避免「改个密码把用户所有自动化脚本打断」。

## 8. 旧令牌迁移

### 8.1 目标

把 `oauth_custom_token` 里**仍然有效**的存量自定义令牌，原样搬进 `tlc_pat`，让它们从「只能打 `/oauth2/**`」平滑过渡到「只能打 `/pat/**`」，调用方不需要换令牌。

- 旧表**只读**：不删行、不改名、不清列，迁移前后 `CHECKSUM TABLE` 应一致。
- 明文原样写入，不换形态、不计算摘要。
- 迁移行 `payload = NULL`、`client_id` 为真实应用 ID。

### 8.2 执行流程

```
countAlive()                                   -- total：旧表 expire_time > now 的行数
FOR EACH batch IN oauth_custom_token WHERE expire_time > NOW() AND id > cursor ORDER BY id LIMIT 500
    批量 INSERT IGNORE tlc_pat(...)            -- 1. 先保证新表可查（整批一条语句）
    SREM uc:uid:oauth:{uid} access:{token}     -- 2. 按 uid 分组，摘出 OAuth 索引
    DEL  uc:oauth:access:{token}               -- 3. 变参 DEL，一批一条命令
    migrated += batch.size()
END
```

**不做 `GET` 判空**：旧表中已被撤销（`revokeClientAuthorization` 只删 Redis、不删记录）的行会一并迁成可用 PAT，即所谓「复活」。这是已确认接受的取舍，换取整轮迁移不必逐条往返 Redis。代价的兜底面是：这些令牌会出现在用户的 `/user/pat` 列表里，用户可再次手动撤销。

顺序固定的理由：

- `INSERT` 在前：新表可查之后才拆旧桶，中间失败不会出现「两边都没有」。插入抛异常时整批 Redis 一条都不动，抛给上层计入 `failed`。
- `SREM` 必做：否则 OAuth 侧按用户批量吊销时仍会删到它，与 PAT 语义冲突。
- `DEL` 必做：否则按 id 撤销删不掉 Redis 里的旧副本，令牌会「复活」。

### 8.3 幂等与并发

- `INSERT IGNORE` + `uk_tlc_pat_token`：重复执行不会产生重复行，同时也兜住旧表可能存在的重复明文（`oauth_custom_token` 没有 token 唯一键）。
- 重跑到已处理行时被唯一键忽略，不会翻倍。
- `run()` 加了 `synchronized`：端点没有开关，浏览器重复提交或脚本重试都可能触发并发；串行后第二次排队执行，统计口径 `migrated + failed == total`（`skipped` 恒 0）不会被并发双跑打乱。
- 整批异常计入 `failed`，不阻断后续批次；本批 Redis 不清理，重跑即可收敛。

### 8.4 对账

```sql
SELECT COUNT(*) FROM tlc_pat WHERE LEFT(token, 8) <> 'tlc_pat_';   -- == migrated
SELECT COUNT(*) FROM oauth_custom_token WHERE expire_time > NOW(); -- == total
CHECKSUM TABLE oauth_custom_token;                                  -- 迁移前后一致
```

再抽查 10 条旧明文：调 `/pat/ak-accounts/operators` 应 200；调 `/oauth2/userinfo` 应 `90009`（旧 key 已删，属预期）。

### 8.5 回滚

回滚不靠接口，靠旧表。旧表明文完整保留，先重灌 Redis 再回滚 jar：

```
FOR EACH row IN oauth_custom_token WHERE expire_time > NOW()
    SET  uc:oauth:access:{row.token} {...} EX (expire_time - now)
    SADD uc:uid:oauth:{uid} access:{row.token}
END
```

直接回滚 jar 而不重灌，会让已迁移令牌在旧链路上全部失效——它们只存在于 `tlc_pat` 里了。

## 9. 安全模型

### 9.1 全链路明文（明确取舍）

`tlc_pat.token`、Redis 预留键、迁移写入、列表响应全部使用令牌明文，不计算 SHA-256。换来的是：令牌可回看、旧令牌无感迁移、消费端一次精确匹配、校验与吊销口径统一。

代价是四个新的敏感数据面，**必须配套**：

| 面 | 要求 |
| --- | --- |
| 数据库 | 账号最小权限；备份加密；`token` 列 bin 排序防大小写折叠 |
| Redis（启用缓存后） | 按敏感数据源管理，实例不与非敏感业务共用 |
| 接口响应 | 全程 TLS；列表响应不写访问日志、不进前端埋点 |
| 日志/监控 | 日志、异常、APM **不得出现令牌明文**；SQL 绑定参数是否被采集需单独确认（见 §12.1） |

### 9.2 错误语义

不做「令牌不存在 / 已过期 / 已撤销」的区分：`resolve` 统一返回 `null`，拦截器统一抛 `80001`。好处是不给攻击者做令牌状态探测，代价是排障只能靠服务端日志里的元数据（uid、id、name，不含明文）。

### 9.3 三条「绝不」

1. **绝不**让 PAT 进入 `/oauth2/**` 的 access_token 解析链路（要进就必须改分派顺序，且先 OAuth 后 PAT，否则每次 OAuth 调用都白付一次查库）。
2. **绝不**把 PAT 当作「可以跨账号取数」的凭证：账号归属始终由 `payload` + `ak_account_binding` 双重限定。
3. **绝不**把明文写进日志、异常消息、监控标签、前端埋点。

## 10. 关键设计决策

| 决策 | 选择 | 理由 | 代价 |
| --- | --- | --- | --- |
| PAT 的定位 | 独立表、独立命名空间、独立拦截器 | 不污染 OAuth 链路，存量 access_token 零影响 | 多一套代码与文档 |
| 存储形态 | 全链路明文 | 可回看、旧令牌无感迁移、吊销口径统一 | 库/备份/日志都成敏感面 |
| 轮换接口 | **删除** | 明文可回看后，「撤销 + 重签」等价；新旧并存会制造混淆；对 legacy 行有实质缺陷 | 换令牌需要两步操作 |
| payload | 固定实体 `PatPayload`，DB 用 TEXT | 类型安全、加字段不迁移数据、旧行容错 | 不再回显任意 KV |
| payload 列类型 | `TEXT` 而非 MySQL `JSON` | MyBatis 按 String 映射，`JSON` 列取值存在格式差异 | 无法用 DB 侧 JSON 函数查询 |
| 消费端形态 | 独立 `/pat/**`，按明文查库 | `OAuthTokenResolver` 一行不改；PAT 调用量低，无需缓存 | 每次消费一次库查询 |
| 撤销语义 | 物理删行 | 没有缓存回写，不需要墓碑 | 无法追溯「曾签发过什么」 |
| 安全事件联动 | 不联动 | 避免改密打断自动化脚本 | 改密后旧 PAT 仍可用（已知缺口） |
| 迁移端点 | 无开关、随启动注册、用完删类 | 一次性运维，不需要长期开关 | 暴露面完全依赖内网限制 |

## 11. 文档关系

| 文档 | 回答什么问题 | 读者 |
| --- | --- | --- |
| 本文 `PAT整体架构.md` | 全局长什么样、各部分怎么咬合、还差什么 | 所有人，先读这篇 |
| `docs/oauth/PAT改造方案-最终版.md` | 为什么这么设计、数据模型与迁移的原始论证 | 架构/评审 |
| `docs/pat/PAT消费端接口设计.md` | `/pat/**` 为什么独立、拦截器与取数口径 | 后端 |
| `docs/pat/PAT接口文档.md` | 每个接口怎么调、字段与错误码 | 调用方 |
| `docs/pat/PAT落地核对报告.md` | 落地与设计的差异、审查发现、变更记录 | 维护者 |
| `docs/API.md` | 全站接口登记与认证域约定 | 所有人 |

## 12. 未完成工作

> 口径：截至 2026-11-18。分四类——**上线阻塞**、**阶段二功能**、**测试缺口**、**明确不做**。
> 已完成的文档/注释欠账（原核对报告第 6 节）不再列入。

### 12.1 上线阻塞（不闭合不应上线）

| # | 事项 | 现状 | 为什么阻塞 | 建议动作 |
| --- | --- | --- | --- | --- |
| 1 | 迁移从未真跑过 | 迁移端点已可用（无开关），但只在单测层面验证；`total/migrated/failed` 与对账三项都没在真实数据上跑过 | 迁移会 `DEL` 旧 key，跑错无法靠重跑修复，只能靠旧表重灌 | 测试库先跑：核对 `migrated + failed == total`、`CHECKSUM TABLE` 前后一致、抽查 10 条打 `/pat/**` |
| 2 | 明文可能落日志/APM | 明文直接作为 SQL 绑定参数；MyBatis SQL 日志、慢查询日志、APM 是否采集参数尚未确认 | 一旦采集，等于把全部令牌写进日志系统，与全链路明文的补偿要求直接冲突 | 上线前确认并关闭参数采集/慢查询明细；必要时对 `tlc_pat` 相关语句单独脱敏 |
| 3 | 已有库增量 DDL 未落 | `db/schema-user.sql` 是含 `DROP TABLE` 的全量初始化脚本；`token` 列、`uk_tlc_pat_token`、删除 `source` 列都只体现在脚本里 | 直接重跑脚本会清空 `tlc_pat` | 对已有库单独执行增量变更（按项目约定，SQL 不进文档） |
| 4 | 迁移 Controller 仍在代码里 | 现在随应用启动注册、无鉴权 | 它就是「任何人访问即触发一次全表迁移」的按钮 | 跑完立即删除 `PatMigrationController` 与 `PatMigrationControllerTest` 并重新发布 |
| 5 | 消费端与迁移必须同批 | 两条链路都已就绪，但发布顺序没有强制机制 | 迁移删掉旧 key 后，若消费端没上，旧令牌两边都不认 | 同一次发布带上 `/pat/**` 与迁移端点；迁移放在发布完成后立即执行 |

### 12.2 阶段二功能未实现

| # | 事项 | 现状 | 影响 | 建议 |
| --- | --- | --- | --- | --- |
| 1 | 旧 6 端点未 `410 Gone` | `UserController` 3 个 + `OAuthController` 3 个端点仍原样返回业务结果 | 旧链路继续可写新令牌，迁移永远追不上 | 前端切换完成后统一改 `410`，观察旧端点日志归零 |
| 2 | 前端未切换 | 用户侧/应用侧的「自定义令牌」页面仍调旧端点 | 新签发的 PAT 与旧令牌分散在两套界面 | 切到 `/user/pat/**`、`/oauth2/pat/**`；列表可直接复制明文，交互不用大改 |
| 3 | 旧表未导出删除 | `oauth_custom_token` 明文常驻 | 库里长期保留一份可用的明文副本，且与 `tlc_pat` 双份共存 | 全量导出归档（加密）后再删表/清列 |
| 4 | `last_used_at` 未回写 | 列存在，无写入点；列表恒为 `null` | 用户看不到「这把令牌最近用过吗」，异常令牌难发现 | 在 `resolve` 命中后按「距上次更新 > 5 分钟」节流更新，另起一批 |
| 5 | 撤销应用授权不清 `tlc_pat` | `revokeClientAuthorization` 只清 OAuth 令牌，未接线 | 用户撤销某应用授权后，该应用名下旧长期令牌仍可用 | 加一句按 `uid + client_id` 删行；注意别误删个人自助 PAT（其 `client_id` 固定 `this_land_cloud`） |
| 6 | 家族索引未处理 | 迁移只 `SREM uc:uid:oauth:{uid}`，没有 `SADD` 到 PAT 侧，也没处理 `uc:oauth:family:*` | 迁移来的旧令牌在刷新重放时不参与整族吊销 | 迁移里补两行；或明确接受该缺口并写进运维手册 |
| 7 | 过期判定用应用本机时间 | `resolve` 用 `LocalDateTime.now()`，方案写的是 DB `NOW()` | 多实例时钟漂移时，同一令牌可能一个节点放行、一个拒绝 | 评估改用 DB 时间，或强制 NTP + 可接受漂移 |
| 8 | 归一化时机不一致 | `RequestUtil` 已 trim，`resolve` 不再 trim；`revokeByClient` 又 trim 一次 | 当前无害，属一致性债 | 统一在入口 trim，服务层只管精确匹配 |

### 12.3 测试缺口（当前实际状态）

| 缺口 | 现状 | 建议 |
| --- | --- | --- |
| `revokeByClient` 无直接单测 | 只覆盖了签发侧准入；「client_id 不符删不掉」「`direct_auth_enabled=0` 删不掉」「明文不存在幂等」都没测 | 补 3 例 service 单测 |
| `listByClient` 未测准入 | 没有 `direct_auth_enabled=0 → 90014` 的用例 | 补 1 例 |
| `UserPatController` 无端到端测试 | 4 个端点只有服务层单测 | 补 MVC 测试（尤其 `revoke-all` 的越权边界） |
| 消费端响应与 OAuth 版一致性 | 没有自动断言，只靠人工比对 | 用同一份 mock 数据跑两个控制器，逐字段比对 |
| ~~`PatMigrationServiceImpl` 无单测~~ | **已闭合**：`PatMigrationServiceImplTest` 覆盖 4 例——全量搬迁不读令牌明细（`opsForValue` 从未调用）、明文/前缀/`create_time` 原样搬迁、插入失败时 Redis 一条不动、无待处理行时不发写命令 | — |
| 过期行返回 `null` 无断言 | `.gt(expireTime, now)` 加在 wrapper 上，单测 mock 掉 `selectOne` 后验证不到 | 用集成测试或捕获 wrapper 条件断言 |
| ~~`/oauth2/pat/**` 无 token → `90009`~~ | **已闭合**：`OAuthPatMvcTest` 覆盖 `/list`、`/revoke` 无 token | — |

### 12.4 已知缺口（接受或待定）

| 缺口 | 后果 | 当前处置 |
| --- | --- | --- |
| 并发撤销无栅栏 | 迁移与撤销并发时，少数令牌可能「撤销后又被写回」 | 迁移期间停写；不停写则接受 |
| 孤儿令牌（Redis 有、旧表无） | 迁不了 | 留在旧桶自然过期，接受 |
| 明文列表的展示风险 | 列表页可直接复制令牌，成为分发渠道 | 已要求 TLS + 不落日志/埋点；前端侧需自查 |
| `payload` 标量强转 | `true` / `1.5` 会被强转成字符串，不在取值层拒绝 | 由绑定校验兜底（`80008`），行为已用测试固化 |
| 无「最近使用」审计 | 见 12.2 第 4 项 | 延后 |

### 12.5 明确不做（避免后来人重复提）

| 项 | 为什么不做 |
| --- | --- |
| PAT 联动改密 / 删应用 | 产品口径：PAT 只由用户自助撤销；改密打断用户全部自动化脚本不可接受 |
| PAT 轮换接口 | 明文可回看后「撤销 + 重签」等价；新旧并存会制造混淆，且对 legacy 行有实质缺陷 |
| Redis 校验缓存 | PAT 调用量低，一次库查询足够；缓存会新增一处明文副本与失效一致性问题 |
| PAT 调用 `/oauth2/**` | 需要改认证分派顺序，且会给每次 OAuth 调用增加一次 miss 成本；当前无需求 |
| 软删 / 墓碑 | 没有缓存回写，删行即生效，不需要墓碑 |
| 摘要存储 | 与「列表可回看、旧令牌无感迁移」的诉求直接冲突，是全链路明文的取舍本身 |
