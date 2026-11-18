# PAT 落地核对报告

> 生成日期：2026-11-18  
> 核对依据：`docs/oauth/PAT改造方案-最终版.md`、`docs/pat/PAT消费端接口设计.md`，对照**本地 git 工作区改动**（`git diff` + 未跟踪新增文件）  
> 性质：只读审查报告；审查结论已同步至 `.workbuddy/memory/2026-11-18.md`  
> **后续变更（2026-11-18）：按业务取舍改为「PAT 全链路明文」，代码与三份文档已同步改造，见第 0 节。**
> **文档补齐（2026-11-18）：** 第 6 节所列文档/注释欠账已全部闭合，`docs/API.md` 与调用方接口文档已补齐。
> **4.1 处理（2026-11-18）：** 消费端接口改为不接收任何请求参数，详见第 4.1 节。
> **第 3 步专项核对（2026-11-18）：** 展开复核「按明文精确查 tlc_pat」，结论记入 4.4；按决策仅记录、不改代码。
> **source 列删除（2026-11-18）：** 全量移除 `tlc_pat.source` 及其实体/VO/Mapper/迁移/测试/文档落点，详见第 0.1 节。
> **payload 实体化（2026-11-18）：** 从任意 `Map<String,Object>` 改为固定结构 `PatPayload`，DB 仍存 TEXT，读取时转对象并容错，详见第 0.3 节。
> **payload 字段改名（2026-11-18）：** `akUid` → `gameUid`（String）、新增 `gameName`（String），详见第 0.4 节。
> **迁移端点去开关（2026-11-18）：** `POST /internal/pat-migration/run` 改为随启动注册、不设开关，跑完删类，详见第 0.5 节。

## 0. 后续变更：全链路明文（2026-11-18 追加）

审查完成后确认的业务决策：**PAT 不做摘要，全部用明文**——数据库、Redis、迁移写入的新表行、  
列表接口都是令牌明文本身；旧自定义令牌迁移时保留原样明文写入 `tlc_pat`；用户侧与应用侧  
列表均返回完整明文。这是明确的业务取舍：换取令牌可回看、旧令牌无感迁移、消费端一次精确  
匹配、校验与吊销口径统一；代价是数据库、备份、Redis（若启用缓存）与接口响应都成为敏感数据源。

已同步改造：

| 面         | 变更                                                                                                                                                               |
| --------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 建表        | `tlc_pat.token_hash CHAR(64)` → `token VARCHAR(128) COLLATE utf8mb4_bin`；唯一键 `uk_tlc_pat_hash` → `uk_tlc_pat_token`。保留 `utf8mb4_bin` 是为了不让 general_ci 折叠 hex 大小写 |
| 实体/Mapper | `TlcPat#tokenHash` → `#token`；`insertIgnore` 写 `token` 列，幂等唯一键同步改名                                                                                               |
| 工具类       | `PatUtil.hash()` 删除；`RedisKeyUtil` 四个预留键参数与注释从 `{sha256}` 改为 `{token}`（阶段一仍无写入点）                                                                                 |
| 业务层       | 签发/迁移原样落明文；`resolve` 与 `revokeByClient` 按明文精确查删；`PatVO` 新增 `token` 字段，两个列表接口回传明文                                                                                 |
| 测试        | 移除摘要一致性用例，改断言「落库明文 == 响应明文」「用户侧/应用侧列表回传明文」；曾新增的 `resolve` 查库参数断言依赖框架内部渲染，已移除                                                                                      |
| 文档        | 方案 §2.1/§2.2/§3/§4/§5/§7/§8/§9、消费端设计 §3.3/§4/§5/§6/§7 全部按明文口径回写                                                                                                  |
| 轮换接口      | 已删除，理由见上                                                                                                                                                         |

配套要求（**必须随上线一起落实**）：数据库账号最小权限、备份加密、全链路 TLS、列表响应不写  
访问日志/前端埋点、日志与异常上报不落令牌明文，**并需确认 SQL 绑定参数不被采集**（见 4.4）。
若阶段二启用 `tlc:pat:*` 缓存，Redis 同样按敏感数据源管理。

> 已有库升级不能重跑 `schema-user.sql`（含 `DROP TABLE`），需走增量 DDL：新增 `token` 列、  
> 回填（旧行可由 `oauth_custom_token.token` 明文补齐）、删除 `token_hash` 列与旧唯一键、  
> 新建 `uk_tlc_pat_token`。按项目约定本报告不附具体 SQL。
>
> **同批决策：删除 PAT 轮换接口。** 理由：① 列表回明文后，轮换原本解决的「明文只显示一次、  
> 丢了/泄露只能换一把」场景消失，「撤销 + 重新签发」完全等价；② `revokeOld=false` 的新旧并存  
> 在明文列表里会变成两把难以分辨的令牌；③ 轮换是唯一「按 id 复制旧 payload」的写路径，  
> 对 `legacy` 行有实质缺陷——legacy 的 payload 为 `null`，轮换出的带前缀新令牌会被消费端强制  
> 要求 `payload.game_uid`，直接返回 10002；④ 唯一保留价值是一次性继承 `name`/`expires_in`/`payload`，可由前端用列表项预填签发表单替代。前端切换尚未开始，此时删除成本最低。
>
> 删除范围：`POST /user/pat/rotate`、`PatRotateRequest`、`PatService#rotate` 及实现、3 个轮换  
> 单测、三份文档中的轮换条目与 `revoke_old` 字段说明。
>
> **本报告第 1–9 节是审查当时的基线快照**，其中涉及「只存摘要」「列表只有 tokenPrefix」的  
> 描述均以第 0 节为准；下文已修正会直接误导的关键条目，其余保留以便追溯审查过程。

## 0.1 后续变更：删除 `tlc_pat.source`（2026-11-18 追加）

