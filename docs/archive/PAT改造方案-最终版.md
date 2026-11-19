# PAT 独立凭证类改造方案（最终版）

> 名称兼容：示例统一使用 `TCL-Token`、`TCL-Client-Id`、`tcl_ticket`；对应入口仍接受旧名称 `UC-Token`、`UC-Client-Id`、`uc_ticket`。同一项新旧名称同时提供且非空值不一致时返回 `10001`；令牌解析仍优先使用 `Authorization: Bearer`。此说明不新增端点的认证要求，也不改变归档接口的历史状态。

> 把「用户自签的长期令牌」从 OAuth access_token 域里拆出来，成为独立凭证类 PAT：独立表、独立命名空间、独立生命周期。
>
> 本文是唯一实施依据。历史版本（v1–v5、整合版）已移入 `docs/oauth/archive/`，仅作论证底稿。
>
> 全貌速览（分层、链路、未完成工作）见 `docs/pat/PAT整体架构.md`；本文保留设计与论证细节。
>
> **变更记录（2026-11-18）：PAT 改为全链路明文，不再做摘要。**
> 决策口径：令牌不计算 SHA-256；`tlc_pat.token`、Redis 预留键、迁移写入的新表行、列表接口
> 一律使用**令牌明文本身**；旧自定义令牌迁移时保留原样明文写入新表；用户侧与应用侧列表
> 均回传完整明文。这是明确的**业务取舍**：换取「令牌可回看、旧令牌无感迁移、消费端一次精确
> 匹配、吊销与校验口径统一」，代价是数据库、备份、Redis（若启用缓存）与接口响应都成为敏感
> 数据源。必须配套：数据库账号最小权限、备份加密、全链路 TLS、日志/异常/监控不上报明文、
> 列表响应不写入访问日志与前端埋点。本文以下各节均已按此口径修订。
>
> **同批决策：删除 `/user/pat/rotate` 轮换接口。** 明文可回看后，「撤销 + 重新签发」与轮换等价；
> 轮换原本解决的「明文只显示一次、丢了只能换一把」已不成立，而 `revokeOld=false` 的新旧并存
> 反而会在列表里制造两把几乎一样的明文令牌。它还是唯一「按 id 复制旧 payload」的写路径，
> 对迁移来的 `legacy` 行尤其危险：legacy 没有 payload，轮换生成带前缀新令牌后消费端会强制
> 要求 `payload.game_uid`，新令牌直接 10002。需要换令牌时走 `/user/pat/revoke` + `/user/pat`。

## 1. 结论

**PAT 是「被管理的对象」，不是「请求的凭证」。** 申请、查看、撤销 PAT 时，请求头传的仍是原有两种凭证（`TCL-Token` 会话 / access_token）。PAT 的消费端是独立接口 `/pat/ak-accounts/operators`，已落地（见《PAT消费端接口设计.md》）。

因此改造分两阶段，中间没有必须赶的时间点：

| 阶段 | 内容 | 前置 |
| --- | --- | --- |
| 一 | 建表 + 签发 / 列表 / 撤销。**不动认证链路、不迁移、不下线旧接口** | 无，随时可上 |
| 二 | 认证解析 + 迁移旧数据 + 下线旧接口 + 旧表导出删除 | 消费端接口定稿 |

**阶段一与阶段二必须分开上。** 迁移会对旧令牌执行 `DEL uc:oauth:access:{token}`；如果此时还没有任何代码能校验 PAT，旧 key 一删，这些令牌既不被旧链路认、也不被新链路认，当场全部失效。

## 2. 数据模型

### 2.1 表 `tlc_pat`