**结论：该列是纯冗余字段。** 核对确认 `source` 只有两条写路径（签发写 `session`、迁移写 `legacy`）
和一条读路径（`toVOList` 塞进 `PatVO.source` 供列表展示），鉴权链路零依赖：`resolve` 只按
`token` 明文精确查 + `expire_time`，两代令牌分流靠 `PatUtil.isPat` / `isLegacyToken` 的明文格式，
`PatPrincipal` 不含来源；全部列表与撤销查询也没有任何 `WHERE source` 条件。

来源信息可由既有列完整推出，且比独立列更权威（不会漂移）：新签发令牌必带 `tlc_pat_` 前缀、
`client_id` 固定 `this_land_cloud`；迁移行是纯 64 位 hex、`payload=null`、`client_id` 为真实应用 ID。

| 面 | 变更 |
| --- | --- |
| 建表 | `schema-user.sql` 删除 `source VARCHAR(16) NOT NULL` 列，索引注释「迁移来源统计」改为「按应用统计」 |
| 实体/VO | `TlcPat`、`PatVO` 删除字段与 getter/setter |
| 业务/迁移 | `PatServiceImpl` 删 `SOURCE_SESSION` 常量与签发、列表映射两处；`PatMigrationServiceImpl` 删 `SOURCE_LEGACY`；`TlcPatMapper#insertIgnore` 去掉列与占位符 |
| 注释/测试 | `PatUtil`、`PatServiceImpl` 注释改为按明文形态描述；`PatServiceImplTest` 去掉 3 处来源断言/构造 |
| 文档 | 方案 §2/§5.3/§5.4/§7、消费端设计 §3.1、接口文档 §2.2/§5.2/§8/§9、`API.md`、本报告同步 |
| 接口 | `GET /user/pat/list`、`GET /oauth2/pat/list` 响应不再含 `source`，**属破坏性变更** |

换用的判据：迁移对账改为 `SELECT COUNT(*) FROM tlc_pat WHERE LEFT(token, 8) <> 'tlc_pat_'`；
阶段二「撤销应用授权清 `tlc_pat`」改为 `DELETE ... WHERE uid=? AND client_id=?`（`uid + client_id`
已定位应用，个人自助 PAT 的 `client_id=this_land_cloud` 不会被误删），必要时可加
`AND LEFT(token, 8) <> 'tlc_pat_'`。已有库需单独执行增量 DDL 删除该列，**不能重跑**
`schema-user.sql`；按项目约定本报告不附具体 SQL。全量单测 283 跑 / 0 失败 / 0 错误 / 7 skip。

## 0.2 后续变更：新增应用侧签发 `POST /oauth2/pat`（2026-11-18 追加）

**背景**：此前 `/oauth2/pat/**` 只有 `list` 与 `revoke`，应用侧没有签发入口；`/oauth2/pat/list` 按 `uid + client_id` 过滤且要求 `direct_auth_enabled=1`，而 `/user/pat` 签发的个人令牌 `client_id=this_land_cloud` 不在 `oauth_client` 表，形成「应用只能看迁移来的旧令牌」。本次补齐「应用签发 → 应用列表 → 应用吊销」闭环。

| 面 | 变更 |
| --- | --- |
| 认证 | `POST /oauth2/pat` **不经过 access_token 拦截器**：`WebConfig` 用 `excludePathPatterns("/oauth2/pat")` 精确排除根路径，`/list`、`/revoke` 仍走 `OAuthAuthInterceptor`。凭证是请求体里的 refresh_token，属协议级例外（与 `/oauth2/token/from-refresh` 同类） |
| 凭证解析 | `OAuthTokenService` 新增 `resolveRefreshToken` 与嵌套 `RefreshTokenPrincipal`；`issueAccessTokenFromRefreshToken` 改为复用它，行为与错误码不变，不消费/不轮转 refresh_token |
| 权限 | 新增 `resolveSelfIssuedScopeWithin`：上限为「refresh_token 已授予范围 ∩ 系统可自助范围」，请求 scope 只减不增，挡住凭低权限刷新令牌签出高权限 PAT；不传 scope 取交集全集 |
| 签发 | `PatService#issueByRefreshToken` + 新 DTO `PatIssueByRefreshRequest`；`insertToken` 重载支持指定 `clientId`。`uid`/`client_id` 全部取自凭证记录，不接受调用方指定；签发准入同列表/吊销（`direct_auth_enabled=1`） |
| 落库 | 新令牌 `client_id` 写 refresh_token 所属应用 ID，因此可被 `/oauth2/pat/list` 列出、`/oauth2/pat/revoke` 吊销；`/user/pat/list` 按 uid 也能看到。参数校验、明文落库、`PatIssueVO` 响应与 `/user/pat` 一致 |
| 测试 | `PatServiceImplTest` +6（凭证身份/scope/准入/越权/参数）、新增 `OAuthPatMvcTest` +3（根路径无需 access_token、`/list` 与 `/revoke` 仍被拦截）；全量 **292 跑 / 0 失败 / 0 错误 / 7 skip** |
| 文档 | `API.md`（认证域例外 + 接口登记）、`PAT接口文档.md`（§2/§5.2/§6 新增 6.1 并重编号/§9）、`PAT消费端接口设计.md` §3.1、本方案 §3.1/§3.2/§9 同步。第 1–9 节里的「8 条接口」是审查时基线，现为 **9 条**（应用侧 2 → 3） |

**遗留**：与 `/oauth2/token/from-refresh` 一样，`resolveRefreshToken` 不检查 refresh_token 轮转墓碑（沿用现有直签口径）；refresh_token 明文进请求体，日志/监控脱敏要求同全链路明文取舍。

## 0.3 后续变更：payload 实体化（2026-11-18 追加）

**决策**：把 payload 从「任意 KV 的 `Map<String,Object>`」改成固定结构的自定义实体 `PatPayload`。DB 列保持 `TEXT` 不变，签发时序列化成 JSON 文本，列表与消费端读取时再反序列化成对象。这是类型安全与可扩展性的取舍，代价是不再「原样返回任意 KV」。本节字段口径已按 §0.4 的最终形态修订，改造脉络保留以便追溯。

容错口径（用户明确要求）：**所有字段均可为空**——可完全不传、传 `{}`、或只填部分；实体带 `@JsonIgnoreProperties(ignoreUnknown = true)`，DB 历史行里的任意 KV 与未来新增的键静默忽略，已定义但缺失的字段保持 `null`。因此新增字段无需迁移存量数据，读旧行不会因字段对不上报错。

| 面 | 变更 |
| --- | --- |
| 新实体 | `entity/dto/pat/PatPayload`（请求/响应/消费端共用）：`@JsonIgnoreProperties(ignoreUnknown = true)` + `@JsonInclude(NON_NULL)` + 无校验注解；字段 `gameUid` / `gameName`（JSON `game_uid` / `game_name`，均为 `String`）、`isEmpty()`、`FIELD_GAME_UID` / `FIELD_GAME_NAME` 常量（字段最终形态见 §0.4） |
| 类型切换 | `PatIssueRequest`、`PatIssueByRefreshRequest`、`PatVO`、`PatIssueVO`、`PatPrincipal` 的 payload 全部由 `Object` / `Map<String,Object>` 改为 `PatPayload`；`PatPrincipal` 仍保证非 null（用全空实体兜底） |
| 序列化 | `PatServiceImpl#normalizePayload` 收 `PatPayload` → JSON 文本，去掉「必须是 Map」的运行时校验（类型已由 DTO 保证），保留全空落 `NULL` 与 4KB 上限；`parsePayload` 改为 `readValue(json, PatPayload.class)` 并返回实体；删除 `parsePayloadMap`，新增 `parsePayloadOrEmpty` 供消费端 |
| 取值 | `PatPayloadUtil.requireGameUid` 读 `getGameUid()`，不再按字符串 key 取 Map；字段为 `String`，取值层只做 trim 与「绝不返回空串」（类型口径的最终调整见 §0.4） |
| 异常处理 | `GlobalExceptionHandler` 新增 `HttpMessageNotReadableException → 10001`：payload 传数组/字符串时由 Jackson 绑定阶段拒绝，避免退化成 90001 系统异常 + ERROR 堆栈 |
| 测试 | 新增 `PatPayloadTest` 8 例（§0.4 后为 9 例：未知字段忽略、全空/部分空/null、数字与驼峰别名、NON_NULL 序列化）；`PatServiceImplTest` 改 4 例并新增 3 例（列表/resolve 的未知字段容错）；`PatPayloadUtilTest`、`PatAkAccountMvcTest`、`PatContextTest` 改构造；`OAuthPatMvcTest` 新增 2 例 |
| 文档 | `schema-user.sql` 列注释、`TlcPat`、`PatService`、`PAT改造方案-最终版.md` §2.1/§2.2/§9、`PAT消费端接口设计.md` §3.2/§3.3/§3.4/§5/§6、`PAT接口文档.md` §3.2/§5.1/§5.2/§6、`API.md` 同步 |

**DB 零变更**：只改了列注释文本，`TEXT` 列与 `payload=NULL` 的语义不变，**已有库无需执行任何 DDL**；历史行 `{"a":1}` 这类任意 KV 会被当作「无有效 payload」处理，不影响鉴权（旧令牌本就无 payload 约定）。

**破坏性变更**：列表与签发响应的 `payload` 从任意 object 变成只含已定义字段的 `PatPayload`；调用方若写入过自定义键，这些键不再回传。全量单测 **304 跑 / 0 失败 / 0 错误 / 7 skip**（以最终回归为准）。

## 0.4 后续变更：payload 字段改名与类型收敛（2026-11-18 追加）

**决策**：`PatPayload.akUid`（`Object`）改名为 `gameUid` 并收敛为 `String`，另新增 `gameName`（`String`）。JSON 键统一为蛇形 `game_uid` / `game_name`，另接受驼峰别名 `gameUid` / `gameName`。功能尚未上线，**不保留 `ak_uid` 旧键别名**，DB 文本与对外接口都是干净的新契约。

| 面 | 变更 |
| --- | --- |
| 实体 | `PatPayload` 两个 `String` 字段 + `FIELD_GAME_UID` / `FIELD_GAME_NAME` 常量；`isEmpty()` 同时判断两字段；`gameName` 单独存在也算有效 payload，会落库并回显，但消费端仍因缺 `game_uid` 返回 `10002` |
| 取值 | `PatPayloadUtil.requireAkUid` → `requireGameUid`，常量 `KEY_AK_UID` → `KEY_GAME_UID`；去掉 `Object` 类型分支，只做 trim 与判空 |
| 消费端 | `PatAkAccountController` 改调 `requireGameUid`；错误文案改为 `PAT 未配置 game_uid` / `PAT 的 game_uid 不能为空`。业务层 `listOperators(uid, akUid, source)` 参数名与 `ak_account_binding.ak_uid` 列名保持不变 |
| 注释 | `PatIssueRequest`、`TlcPat`、`PatService`、`PatAkAccountController` 的 payload 字段口径同步 |
| 测试 | `PatPayloadTest` 9 例（新增 gameName 单字段、数字强转、双字段序列化）、`PatPayloadUtilTest` 7 例（新增 gameName 不满足 gameUid、标量强转行为、对象值绑定失败）、`PatServiceImplTest` / `PatAkAccountMvcTest` / `OAuthPatMvcTest` 改字段名与错误文案 |
| 文档 | `API.md`、`PAT接口文档.md`、`PAT消费端接口设计.md`、`PAT改造方案-最终版.md`、本报告同步；业务 API 文档里的 `akUid` 查询参数与 `ak_*` 表列名不受影响 |

**行为变化**：字段声明为 `String` 后，Jackson 会把 JSON 里的布尔/浮点标量强转成 `"true"` / `"1.5"`，取值层不再以 `10002` 拒绝；这类值最终由业务层 `ak_account_binding` 绑定校验挡下（`80008`），不会读到他人数据。对象/数组仍在请求体绑定阶段报 `10001`。`game_name` 目前是纯扩展字段，不参与鉴权与取数。全量单测 **307 跑 / 0 失败 / 0 错误 / 7 skip**。