```sql
CREATE TABLE `tlc_pat` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`          BIGINT       NOT NULL COMMENT '所属用户',
    `client_id`    VARCHAR(128) NOT NULL COMMENT '个人令牌固定 this_land_cloud',
    `name`         VARCHAR(50)  NOT NULL COMMENT '用户命名的用途',
    `token`        VARCHAR(128) COLLATE utf8mb4_bin NOT NULL COMMENT '令牌明文（不做摘要）',
    `token_prefix` VARCHAR(16)  NOT NULL COMMENT '前 12 位，列表展示',
    `scope`        VARCHAR(256) NOT NULL COMMENT '权限快照，英文逗号分隔',
    `expires_in`   BIGINT       NOT NULL COMMENT '签发时指定的秒数',
    `expire_time`  DATETIME     NOT NULL COMMENT '到期时间',
    `payload`      TEXT             NULL COMMENT '自定义 payload 结构的 JSON 文本（PatPayload）',
    `last_used_at` DATETIME         NULL COMMENT '最近使用（阶段二启用）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tlc_pat_token` (`token`),
    KEY `idx_tlc_pat_uid` (`uid`, `create_time`),
    KEY `idx_tlc_pat_client` (`client_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '个人访问令牌';
```

关键约束：

- **全链路明文（业务取舍）**：`token` 直接保存令牌明文，不计算摘要；Redis 预留键、迁移写入、列表接口同样用明文。`token` 列仍用 `COLLATE utf8mb4_bin`，目的是让 hex 令牌的大小写不被 `general_ci` 折叠，而不是为了比摘要。
- **`client_id` NOT NULL，个人令牌固定 `this_land_cloud`**。它在系统里同时是配置数据的分区键（`UserConfigServiceImpl.requireClientId()`），置空会让 `/oauth2/config/**` 抛「缺少来源客户端标识」，且与存量分区不一致。
- **`payload` 是固定结构实体 `PatPayload`**，DB 仍用 `TEXT` 存序列化后的 JSON 文本，读取时再反序列化成对象。只校验「序列化后 ≤ 4KB」，默认不解读内容、不据其分支；**所有字段均可为空**（可传 `{}`、只填部分，或完全不传），缺失字段为 `null`。实体带 `@JsonIgnoreProperties(ignoreUnknown = true)`：DB 历史行里的任意 KV 与未来新增的键都会被静默忽略，不会因字段对不上而报错，因此加字段无需迁移存量数据。代价是列表/签发响应只回传已定义字段，不再「原样返回任意 KV」。当前两个字段均为 `String`：`game_uid` 是**唯一被服务端解读的字段**，决定读取哪个账号（见《PAT消费端接口设计.md》§3.2）；`game_name` 是游戏名称，暂不参与鉴权与取数。两者另接受驼峰别名 `gameUid` / `gameName`。`game_uid` 由 `PatPayloadUtil` trim 后取用，缺失或空白返回 `10002`；JSON 里写成整数会被 Jackson 转成十进制字符串，布尔/浮点标量同样被强转（最终由绑定校验兜底），对象/数组在请求体绑定阶段报错。全空实体与 `NULL` 等价。用 `TEXT` 而非 MySQL `JSON` 列：MyBatis 按 `String` 映射，`JSON` 列在部分版本取值存在格式差异，会让「原样取出」打折。
- `payload` 明文入库、明文返回，**不得放密钥或其它敏感信息**——令牌明文本身已是敏感数据，payload 不应再叠加。

### 2.2 Redis（按需，默认不用）

| Key | 值 | TTL |
| --- | --- | --- |
| `tlc:pat:{token}` | `{"uid":..,"clientId":..,"scope":..,"payload":{...}}` | 至 `expire_time` |
| `tlc:pat:{token}`（撤销后） | `{"revoked":true}` | 24h 墓碑 |
| `tlc:uid:pat:{uid}` | Set，成员为令牌明文 | 无 |
| `tlc:pat:miss:{token}` | `1` | 60s 负缓存 |
| `tlc:pat:lock:{token}` | `1` | 5s 单飞锁 |

- `payload` 以**嵌套对象**存放，不二次转义：它是 `PatPayload` 的 JSON 形态，嵌在 `payload` 键下与外层字段无冲突，读取时用 `ObjectMapper.readValue(json, PatPayload.class)` 转回实体（未知键忽略、缺失字段为 null）。
- 容量：单条记录约 130 字节 ~ 4KB。全打满 2 万条约 80MB，属最坏情况。
- **默认不建这些键。** 5.1 的校验是「按明文查一次库」，PAT 调用量远低于普通接口，直接读 DB 即可；缓存、负缓存、单飞、墓碑都是为高频校验准备的，等真出现性能压力再加。启用后 Redis 里会出现令牌明文，须按敏感数据源管理。
- 若确实要加，`RedisKeyUtil` 的四个方法（`pat` / `uidPat` / `patMiss` / `patLock`）在阶段一先备着，不加也不影响任何功能。

存量 `uc:oauth:access:` / `uc:oauth:refresh:` / `uc:uid:oauth:` 一律不动。

## 3. API

### 3.1 新增

| 方法 | 路径 | 认证 | 用途 |
| --- | --- | --- | --- |
| POST | `/user/pat` | 会话 | 签发。`name`、`expires_in` 必填；`scope`、`client_id`、`payload` 可选。明文原样入库，列表可回看 |
| GET | `/user/pat/list` | 会话 | 我的令牌，含令牌明文（含 `payload`） |
| POST | `/user/pat/revoke` | 会话 | 按 `id` 撤销 |
| POST | `/user/pat/revoke-all` | 会话 | 作废本用户全部 PAT |
| POST | `/oauth2/pat` | 请求体 refresh_token（协议级，不经过 access_token 拦截器） | 应用侧签发 PAT：参数/响应同 `/user/pat`，`client_id` 取凭证所属应用，准入 `direct_auth_enabled=1`，scope 上限为「凭证范围 ∩ 可自助范围」 |
| GET | `/oauth2/pat/list` | access_token | 应用视角只读列表，准入 `direct_auth_enabled=1` |
| POST | `/oauth2/pat/revoke` | access_token | 应用视角按**明文**吊销本应用为该用户签发的一条 PAT，归属按 uid + `client_id` 双限，准入同上 |
| POST | `/internal/pat-migration/run` | 无（仅存在于一次性构建） | 迁移触发，跑完即删，阶段二才用 |

拦截器注册：

- `/user/**` 已被 `UserAuthInterceptor` 通配覆盖，无需额外配置。
- **`/oauth2/pat/**` 必须显式加进 `WebConfig` 的 `addPathPatterns`**。`list` 与 `revoke` 走 access_token；根路径 `POST /oauth2/pat` 凭请求体 refresh_token 签发，用 `excludePathPatterns("/oauth2/pat")` 单独排除（exclude 优先于 include，不影响子路径拦截）。漏配的表现是「接口能访问但没校验过」——这是最危险的一类失误。验证要覆盖两个方向：`/list` 不带 token 必须被拦下、根路径不带 access_token 仍能签发。
- `/internal/**` 不在任何拦截器范围内（现状天然满足）。

列表用 `/list` 而非资源根，给以后的 `GET /user/pat/{id}` 让路——否则 `{id}` 会捕获 `list`。

请求体字段（JSON）：签发 `name` / `expires_in` / `scope` / `client_id` / `payload`；用户侧撤销为 `id`，应用侧撤销为 `token`（明文）。`expires_in`、`client_id` 同时接受驼峰写法。响应里 `token` 是明文；列表同时回传 `token` 明文与 `tokenPrefix`，签发响应不再是唯一获取途径。

> **轮换接口已删除**：换令牌走 `POST /user/pat/revoke` + `POST /user/pat`。需要继承 `name` / `expires_in` / `payload` 时，由前端用列表项预填签发表单，不在服务端保留一条复制旧行的写路径。

### 3.2 下线（阶段二）

共 6 个端点，路径与请求形态原样保留，响应统一改为 `410 Gone`。前 3 个在 `UserController`（会话认证），后 3 个在 `OAuthController`（两个 `custom-tokens` 走 `OAuthAuthInterceptor`，`from-refresh` 自带 refresh_token 认证、不在拦截器内）。

| 方法 | 路径 | 原认证 | 原用途 | 替代 |
| --- | --- | --- | --- | --- |
| GET | `/user/oauth/custom-tokens` | 会话 | 「我的自定义令牌」列表：本用户全部直签令牌（含已过期），返回**明文**与剩余秒数 | `GET /user/pat/list` |
| POST | `/user/oauth/custom-tokens` | 会话 | 会话直签一把自定义有效期 access_token（`name`、`expires_in` 必填，`scope` 可收敛），落 `oauth_custom_token` 记录 | `POST /user/pat` |
| POST | `/user/oauth/custom-tokens/revoke` | 会话 | 按明文吊销本人名下一条直签令牌，先删 Redis 再删记录，幂等 | `POST /user/pat/revoke`（改按 `id`） |
| GET | `/oauth2/custom-tokens` | access_token | 应用视角读本应用为该用户签发的直签令牌明文，准入 `direct_auth_enabled=1` | `GET /oauth2/pat/list` |
| POST | `/oauth2/custom-tokens/revoke` | access_token | 应用视角吊销本应用签发的一条直签令牌，归属按 uid + clientId 双限，准入同上 | `POST /oauth2/pat/revoke` |
| POST | `/oauth2/token/from-refresh` | refresh_token（协议级） | 用 refresh_token 现场换一把自定义有效期 access_token，**不消费、不轮转** refresh_token；权限只能收敛 | **无**，见下 |

替换关系有三处需要留意，实施时容易漏：

- **应用侧吊销按明文、不按 `id`。** 调用方手里只有令牌本身，拿不到 `/oauth2/pat/list` 之外的 `id`；服务端按明文精确匹配删行，归属仍按 uid + `client_id` 双限，准入仍要 `direct_auth_enabled=1`。旧调用方连请求体都不用改，只换路径。用户侧则相反，走 `POST /user/pat/revoke` 按 `id` 删。
- **`from-refresh` 场景由 PAT 承接。** 它的真实用途是「拿 refresh_token 换一把长期凭证粘进服务配置」：应用后端现在可直接调 `POST /oauth2/pat`（请求体带同一个 refresh_token）签出 PAT，无需用户手工到 `/user/pat` 操作；用户自助签发仍走 `/user/pat`。旧端点下线后不再产出新的长期 access_token。
- **列表继续回明文（业务取舍）。** 用户侧与应用侧 PAT 列表都返回完整 `token`，旧页面「列表里直接复制令牌」的用法可以原样平移，无需改造成「签发时一次性保存」；代价是列表响应本身成为凭证分发渠道，必须走 TLS 且不落访问日志。

> **不要反过来让后端改用标准刷新 `/oauth2/token`**：refresh 轮转带重放检测，多实例并发刷新会互相踢死整族令牌。这正是 `from-refresh` 当初存在的理由，下线它不等于退回标准刷新。

## 4. 阶段一语义

- **不写 Redis、不加认证分派**：`OAuthTokenResolver` 一字不改，存量 access_token 零影响。
- **撤销 = 删行**：用户侧 `DELETE FROM tlc_pat WHERE id = ? AND uid = ?`，应用侧 `... WHERE token = ? AND uid = ? AND client_id = ?`。不需要软删或墓碑——没有缓存会把记录写回来。
- **`scope` 是快照**：签发时的权限不随用户后续授权调整回溯；需要变更权限时撤销后重新签发，由 `resolveSelfIssuedScope` 按当前授权重算。
- **`scope` 只能收敛**：沿用 `narrowScope`，只能减不能增；元范围 `all` 不允许自助签发。
- **`revoke-all`** 按 `uid` 删行。
- **`last_used_at` 阶段一不更新**：没有校验路径可挂。
- **`payload` 写入点只有两处**：签发、迁移（`null`）。当前无修改接口，故不存在「DB 改了缓存没改」的问题。

## 5. 阶段二

### 5.1 校验归属：在消费端接口内部完成

PAT 不进 `/oauth2/**` 的 access_token 解析链路。消费端是新增的独立接口，它收到 PAT 后自己校验：

```
row  = SELECT uid, client_id, scope, expire_time FROM tlc_pat WHERE token = pat AND expire_time > NOW()
row 为 null → 401；否则按 row.scope 判定权限
```

即 `OAuthTokenResolver`、`OAuthAuthInterceptor` 以及全部 `/oauth2/**` 资源端点**一行都不用改**。这也是阶段一与本方案其余部分的共同前提：PAT 从不作为请求凭证出现在既有链路上。

> 备选（仅在需求变化时考虑）：若将来要让 PAT 直接调用 `/oauth2/**` 资源接口，才需要加分派层，且顺序必须是「先原有 access_token，miss 后再查 PAT」——反过来会让每次 `/oauth2/**` 调用都白付一次 Redis miss + 一次 DB 查询。届时再引入 2.2 的缓存与墓碑。

### 5.2 迁移接口

```http
POST /internal/pat-migration/run
```

**无参数、无鉴权、同步执行**，跑完返回统计后就没用了：

```json
{ "total":20413, "migrated":20413, "skipped":0, "failed":0, "elapsedMs":1820 }
```

行为固定：按 5.3 全量执行一次。重复调用幂等，不会翻倍。

**这个接口只存在于一次性构建里，跑完立即回滚到不含它的版本。** 别把它留在正式版本中——留着就等于在生产上挂了一个「任何人访问即触发一次不可逆全表迁移」的按钮，哪怕只多存在一天。

实现上**不设开关**：按上线需求直接随应用启动装配，暴露面靠部署侧限定内网访问兜住；跑完立即删除该 Controller 并重新发布。`PatMigrationServiceImpl#run` 已加锁串行，重复触发只会排队、后一次因唯一键忽略而不再插入，`migrated + failed == total` 的口径不会因并发双跑失真。

**回滚不靠接口，靠旧表。** 旧表明文完整保留，需要退回旧版本代码时，用一段一次性脚本把未过期行重灌回旧桶（就是 5.3 的逆操作）：

```
FOR EACH row IN oauth_custom_token WHERE expire_time > NOW()
    SET  uc:oauth:access:{row.token} {"uid","clientId","scope"} EX (expire_time - now)
    SADD uc:uid:oauth:{uid} access:{row.token}
END
```

先跑这段、再回滚 jar，迁移过的令牌就全部恢复。直接回滚 jar 会让它们失效——旧版本只查 `uc:oauth:access:`，而那些 key 已被迁移删除。

### 5.3 迁移动作

```
FOR EACH batch IN oauth_custom_token WHERE expire_time > NOW()   -- 游标：WHERE id > ? ORDER BY id LIMIT n
    批量 INSERT IGNORE tlc_pat(uid, client_id, name, token=row.token, token_prefix=LEFT(token,12),
                               scope, expires_in, expire_time, payload=NULL, create_time=row.create_time)
    SREM  uc:uid:oauth:{uid} access:{row.token}               -- 按 uid 分组，摘出 OAuth 索引
    DEL   uc:oauth:access:{row.token}                         -- 变参 DEL，一批一条命令
    migrated += batch.size()
END
```

1. **旧表只读**：不删行、不改名、不清列。迁移前后 `CHECKSUM TABLE` 应一致，可随时与备份逐行比对。
2. **不判有效性（已确认取舍）**：不查 `uc:oauth:access:{token}` 是否还在，全量搬。`revokeClientAuthorization` 一直只删 Redis 不删记录，所以存量里「已撤销但记录还在」的行会被迁成可用 PAT——这就是「复活」，已明确接受，用来换取迁移不必逐条往返 Redis。兜底面是这些令牌会出现在用户的 `/user/pat` 列表里，可再次手动撤销。若某次部署不能接受，必须改用按 Redis 明细筛选的版本。
3. **`create_time` 必须显式搬**：不写会落到列默认值 `CURRENT_TIMESTAMP`，历史令牌在列表里全显示为迁移当天，而列表正是按 `(uid, create_time)` 倒序。
4. **`SREM` 与 `DEL` 都要**：`SREM` 让它脱离 OAuth 索引（否则批量吊销仍会删它、与 PAT 语义冲突）；`DEL` 消除双副本（否则按 id 撤销删不掉旧副本）。插入抛异常时本批 Redis 一条不动。
5. **顺序固定** `INSERT → SREM → DEL`：先保证新表可查，再拆旧桶。默认不写 `tlc:` 缓存键（见 2.2）；将来启用缓存后，首次校验会按明文自动回填。
6. **幂等**：`INSERT IGNORE` + `uk_tlc_pat_token` 兜住重复明文（旧表无 token 唯一键）与重跑；`SREM`/`DEL` 均幂等。整批失败计入 `failed`，不阻断后续批次。

### 5.4 对账

```sql
SELECT COUNT(*) FROM tlc_pat WHERE LEFT(token, 8) <> 'tlc_pat_';  -- == migrated（按明文形态识别迁移行）
SELECT COUNT(*) FROM oauth_custom_token WHERE expire_time > NOW();  -- == total
CHECKSUM TABLE oauth_custom_token;                                   -- 迁移前后一致
```

再抽查 10 条（从旧表取明文）调 `GET /pat/ak-accounts/operators`，应全部 200；同一批令牌调 `GET /oauth2/userinfo` 应返回 `90009`（迁移已 `DEL uc:oauth:access:{token}`，旧链路不再认它们是预期行为，不是回归）。

## 6. 安全事件矩阵

| 事件 | 会话 | OAuth 令牌 | PAT |
| --- | --- | --- | --- |
| `resetPassword`（邮箱验证码） | 全踢 | 全吊销 | **不动** |
| `updatePassword`（旧密码） | 全踢 | 不动 | 不动 |
| `deleteClient` | — | 按 clientId 清理 | **不动** |
| 用户撤销某应用授权 | — | 按 uid + clientId 清理 | **不动**（缺口，见第 7 节） |

- **PAT 不参与任何安全事件联动。** 它只由用户自助入口处置（`/user/pat/revoke`、`/revoke-all`）；账号侧事件（改密、删应用）一律不碰 `tlc_pat`。这是产品口径，不是遗漏——写在这里是为了避免后人「顺手统一」。
- `resetPassword` 保持现状：`AuthServiceImpl.resetPassword:259-268` 只调 `kickAllSessions`。若将来改口径要求改密即失效，在这里追加一行 `patService.revokeAllByUid(uid)` 即可（服务方法已具备）。
- `updatePassword` 与 `resetPassword` 在凭证处理上完全一致（都只踢会话），差异仅在验证方式：旧密码 vs 邮箱验证码。
- `deleteClient` 靠 `revokeByPrefix` 扫旧键，只清 OAuth 令牌；**不补** `DELETE FROM tlc_pat`。删应用后其名下 legacy PAT 仍可用，记入第 7 节缺口。

## 7. 已知缺口

| 缺口 | 后果 | 何时处理 |
| --- | --- | --- |
| ~~PAT 暂无消费方~~（已闭合） | — | 已由 `/pat/ak-accounts/operators` 落地 |
| 家族索引成员未改写 | 迁移来的 legacy 令牌在刷新重放时不参与整族吊销 | 阶段二，迁移时顺手加两行 `SREM`/`SADD` |
| 重置密码不清 PAT | 邮箱验证码找回后，旧 PAT 仍可用 | 暂不处理（产品口径：PAT 由用户自行撤销） |
| 删客户端不清 PAT | 应用被删后，其名下 legacy PAT 仍可用 | 暂不处理 |
| 撤销应用授权不清 `tlc_pat` | 用户撤销某应用授权后，该应用名下旧长期令牌仍可用 | 阶段二，一句 `DELETE ... WHERE uid=? AND client_id=?` |
| 并发撤销无栅栏 | 迁移期间撤销，少数令牌可能「撤销后又被写回」 | 迁移时停服，或接受 |
| 孤儿令牌（Redis 有、旧表无） | 迁不了，留在旧桶自然过期 | 接受 |
| 旧表明文常驻 | 库里仍有 2 万条可用明文 | 阶段二导出并删除 |

## 8. 落地

不排日期，按批次推进，每批可独立上线、独立回滚。

| 批次 | 内容 | 完成判据 |
| --- | --- | --- |
| 1 | 建表 + 实体 `TlcPat` + `TlcPatMapper` + `PatUtil` + `RedisKeyUtil` 四方法 | 建表成功；`generate` 输出 72 字符；签发后 `tlc_pat.token` 与响应明文逐字相同 |
| 2 | 五个管理接口 + `/oauth2/pat/list` + `/oauth2/pat/revoke` + `WebConfig` 注册 | 不带 token 访问这两个 `/oauth2/pat/**` 端点均返回 `90009` |
| 3 | 迁移 Controller（不执行） | 先在测试库跑通：`migrated == total`，重跑不翻倍 |
| 4 | 阶段二：消费端校验形态（已完成） | `/pat/**` 独立拦截器按明文查库，见《PAT消费端接口设计.md》 |
| 5 | 执行迁移 + 对账 | 第 5.4 节三项全过 |
| 6 | 旧 6 端点 `410`、前端切换、导出删旧表 | 旧端点日志归零 |

批次 1–3 是阶段一，**不改变任何既有行为**（不接线任何安全事件、不动认证链路），回滚只需回滚 jar + `DROP TABLE tlc_pat`。

## 9. 验证清单

**单测**

- `PatUtil`：`generate` 输出 `tlc_pat_` + 64 hex；`isPat` 对旧 64 位 hex 返回 `false`
- 签发：`name`/`expires_in` 缺失或超上限报 `10001`；`scope` 提权被拒
- 撤销：跨用户 `id` 幂等成功且不误删；撤销后查不到
- `/oauth2/pat/revoke`：按明文删行；`client_id` 不符或 `direct_auth_enabled=0` 时删不掉；不存在的明文幂等成功
- `revoke-all`：只删本人，不影响他人
- `payload`：字段类型不匹配（如传数组）报 `10001`；序列化后超 4KB 报错；全空实体 `{}` 落库为 `NULL`；未知键被忽略、缺失字段为 `null`

**冒烟**

- 签发 → 列表出现（`token` 明文与签发响应一致）→ 撤销 → 列表消失
- `payload` 传 `{}`、只填 `game_uid`、或只填 `game_name` → 签发成功；传 `{"game_uid":"12345678","game_name":"明日方舟","unknown":1}` → 列表只回传两个已定义字段，`unknown` 被忽略
- 历史行 `{"keep":"me"}`（全未知键）→ 列表与消费端都不报错，按「无有效 payload」处理
- `/oauth2/pat` 不带 access_token、请求体带有效 refresh_token → 200 且 `client_id` 为凭证所属应用；凭证无效 → `90009`；未开通直连 → `90014`；请求 scope 超出凭证范围 → `90003`
- `/oauth2/pat/list` 无 token → `90009`；有 access_token → 只返回该用户记录
- `/oauth2/pat/revoke` 无 token → `90009`；带 access_token 吊销本应用名下一条 → 列表少一条、该令牌失效；换另一个应用的同用户 access_token 去吊销 → 删不掉

**零影响专项**

- 旧 5 个 `custom-tokens` 端点与 `/oauth2/token/from-refresh` 行为不变
- `CHECKSUM TABLE oauth_custom_token` 上线前后一致
- 存量 access_token 校验正常（**迁移前**旧令牌打 `/oauth2/userinfo` 仍 200；迁移后应为 90009）