## 0.5 后续变更：迁移端点去掉开关，随启动注册（2026-11-18 追加）

**决策**：`POST /internal/pat-migration/run` 不再用 `@ConditionalOnProperty` 门控，直接随应用启动装配。该端点从设计起就是一次性运维接口，上线跑完即删除对应 Controller，因此不再保留「开关」这一层。

| 面 | 变更 |
| --- | --- |
| Controller | 删除 `@ConditionalOnProperty(name = "pat.migration.enabled", havingValue = "true")` 与对应 import；类注释改为「不设开关、暴露面靠内网访问限制兜住、用完删类重新发布」 |
| 并发保护 | `PatMigrationServiceImpl#run` 加 `synchronized`：没有开关后浏览器重复提交/脚本重试的概率上升，并发双跑会让同一批行被两个线程各自插入与清理而重复计入 `migrated`（`total` 只在入口取一次），破坏统计口径；串行后第二次因唯一键忽略而不再插入（口径已在 §0.6 调整为 `migrated + failed == total`） |
| 测试 | 新增 `PatMigrationControllerTest` 2 例：① 容器不配置任何属性时仍能取到该 Controller（把「无开关」钉住，加回注解即失败）② 无参数 `POST` 返回 200 与 5 个统计字段 |
| 文档 | `docs/API.md` 鉴权说明与第六节、`docs/pat/PAT接口文档.md` §8、`PAT改造方案-最终版.md` §5.2、本报告 §8 上线约束第 3 条同步 |

**风险口径**：端点无鉴权，且执行会 `DEL` 旧链路的 Redis 令牌明细。去掉开关后暴露面完全依赖部署侧——必须限定内网访问（网关/安全组/白名单），跑完立即删类。第 3 节第 200 行、第 237 行与上表提到的「默认不装配」是审查时基线，以本节为准。

## 0.6 后续变更：迁移改为全量搬迁，不再判 Redis 有效性（2026-11-18 追加）

**决策**：迁移不再逐条 `GET` 判断旧令牌在 Redis 中是否仍有效，直接全量搬迁 `oauth_custom_token` 中所有未过期行。旧表中「用户撤销过授权、Redis 明细已删但签发记录仍在」的行会因此变成可用 PAT（「复活」），该代价已由需求方确认接受，用来换取整轮迁移不必逐条往返 Redis。兜底面是这些令牌会出现在用户的 `/user/pat` 列表里，可再次手动撤销。

| 面 | 变更 |
| --- | --- |
| 迁移逻辑 | 删掉 `migrateRow` 的 `GET` 判空分支与原 `skipped` 累加；改为按批 `migrateBatch`：整批 `INSERT IGNORE` → 按 uid 分组 `SREM` → 变参 `DEL`。插入抛异常时本批 Redis 一条不动，整批计入 `failed` |
| 批量写入 | `TlcPatMapper#insertIgnore` → `insertIgnoreBatch(List<TlcPat>)`，`<foreach>` 单语句写整批；`IGNORE` 同时兜住旧表可能存在的重复明文（`oauth_custom_token` 无 token 唯一键） |
| 字段修正 | `OAuthCustomTokenMapper#selectAliveAfterId` 增查 `create_time`，并写入 `tlc_pat.create_time`。原实现未搬该列，会落到 `CURRENT_TIMESTAMP`，使历史令牌在用户列表里全显示为迁移当天 |
| 统计口径 | `skipped` 恒为 0（字段保留，不破坏既有响应结构），有效口径改为 `migrated + failed == total`；`migrated` 按批累加行数 |
| 端点方法 | `@GetMapping("/run")` 改回 `@PostMapping("/run")`，与文档、运维脚本及既有测试一致（GET 版本会使 `PatMigrationControllerTest` 报 405） |
| 测试 | 新增 `PatMigrationServiceImplTest` 4 例：全量搬迁不读令牌明细（`opsForValue` 从未调用）、明文/前缀/`create_time` 原样搬迁、插入失败时 Redis 不动、无待处理行不发写命令。`PatMigrationControllerTest` 样本改为 `migrated=20413 / skipped=0` |

**Redis 清理口径不变**：仍按表内 `token` 逐个构造 key 精确删除，禁止用 `uc:oauth:access:*` 前缀通配——该前缀与标准 OAuth 的 access_token 共用，通配会打挂全部第三方应用。

## 1. 结论速览

| 项目                   | 结论                                                                             |
| -------------------- | ------------------------------------------------------------------------------ |
| 代码状态                 | `HEAD = d2b90cb (dev)` 只提交了方案文档；PAT 全部实现仍在工作区**未提交**（12 个已跟踪文件改动 + 30 个新文件/目录） |
| 构建状态                 | `mvn -o test -Dtest='!*AkOperatorStateStatisticsIntegrationTest'` 通过（`EXIT=0`） |
| 阶段一（建表 + 管理面 + 迁移代码） | 已落地，语义与方案一致                                                                    |
| 阶段二校验形态（消费端接口）       | 已落地，闭合了方案 §7「PAT 暂无消费方」                                                        |
| 剩余工作                 | 必修缺陷 1 项（4.2 配置调试遗留）、阶段二未实现 6 项、测试缺口 6 项；**文档/注释欠账与 4.1 均已闭合**                          |

一句话：**主链路完整、能编译能跑；文档与注释欠账已补齐、消费端参数缺陷已按决策闭合，剩的是阶段二下线/迁移/对账那一批、测试缺口 6 项，以及 `application-test.yml` 的配置调试遗留。**

## 2. 核对基线

| 基线      | 内容                                                                                                                                                                                                                            |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 方案文档    | `docs/oauth/PAT改造方案-最终版.md`（唯一实施依据，§8 批次 1–6）                                                                                                                                                                                 |
| 消费端设计   | `docs/pat/PAT消费端接口设计.md`（方案 §5.1 的落地设计，§6/§7 验证清单与衔接说明）                                                                                                                                                                       |
| 调用方文档   | `docs/pat/PAT接口文档.md`（2026-11-18 补齐，面向前端/应用后端/脚本）                                                                                                                                                                                |
| 接口总览    | `docs/API.md`（2026-11-18 补齐 PAT 认证域与 8 条接口登记）                                                                                                                                                                                      |
| 已跟踪改动   | `UserContext`、`RedisKeyUtil`、`WebConfig`、`OAuthCustomTokenMapper`、`OAuthTokenService`、`AuthServiceImpl`、`OAuthClientAdminServiceImpl`、`OAuthTokenServiceImpl`、`schema-user.sql`、`application.yml`、`application-test.yml`、方案文档 |
| 新增文件    | `docs/pat/` 三份文档；`common/context/PatContext`、`common/util/{PatUtil,PatPayloadUtil}`；`controller/{internal,oauth,pat,user}` 四个控制器；`entity/dto/pat`、`po/TlcPat`、`vo/pat`；`interceptor/PatAuthInterceptor`；`mapper/TlcPatMapper`；`service/Pat*`（含 Impl）；5 个测试类 |
| 未纳入     | 远端 GitHub（按确认改为本地 git）；`src/main/resources` 下除 `application-test.yml` 外的 yml 内容（按 `AGENTS.md` 约定不读取）                                                                                                                          |

## 3. 已落地项逐条核对

### 3.1 数据层与工具类

| 方案要求                     | 实现                                                                                                                                                                    |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `tlc_pat` 建表（§2.1）       | `db/schema-user.sql` 第 13 节；**现为** `token VARCHAR(128) COLLATE utf8mb4_bin`（明文），唯一键 `uk_tlc_pat_token` + uid/client 两个索引，表级 `utf8mb4_general_ci`，显式声明 CHARSET/COLLATE |
| 实体与 Mapper               | `TlcPat`、`TlcPatMapper#insertIgnore`（`INSERT IGNORE` 保幂等）                                                                                                             |
| `PatUtil` 生成 / 格式判定      | `PatUtil.generate/isPat/isLegacyToken/displayPrefix`（**`hash` 已删除**）；72 字符新令牌 + 64 位小写 hex 旧令牌两代分流；`HEX_CHARS` 经字符码点复核为标准十六进制字符表                                      |
| `RedisKeyUtil` 四方法（§2.2） | `pat/uidPat/patMiss/patLock` 全部备好、键参数已改为令牌明文，**无写入点**，符合「默认不建这些键」                                                                                                     |

### 3.2 接口登记（8 条，删除轮换后）

| 路径                            | 方法   | 认证           | 实现                                      |
| ----------------------------- | ---- | ------------ | --------------------------------------- |
| `/user/pat`                   | POST | 会话           | `UserPatController#issue`               |
| `/user/pat/list`              | GET  | 会话           | `#list`                                 |
| `/user/pat/revoke`            | POST | 会话           | `#revoke`（按 id，幂等）                      |
| `/user/pat/revoke-all`        | POST | 会话           | `#revokeAll`（按 uid 删行）                  |
| `/oauth2/pat/list`            | GET  | access_token | `OAuthPatController#list`               |
| `/oauth2/pat/revoke`          | POST | access_token | `#revoke`（按明文精确匹配 + uid + client_id 三限） |
| `/pat/ak-accounts/operators`  | GET  | PAT          | `PatAkAccountController`（消费面）           |
| `/internal/pat-migration/run` | POST | 无（不设开关，随启动注册，见 §0.5）     | `PatMigrationController`                |

- `WebConfig` 已显式注册 `/oauth2/pat`、`/oauth2/pat/**`（OAuth 拦截器）与 `/pat/**`（PAT 拦截器），`/user/**` 由通配兜住。
- 应用侧准入 `direct_auth_enabled=1` 复用 `OAuthTokenService#requireDirectAuthEnabledClient`；scope 上限与收敛复用 `#resolveSelfIssuedScope`（`OAuthScope.selectable()` + `narrowScope`，`all` 不可自助签发）。
- 请求体同时接受下划线与驼峰写法（`expires_in/expiresIn`、`client_id/clientId`）。
- DTO 校验：`name` 非空 ≤50、`expires_in` 为正且 ≤10 年零 1 个月、`payload` 必须是 JSON object 且序列化 ≤4KB、`{}` 落库为 NULL。

### 3.3 消费端主链路

| 设计条款       | 实现                                                                                                                          |
| ---------- | --------------------------------------------------------------------------------------------------------------------------- |
| 路径与认证域     | `GET /pat/ak-accounts/operators`，独立拦截器，不进 `/oauth2/**` 链路                                                                   |
| 凭证解析       | `RequestUtil.resolveToken` → `isPat/isLegacyToken` 短路（格式不符不查库）→ **不计算摘要** → 按 `token` 明文单次查询 `tlc_pat`（`expire_time > LocalDateTime.now()`，应用本机时间，见 4.4） |
| 上下文拆分      | 身份三元组进 `UserContext`，整个 `PatPrincipal`（含 payload、prefixed）进 `PatContext`；`afterCompletion` 两行 `clear()`                     |
| 账号来源分流     | 新令牌取 `payload.game_uid`；旧令牌 `listOperators(uid, null, null)` 走默认来源/最近变更                                                       |
| payload 取值 | `PatPayloadUtil.requireGameUid`：字符串 trim、空白或缺失一律 10002；整数 JSON 由 Jackson 转十进制字符串，**绝不放行空值**                                             |
| 归属校验       | 显式传 `source=skland`，业务层 `AkAccountServiceImpl:229-249` 跳过撞号手动版分支，强制 `requireBinding`，未绑定返回 80008                            |
| 参数与响应      | 控制器不声明任何查询参数，多余参数由框架忽略、账号仍由 PAT 决定；`Cache-Control: private, no-cache`；共用 `OperatorListVO`，与 OAuth 版同源同码 |
| 错误码        | 80001（未带/格式不符/无效/过期）、80008（scope 不足或账号不归属）、10002（payload/参数非法），与 `ResultCode` 定义一致                                          |
| scope 覆盖   | `UserContext.requireScope(AK_DATA_READ)` 支持 `gama-data.read` 与 `all` 覆盖                                                     |

### 3.4 阶段一语义与安全事件

- 撤销 = 删行、无软删/墓碑；不写 Redis、不改 `OAuthTokenResolver`；`scope` 是快照、重新签发时按当前授权重算；`payload` 写入点仅签发/迁移。
- 方案 §6 回退后的口径已落实：`resetPassword` 只踢会话、`deleteClient` 只清 OAuth 令牌，两处各留注释说明「不动 PAT」是有意为之；未残留死代码。
- 迁移代码：`PatMigrationController` 随应用启动注册、无开关（见 §0.5）；`PatMigrationServiceImpl` 按 `批量 INSERT IGNORE → SREM → DEL` 固定顺序、**不判 Redis 有效性**（全量搬，复活代价已接受，见 §0.6）、游标分页、整批失败计入 `failed` 不阻断、重跑幂等。

### 3.5 自动化测试现状

| 测试类                   | 用例数                 | 覆盖                                       |
| --------------------- | ------------------- | ---------------------------------------- |
| `PatAkAccountMvcTest` | 10                  | 两代令牌分流、拦截器注册、payload 取值、多余参数不影响取数、scope 与归属失败 |
| `PatPayloadUtilTest`  | 7                   | `game_uid` 取值口径                            |
| `PatUtilTest`         | 6                   | 令牌形态、字符表、展示前缀、两代格式识别（摘要一致性用例已删）          |
| `PatContextTest`      | 3                   | 上下文存取与清理                                 |
| `PatServiceImplTest`  | 19（含 5 个 `resolve`） | 签发校验、明文原样落库、列表回传明文、payload、撤销            |

数量为第 0 节明文改造后的现状；全量回归通过。

## 4. 必修缺陷（P0）

### 4.1 `?akUid=` 空串被静默忽略

> **已闭合（2026-11-18）**：按决策直接把接口改为**不接收任何请求参数**，没有采用「按参数是否出现判断」的方案。

审查时的问题是 `PatAkAccountController` 用 `@RequestParam(value = "akUid", required = false)` 声明了一个占位参数、再用 `StringUtils.hasText` 判空抛 10002，导致 `?akUid=` 空串被静默忽略。现在改为：

- 控制器方法签名为 `listOperators()`，**不声明任何参数**；删掉占位参数、`hasText` 判空与抛错分支，相关 4 个 import 一并清理。
- 账号完全由 PAT 决定（新令牌取 `payload.game_uid`、旧令牌走默认来源），请求参数不参与取数。
- 语义变化：调用方多传 `akUid`（或任何查询参数）由 Spring MVC **静默忽略**，不再返回 `10002`。这是明确的取舍——用「接口契约上根本没有参数」替代「运行时拦截参数」，代价是误传参数时不会报错，因此四份文档都已显式写明「不要依赖请求参数指定账号」。设计文档 §4 原「传入 akUid 直接报 10002」的要求同步撤销。
- 测试：`PatAkAccountMvcTest` 的 `queryAkUidIsRejected` 改为 `queryParamsAreIgnoredAndAccountStillComesFromPat`，断言带 `?akUid=999` 仍返回 200、且按 payload 的 `game_uid` 调用 `listOperators`。用例数保持 10。

### 4.2 配置文件调试遗留

`src/main/resources/application-test.yml`：

```yaml
migrate.enabled:      false -> true
migrate.require-https: true  -> false
```

这是 **OAuth 迁移端点**的开关，与 PAT 无关，但会随这批改动一起提交；`enabled=true` + 关闭 HTTPS 强制在公网上是风险敞口，提交前应回滚。  
另外 `application.yml` 有 1 行改动（1+/1-），按 `AGENTS.md` 约定未读取内容，请自行确认它是否为 `pat.migration.enabled` 开关。目前 `application-test.yml` 中**没有**该 key，迁移 Controller 在测试环境不装配（符合默认关闭，但批次 3「先在测试库跑通」需要显式打开）。

### 4.3 其它小偏差

- `PatServiceImpl#resolve` 用应用本机 `LocalDateTime.now()` 判过期，设计 §3.3 写的是 DB `NOW()`；多实例时钟漂移时过期判定口径会不一致。
- `revokeByClient` 对明文 `trim()` 后精确匹配，`resolve` 依赖 `RequestUtil.resolveToken` 的 trim，两条链路对同一明文的归一化时机不同（当前不会造成实际问题）。

> 这两项与另两项新发现一并在 **4.4** 展开；本小节保留为速览。

### 4.4 明文查库步骤专项核对（2026-11-18，仅记录）

> 对应消费端流程图第 3 步「按明文精确查 `tlc_pat`」。按决策**仅核对、不改代码**，结论供后续排期使用。

`PatServiceImpl#resolve` 这一步实际有 4 个动作，流程图标题只概括了其中一个：

| # | 动作                     | 代码                                                                                    |
| - | ---------------------- | ------------------------------------------------------------------------------------- |
| 1 | 格式二次判定（拦截器已判过一次）       | `isPat` / `isLegacyToken`；不符直接 `return null`，**不产生 SQL**                              |
| 2 | 明文等值                   | `.eq(TlcPat::getToken, raw)`；参数是令牌明文本身，无摘要换算                                       |
| 3 | 过期过滤                   | `.gt(TlcPat::getExpireTime, LocalDateTime.now())`                                     |
| 4 | 命中后组装主体                | `PatPrincipal(uid, clientId, scope, payloadMap, prefixed)`；payload 解析失败按空 Map 处理，不抛系统错误 |

实际 SQL 为 `WHERE token = ? AND expire_time > ?`——**两个条件、两个参数**，第二个是应用本机时间。
「精确」成立依赖两个前提：`uk_tlc_pat_token` 唯一键（保证 `selectOne` 最多一行）与 `token` 列
`utf8mb4_bin`（不折叠 hex 大小写）。失败语义统一：未命中与已过期都返回 `null`，由拦截器抛
`80001`，不区分原因、不暴露令牌状态。

复核发现 4 点，均**未修改代码**：

1. **时间口径**（4.3 已有，此处细化）：方案 §5.1 写 DB `NOW()`，实现用本机 `LocalDateTime.now()`。
   签发时 `expireTime = now().plusSeconds(expiresIn)` 也是应用本机时间，单实例下两边同源、完全
   一致；风险只在多实例时钟漂移——同一令牌在不同节点可能一个放行一个拒绝。
2. **明文直接进 SQL 绑定参数**（新发现，全链路明文取舍下最实际的泄漏面）：若 MyBatis SQL 日志、
   MySQL 慢查询日志或 APM 采集了绑定参数，令牌明文会落盘/外报，与第 0 节「日志/异常/监控不落
   明文」的配套要求冲突。按 `AGENTS.md` 约定未读取 `application.yml`，**线上日志级别与 APM 采集
   配置需自行确认**；确认前这是上线前必须闭合的检查项。
3. **归一化时机不一致**（4.3 已有）：`RequestUtil.resolveToken` 已 `trim()`，`resolve` 不再 trim；
   `revokeByClient` 额外 `trim()`。同一明文在两条链路的归一化点不同，当前无害。
4. **过期行为无自动化覆盖**（新发现，见第 7 节）：5 个 `resolve` 用例覆盖了格式不符不查库、
   payload 解析、legacy 无前缀、payload 损坏容错、行缺失，唯独没有「已过期行返回 null」。
   过期条件加在 `LambdaQueryWrapper` 上，单测 `mock(selectOne(any()))` 后验证不到 `.gt(...)` 是否
   存在——这条关键行为目前完全靠人工审查保证。

## 5. 阶段二尚未实现（P1，按方案排期）

| 项                   | 方案位置        | 现状                                                                         |
| ------------------- | ----------- | -------------------------------------------------------------------------- |
| 旧 6 端点统一 `410 Gone` | §3.2 / 批次 6 | 全仓无 `410`/`Gone`；`UserController` 3 个 + `OAuthController` 3 个端点仍原样返回业务结果   |
| 执行迁移 + §5.4 对账      | 批次 5        | 代码就绪，端点随启动注册（不设开关，见 §0.5）；`migrated+failed==total`、`CHECKSUM TABLE` 三项均未在测试库跑过           |
| 前端切换 + 旧表导出删除       | 批次 6        | 未做，旧表明文仍常驻                                                                 |
| `last_used_at` 回写   | §4 / 消费端 §7 | 未做；两份文档都明确延后，建议按「距上次使用超过 5 分钟才更新」另起一批                                      |
| 家族索引成员未改写           | §7 已知缺口     | 迁移只 `SREM uc:uid:oauth:{uid}`，没有 `SADD` 到 PAT 侧，也没处理 `uc:oauth:family:*`   |
| 撤销应用授权清 `tlc_pat`   | §7 已知缺口     | `revokeClientAuthorization` 未接线，缺按 `uid + client_id` 的删除 |

> **方案 §5.4 口径矛盾已在本轮一并修正**：改为抽查 10 条调 `/pat/ak-accounts/operators` 应 200，调 `/oauth2/userinfo` 应 90009（迁移删了 `uc:oauth:access:{token}`）。

## 6. 文档与注释欠账（P2，2026-11-18 已闭合）

> **2026-11-18 已全部闭合。** 下表「原问题」是审查当时状态，「处理」为实际改动；方案 §2.1 / §1 / §7 / §8 与过期注释在明文改造同批已修，其余在本轮补齐。

| 位置                                        | 原问题                                                                    | 处理                                                                                                          |
| ----------------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| 方案 §2.1                                   | 「不解读键含义、不做键名白名单、不据其分支」与 `game_uid` 唯一例外冲突                               | 已补例外说明；`PatIssueRequest` 类注释同步补充「新令牌必须提供 `game_uid`，否则消费端 10002」                                       |
| 方案 §1 / §7 / §8                           | 消费端「目前不存在」、§7「PAT 暂无消费方」、§8 批次 4「定校验形态」均为旧状态                           | 已改为完成时，§7 首行标记「已闭合」                                                                                        |
| 方案 §5.4                                   | 「抽查 10 条调 `/oauth2/userinfo` 应全部 200」与迁移删 Redis key 自相矛盾                        | 改为调 `/pat/ak-accounts/operators` 应 200、调 `/oauth2/userinfo` 应 `90009`                                       |
| `docs/API.md`                             | 未登记 PAT 接口，鉴权说明缺 PAT 认证域                                                 | 新增 PAT 认证域条目与「URL 一级前缀 = 认证域」约定；登记 8 条接口（用户侧 4、应用侧 2、消费面 1、内部迁移 1）                          |
| 面向调用方的独立接口文档                              | 缺失；契约只写在消费端设计 §4                                                      | 新增 `docs/pat/PAT接口文档.md`：凭证形态、两代令牌分流、逐接口请求/响应字段、错误码、明文取舍安全要求、旧令牌迁移消费、上线约束        |
| `TlcPatMapper` 类注释                        | 仍提「按 clientId 批量清理」，方法已随安全事件回退删除                                     | 明文改造时重写类注释，只保留 `insertIgnore` 的幂等说明                                                                    |
| `TlcPat.java` / `schema-user.sql` 第 13 节   | 仍写「消费端由将来的独立接口自行校验」                                                  | 已改为「消费端由 `/pat/**` 独立校验」，并写明全链路明文取舍与配套要求                                                         |
| 轮换接口删除后的注释残留                              | `PatIssueVO` / `PatVO` 仍写「签发/轮换」，`PatVO#id` 写「撤销与轮换按它定位」                  | 3 处已清理；`PatIssueVO`/`PatVO` 类注释统一为「签发响应」口径                                                            |
| 本报告自身格式                                  | 第 2 节表格管道符错乱、附录多余空代码块、§3.2 仍写「9 条接口」                                   | 已重写第 2 节表格、清理附录代码块、§3.2 改为 8 条                                                                         |

> 第 4 节必修缺陷、第 5 节阶段二排期与第 7 节测试缺口**未纳入本轮**，状态不变。

## 7. 测试覆盖缺口（P2）

| 方案/设计要求                                                                                      | 现状                                 |
| -------------------------------------------------------------------------------------------- | ---------------------------------- |
| `/oauth2/pat/list`、`/oauth2/pat/revoke` 不带 token → 90009（§3.1 特意强调「漏配只有这个场景会暴露」）             | 无 MVC 测试                           |
| `listByClient` / `revokeByClient`：client_id 不符删不掉、`direct_auth_enabled=0` 删不掉、明文不存在幂等（§9 单测） | 无 service 单测                       |
| `UserPatController` 五个管理端点的端到端契约                                                             | 无（仅有 `PatServiceImplTest` 服务层）     |
| 消费端 §6 末条「新令牌与 OAuth 版响应逐字段一致」的自动化对比断言                                                       | 无；MvcTest 用 mock service，不覆盖真实响应对齐 |
| ~~`PatMigrationServiceImpl` 幂等（`migrated+failed==total`、重跑不翻倍）~~ | **已闭合**：`PatMigrationServiceImplTest` 覆盖全量搬迁、字段原样搬迁、插入失败不清理 Redis、无待处理行不写；口径见 §0.6 |
| `resolve` 已过期行返回 null，即 `.gt(expireTime, now)` 条件存在性（4.4 第 4 点）                          | 无；纯单测 mock 掉 `selectOne` 后验证不到 wrapper 条件        |

## 8. 上线约束

1. **消费端与阶段二迁移必须同批上线**。迁移会 `DEL uc:oauth:access:{token}`，旧令牌在那之后只靠 `/pat/**` 按明文识别；两者错批会产生「旧令牌既不被旧链路认、也不被新链路认」的失效窗口。
2. `db/schema-user.sql` 中 `tlc_pat` 段带 `DROP TABLE IF EXISTS`，属于**全量初始化脚本**；已有库上线需要走增量 DDL，不要直接重跑整份脚本。`tlc_pat` 已移除 `source` 列，已有库需单独执行对应的增量变更。按项目约定，本报告不附迁移脚本与 SQL。
3. 迁移端点**不设开关**，随应用启动直接注册：必须靠部署侧限定内网访问（网关/安全组/白名单），跑完立即删除该 Controller 并重新发布，不要把「任何人访问即触发一次全表迁移」的按钮留在后续版本里。服务层 `run()` 已加锁串行，重复触发不会让统计翻倍。

## 9. 建议执行顺序

> 已完成（第 0 节）：全链路明文改造 + 删除 PAT 轮换接口 + 三份文档回写 + 方案 §2.1/§5.4/§1/§7/§8 状态回写 + 过期注释清理。
> 已完成（第 6 节）：`docs/API.md` 登记 8 条接口与 PAT 认证域、新增 `docs/pat/PAT接口文档.md`、清理轮换残留注释、修正本报告格式。
> 已完成（4.1）：消费端接口改为不接收任何请求参数，四份文档与 MVC 用例同步。
> 已完成（4.4）：第 3 步明文查库专项核对，4 点结论已记录；按决策不改代码。

1. 评估 `resolve` 过期判定是否改用 DB 时间；确认线上 MyBatis SQL 日志、慢查询日志与 APM 未采集令牌明文绑定参数（4.4）。
2. 回滚 `application-test.yml` 的调试开关，确认 `application.yml` 那一行改动的用途。
3. 补 `/oauth2/pat/**` 拦截器注册测试、`revokeByClient` 准入/归属单测、迁移幂等测试与过期判定断言（第 7 节 6 项）。
4. 再按批次 5 → 6 推进：测试库跑通迁移与对账 → 同批上线消费端与迁移 → 旧 6 端点 410 → 前端切换 → 导出删旧表。

## 附录：本地 git 改动清单

**已跟踪改动（12）**

```
M docs/oauth/PAT改造方案-最终版.md
M src/main/java/com/orange/common/context/UserContext.java
M src/main/java/com/orange/common/util/RedisKeyUtil.java
M src/main/java/com/orange/interceptor/WebConfig.java
M src/main/java/com/orange/mapper/OAuthCustomTokenMapper.java
M src/main/java/com/orange/service/OAuthTokenService.java
M src/main/java/com/orange/service/impl/AuthServiceImpl.java
M src/main/java/com/orange/service/impl/OAuthClientAdminServiceImpl.java
M src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java
M src/main/resources/application-test.yml
M src/main/resources/application.yml
M src/main/resources/db/schema-user.sql
```

**未跟踪新增（按目录归并）**

```
docs/pat/PAT消费端接口设计.md, docs/pat/PAT落地核对报告.md, docs/pat/PAT接口文档.md
common/context/PatContext.java, common/util/{PatUtil,PatPayloadUtil}.java
controller/internal/PatMigrationController.java
controller/oauth/OAuthPatController.java
controller/pat/PatAkAccountController.java
controller/user/UserPatController.java
entity/dto/pat/{PatIssueRequest,PatRevokeRequest,PatClientRevokeRequest}.java
entity/po/TlcPat.java, entity/vo/pat/{PatIssueVO,PatVO,PatMigrationResultVO}.java
interceptor/PatAuthInterceptor.java, mapper/TlcPatMapper.java
service/{PatService,PatPrincipal,PatMigrationService}.java
service/impl/{PatServiceImpl,PatMigrationServiceImpl}.java
test: PatContextTest / PatUtilTest / PatPayloadUtilTest / PatAkAccountMvcTest / PatServiceImplTest
```
