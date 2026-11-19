# PAT（个人访问令牌）接口文档

> 名称兼容：示例统一使用 `TCL-Token`、`TCL-Client-Id`、`tcl_ticket`；对应入口仍接受旧名称 `UC-Token`、`UC-Client-Id`、`uc_ticket`。同一项新旧名称同时提供且非空值不一致时返回 `10001`；令牌解析仍优先使用 `Authorization: Bearer`。此说明不新增端点的认证要求，也不改变归档接口的历史状态。

> 面向调用方：本系统自有前端、已开通直连认证的应用后端、以及需要用长期凭证读取数据的脚本/服务。
> 全貌（分层、链路、未完成工作）见 `docs/pat/PAT整体架构.md`；接口总览见 `docs/API.md`；
> 存储与迁移方案见 `docs/archive/PAT改造方案-最终版.md`；消费端取数设计见 `docs/archive/PAT消费端接口设计.md`；
> 落地差异见 `docs/archive/PAT落地核对报告.md`。
> 版本基线：2026-11-19（全链路明文 + 轮换接口已删除 + 应用侧 refresh_token 签发；旧直签链路 6 端点下线，
> 新旧对应关系见 2.4）。

## 1. 快速上手

三步即可用 PAT 读数据：

```bash
# 1. 用登录会话签发一枚 PAT（记得在 payload 里指定要读的游戏账号）
curl -X POST https://<host>/user/pat \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <会话token>' \
  -d '{"name":"我的脚本","expires_in":2592000,"scope":"arknights-data.read","payload":{"game_uid":"12345678","game_name":"明日方舟"}}'
# 响应 data.token 就是明文令牌，形如 tlc_pat_<64位hex>

# 2. 随时可以回看（服务端保存明文，不怕丢）
curl https://<host>/user/pat/list -H 'Authorization: Bearer <会话token>'

# 3. 用 PAT 调消费面接口
curl https://<host>/pat/ak-accounts/operators -H 'Authorization: Bearer <PAT明文>'
```

**容易搞错的一点**：`/user/pat/**` 和 `/oauth2/pat/**` 是**管理** PAT 的接口，凭证仍是会话 / access_token；唯一例外是 `POST /oauth2/pat`（凭请求体 refresh_token 签发，见 6.1）。只有 `/pat/**` 才把 PAT 当作请求凭证。

## 2. 认证域与凭证形态

### 2.1 URL 一级前缀 = 认证域

| 前缀 | 认证域 | 凭证 | 拦截器 |
| --- | --- | --- | --- |
| `/user/**` | 用户会话 | 登录后返回的会话 token | `UserAuthInterceptor` |
| `/oauth2/**` | OAuth access_token | 授权签发的访问令牌（**例外**：`POST /oauth2/pat` 用请求体 refresh_token） | `OAuthAuthInterceptor` |
| `/pat/**` | **PAT** | 个人访问令牌明文 | `PatAuthInterceptor` |
| `/open/**` | 完全公开 | 无 | 无 |
| `/internal/**` | 不对外 | 无（须限定内网） | 无 |

三类凭证**互不识别**：PAT 不能当 access_token 用，`/oauth2/**` 链路也不认 PAT；反之亦然。

### 2.2 两代令牌

消费面同时接受两种形态，服务端按**是否有前缀**分流，两者都是明文入库、明文比对：

| 代际 | 形态 | 来源 | 目标账号如何决定 |
| --- | --- | --- | --- |
| 新令牌 | `tlc_pat_` + 64 位小写 hex，共 **72 字符** | `POST /user/pat` 或 `POST /oauth2/pat` | 取 `payload.game_uid`（终末地另取 `server_id`），并强制核对账号绑定关系 |
| 旧令牌 | 纯 **64 位小写 hex**，无前缀 | 由存量自定义令牌迁移进 `tlc_pat` | 忽略 payload；明日方舟按「用户默认来源 → 最近变更」，终末地取当前用户已绑定角色中最近保存的玩家数据 |

> 旧令牌迁移时**原样保留明文**写入新表，不换形态、不重算任何值。已经发出去多年的凭证，调用方无需更换字符串，只需把请求打到 `/pat/**`。

### 2.3 怎么携带凭证

优先 `Authorization: Bearer <PAT明文>`，也支持 `TCL-Token: <PAT明文>`；两者都取时以 `Authorization` 为准，值会自动 `trim()`。

```http
GET /pat/ak-accounts/operators HTTP/1.1
Authorization: Bearer tlc_pat_0123456789abcdef...
```

凭证**不得**放在 URL 查询串里。

### 2.4 旧 API → 新 API 对应关系

> **2026-11-19 起**，旧「自定义有效期 access_token」链路的 6 个端点已从代码中删除，请求直接返回 **404**——不是 410 过渡，也没有兜底响应。旧调用方按下表改打新地址即可。

| 旧 API（已下线） | 原用途 | 新 API | 差异要点 |
| --- | --- | --- | --- |
| `POST /user/oauth/custom-tokens` | 用登录会话换一个长有效期 access_token | `POST /user/pat` | 新端点签发的是 **PAT**（`tlc_pat_` 前缀），不是 access_token；消费面从 `/oauth2/**` 换成 `/pat/**`；`name` 仍必填、`expires_in` 上限不变，新增可选 `payload`（`game_uid` 决定读谁的干员数据） |
| `GET /user/oauth/custom-tokens` | 「我的自定义令牌」列表 | `GET /user/pat/list` | 字段基本对齐，新增 `tokenPrefix`、`payload`、`lastUsedAt`、`expiresInSeconds`；迁移来的旧令牌 `payload=null` |
| `POST /user/oauth/custom-tokens/revoke` | 按明文吊销我的一条令牌 | `POST /user/pat/revoke` | 改为按 `id` 撤销（列表项自带 `id`），不再按明文；另有 `POST /user/pat/revoke-all` 一键全撤销 |
| `POST /oauth2/token/from-refresh` | 拿 refresh_token 换长有效期 access_token | `POST /oauth2/pat` | 同样「不消费、不轮转」refresh_token，但产出 PAT；不再接受 `client_id`，归属由凭证自身决定；不再产出新的长期 access_token |
| `GET /oauth2/custom-tokens` | 应用视角的签发记录列表 | `GET /oauth2/pat/list` | 准入仍是 `direct_auth_enabled=1`；改为按当前用户 `uid` 查询全部 PAT，跨应用返回，不再按 `clientId` 过滤 |
| `POST /oauth2/custom-tokens/revoke` | 应用视角按明文吊销 | `POST /oauth2/pat/revoke` | 支持 `id` 或 `token` 二选一，精确删除当前用户的 PAT（跨应用），调用应用须开通直连认证，幂等 |

配套变化：

- **已发出去的旧令牌字符串不用换**。迁移后它们原样留在 `tlc_pat`，调用方只把请求地址从 `/oauth2/**` 改成 `/pat/**`。
- 旧链路已无写入方，`oauth_custom_token` 表冻结为只读，只由一次性迁移端点遍历（见第 8 节）。
- 旧端点配套的服务方法、DTO/VO 与拦截器路径已一并删除，代码里不再有兼容分支；遇到 404 请直接改调新地址，不要指望灰度开关。

## 3. 通用约定

### 3.1 请求与响应

- 管理面请求体为 `Content-Type: application/json`。字段名同时接受下划线与驼峰：`expires_in` / `expiresIn`、`client_id` / `clientId`。
- 统一响应体 `{ "code": 200, "msg": "操作成功", "data": ... }`；**是否成功以 `code` 判断**，不要只看 HTTP 状态码。
- 时间字段为 `LocalDateTime` 序列化后的 ISO 8601 字符串（如 `2026-12-18T15:00:00`）。
- 列表接口的 `expiresInSeconds` 为**剩余有效期（秒）**，`≤ 0` 表示已过期；已过期记录仍会出现在列表里，不会自动清除。

### 3.2 错误码

| code | 含义 | 本接口族中的典型触发点 |
| --- | --- | --- |
| `10001` | 参数错误 | `name` 空/超长、`expires_in` 非正或超上限、`payload` 字段类型不匹配（如传数组）或序列化后超 4KB、`client_id` 传了非保留值、`id`/`token` 为空 |
| `10002` | 参数校验失败 | DTO 注解校验不通过（缺必填字段、长度超限）；新令牌 `payload.game_uid` 缺失或为空白；终末地角色/服务器格式非法或旧令牌无可用默认数据 |
| `80001` | 未登录或登录已失效 | 消费面 PAT 缺失、格式不符、查不到或已过期；管理面会话/access_token 失效 |
| `80008` | 无权限操作 | 消费面 scope 不足，或目标游戏账号不归属令牌用户 |
| `90003` | 请求的权限范围未授权 | 签发时 `scope` 超出可自助范围，或试图签 `all` |
| `90014` | 客户端未开通直连认证能力 | 全部 `/oauth2/pat` 管理接口的准入失败（签发、列表、单条撤销、全部撤销） |
| `90009` | OAuth 令牌无效 | 迁移完成后旧令牌再打 `/oauth2/userinfo`（预期行为，不是故障） |

> 无效 PAT 统一返回 `80001`，**不区分**「不存在」「已过期」「格式错误」，避免泄露令牌状态。

## 4. 全链路明文：业务取舍与安全要求

PAT **不做摘要**。`tlc_pat.token` 保存令牌明文本身，Redis 预留键（阶段二启用时）用明文，迁移写入用明文，用户侧与应用侧列表都返回完整明文。换取的是：令牌随时可回看、旧令牌无感迁移、消费端一次精确匹配、校验与吊销口径统一。

代价是整条链路都成为敏感数据源，调用方需配合：

- 所有 PAT 相关请求**必须走 TLS**；凭证不得写入 URL、日志、异常上报、监控与前端埋点。
- 列表响应体含完整凭证，前端不要把整个响应打到控制台/埋点，也不要缓存到 localStorage 之外的共享位置。
- 服务端侧配套：数据库账号最小权限、备份加密、库表 `token` 列 `utf8mb4_bin`（不让 general_ci 折叠 hex 大小写）、日志/异常不落明文。
- 令牌泄露后没有「临时禁用」状态，只能 `revoke`（删行）后立即重新签发。

## 5. 用户自助管理面（`/user/pat/**`，登录会话鉴权）

### 5.1 签发 `POST /user/pat`

| 字段 | 必填 | 约束 | 说明 |
| --- | --- | --- | --- |
| `name` | 是 | 去空白后非空、≤ 50 字符 | 用途名称，便于在列表里辨认 |
| `expires_in` | 是 | 正整数，上限 `318038400` 秒（10 年零 1 个月） | 有效期（秒），签发后不可改 |
| `scope` | 否 | ≤ 256 字符，英文逗号分隔 | 只能在系统可自助范围内**收敛**；不传取可自助范围全集；不接受元范围 `all` |
| `client_id` | 否 | 留空或 `this_land_cloud` | PAT 只承载个人令牌，传其它值返回 `10001` |
| `payload` | 否 | 必须是 JSON object，序列化后 ≤ 4KB | 自定义结构 `PatPayload`；**所有字段均可为空**，可传 `{}` 或只填部分。消费端使用 `game_uid` 定位账号，终末地另用 `server_id` 定位服务器 |

**payload 是固定结构的实体，不是任意 KV**：DB 里仍存 JSON 文本，读取时才反序列化成 `PatPayload`。JSON 里未定义的键会被静默忽略，实体里未提供的字段保持 `null`；因此列表与签发响应只回传已定义字段。当前字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `game_uid` | string / null | 明日方舟账号 UID / 终末地角色 `roleId`，可空；也接受驼峰别名 `gameUid` |
| `game_name` | string / null | 游戏名称，可空；当前不参与鉴权与取数，仅随签发与列表透传；也接受驼峰别名 `gameName` |
| `server_id` | string / null | 终末地服务器 ID，也接受驼峰别名 `serverId`；读取时 trim，省略或空白按 `""` 精确定位，不自动选择其它服务器；明日方舟接口忽略此字段 |
| `source` | string / null | 令牌来源标识，可空；由签发方自行填写（如 `web`、`mini_app`、`script`），服务端**不校验取值、不参与鉴权与取数**，仅随签发、列表与消费端解析透传，便于在列表中辨认令牌用途 |

四个字段都**允许全空或部分为空**：不传 payload、传 `{}`、或只填其中任意几个都合法。`game_name` / `source` / `server_id` 单独存在时 `payload` 不算空，会正常落库与回显，但新令牌消费时仍会因缺 `game_uid` 返回 `10002`。后续新增字段不需要迁移存量数据，缺字段的历史行自动为 `null`。

`payload.game_uid` 的取值规则（消费端强制）：字段声明为 `String`，JSON 里写成整数会被 Jackson 转成十进制字符串；取值时 trim，空白、缺失一律返回 `10002`。布尔/浮点标量也会被 Jackson 强转成字符串（不再由取值层拒绝，最终由绑定校验挡下），对象/数组则在请求体绑定阶段报 `10001`。**新令牌必须提供 `game_uid`**，否则读取干员或终末地玩家数据时返回 `10002`。签发后没有修改接口，改账号或服务器只能撤销后重新签发。

请求示例：

```json
{
  "name": "干员数据同步脚本",
  "expires_in": 2592000,
  "scope": "arknights-data.read",
  "payload": { "game_uid": "12345678", "game_name": "明日方舟", "source": "script" }
}
```

响应 `data`（`PatIssueVO`）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 记录主键，撤销按它定位 |
| `token` | string | **令牌明文**（72 字符） |
| `tokenPrefix` | string | 令牌前 12 位，仅用于辨认 |
| `name` / `clientId` / `scope` / `expiresIn` | — | 本次签发参数 |
| `expireTime` | datetime | 到期时间 |
| `payload` | `PatPayload` / 省略 | 结构化对象（未知键忽略、缺失字段为 null）；为 `null` 时字段省略 |
| `createdAt` | datetime | 签发时间 |

```json
{ "code": 200, "msg": "操作成功", "data": {
  "id": 21, "token": "tlc_pat_<64位hex>", "tokenPrefix": "tlc_pat_012",
  "name": "干员数据同步脚本", "clientId": "this_land_cloud",
  "scope": "arknights-data.read", "expiresIn": 2592000,
  "expireTime": "2026-12-18T15:00:00",
  "payload": {"game_uid": "12345678", "game_name": "明日方舟", "source": "script"},
  "createdAt": "2026-11-18T15:00:00" } }
```

### 5.2 列表 `GET /user/pat/list`

返回本人全部 PAT（含已过期记录），按签发时间倒序。每项字段：

| 字段 | 说明 |
| --- | --- |
| `id` / `name` / `clientId` / `scope` / `expiresIn` / `expireTime` / `createdAt` | 同签发响应 |
| `token` | **完整令牌明文**（新令牌 72 字符 / 迁移旧令牌 64 位 hex） |
| `tokenPrefix` | 前 12 位 |
| `payload` | `PatPayload` 对象；只含已定义字段，未知键被忽略、缺失字段为 `null`；旧令牌为 `null` |
| `lastUsedAt` | 阶段一恒为 `null`（消费端暂不回写使用时间） |
| `expiresInSeconds` | 剩余有效期（秒），≤ 0 即已过期 |

> 表中不设独立来源字段：新签发令牌固定带 `tlc_pat_` 前缀（用户自助签发 `clientId=this_land_cloud`，应用侧签发为应用 ID），迁移来的旧令牌是纯 64 位 hex 且 `payload=null`，据此即可区分两代。

### 5.3 撤销 `POST /user/pat/revoke`

```json
{ "id": 21 }
```

按 `id + uid` 物理删行，**幂等**：id 不存在或不是本人的记录，返回成功但不删任何行。响应 `data` 为 `null`。

### 5.4 全撤销 `POST /user/pat/revoke-all`

无请求体，删除本人名下全部 PAT。响应 `data` 为 `null`。

> 账号侧安全事件（改密、删除 OAuth 应用）**不联动** PAT，需要用户自助调用本接口。
> **没有轮换接口**：换令牌走「`revoke` + 重新签发」。想继承 `name` / `expires_in` / `payload`，用列表项预填签发表单即可。

## 6. 应用视角管理面（`/oauth2/pat/**`）

全部接口仅限直连应用调用，功能与 user 侧一致：签发、查看当前用户全部 PAT、撤销单条、全部撤销。`/list`、`/revoke`、`/revoke-all` 走 access_token 鉴权；根路径 `POST /oauth2/pat` 凭请求体 refresh_token 签发（见 6.1）。

准入条件：客户端启用、已审批且 `direct_auth_enabled=1`；未开通直连认证返回 `90014`。列表/吊销的 `uid` 与 `clientId` 均取自 access_token，调用方无法伪造；签发的 `uid` 与 `clientId` 取自 refresh_token 记录，同样无法伪造。列表与吊销均支持当前用户跨应用的 PAT，`clientId` 仅用于准入校验；签发归属仍为 refresh_token 所属应用。

### 6.1 签发 `POST /oauth2/pat`

**凭证**：请求体的 `refresh_token`。本端点不经过 access_token 拦截器（`WebConfig` 用 `excludePathPatterns("/oauth2/pat")` 精确排除根路径），属自带协议级认证的端点。refresh_token **不消费、不轮转**，签出 PAT 后原凭证继续有效。

请求体（`PatIssueByRefreshRequest`）：

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| `refresh_token` | 是 | 有效的 OAuth refresh_token 明文；无效/已过期返回 `90009` |
| `name` | 是 | 同 5.1，≤ 50 字符 |
| `expires_in` | 是 | 同 5.1，上限 10 年零 1 个月 |
| `scope` | 否 | 与 user 侧一致，可选择系统全部自助权限，不受 refresh_token 自身 scope 限制；不传取全部自助权限，`all` 或不支持的 scope 返回 `90003` |
| `payload` | 否 | 同 5.1 的 `PatPayload`，所有字段可空，`game_uid` 规则一致 |

**不接受 `client_id`**：目标应用由凭证自身决定，服务端把 refresh_token 记录里的 `clientId` 写入 `tlc_pat.client_id`。这样签出的 PAT 能被 `/oauth2/pat/list` 列出、被 `/oauth2/pat/revoke` 吊销，用户侧 `/user/pat/list` 同样可见。响应结构与 5.1 的 `PatIssueVO` 完全一致，仅 `clientId` 是应用 ID 而非 `this_land_cloud`。

请求示例：

```json
{
  "refresh_token": "<OAuth refresh_token 明文>",
  "name": "服务端同步任务",
  "expires_in": 31536000,
  "scope": "arknights-data.read",
  "payload": { "game_uid": "12345678", "game_name": "明日方舟" }
}
```

其它约束：签发准入与列表/吊销一致（`direct_auth_enabled=1`，否则 `90014`）；客户端未启用/未审批按 `90001`/`90013`；请求体含长期凭证，须走 TLS 且不得写入访问日志、监控与前端埋点。

### 6.2 列表 `GET /oauth2/pat/list`

返回当前登录用户的全部 PAT，结构与 5.2 完全一致（同样含完整 `token` 明文、含已过期记录，按签发时间倒序）。仅按 access_token 解析出的 `uid` 过滤，包含用户自助签发和其它应用签发的 PAT，不返回其它用户的记录。

调用应用仍须启用、已审批且 `direct_auth_enabled=1`；access_token 中的 `clientId` 仅用于准入校验，不参与列表过滤。不接受调用方通过请求参数指定用户或应用。单条撤销和全部撤销同样支持跨应用，始终限定为当前用户的 PAT。

### 6.3 撤销 `POST /oauth2/pat/revoke`

与 user 侧一样，可以使用列表项的 id 撤销：

```json
{ "id": 21 }
```

也保留按明文撤销的用法：

```json
{ "token": "<PAT明文>" }
```

| 字段 | 必填 | 约束 |
| --- | --- | --- |
| `id` | 与 token 二选一 | PAT 记录主键，与 user 侧一致 |
| `token` | 与 id 二选一 | 非空白、≤ 128 字符 |

服务端按 `uid + id` 或 `uid + token` 精确删除（token 在控制器入口 `trim()`，服务层只做精确匹配），**幂等**，不存在、已撤销或不属于当前用户时仍返回成功。两个字段都不传、同时传入或 token 为空白返回 HTTP 400、`code=10002`。已开通直连认证的应用可撤销当前用户跨应用的 PAT，包含用户自助签发和其它应用签发的令牌；不能撤销其它用户的 PAT。`clientId` 仅用于准入校验，不作为删除条件。

### 6.4 全部撤销 `POST /oauth2/pat/revoke-all`

无需请求体，使用 `Authorization: Bearer <access_token>`。与 `POST /user/pat/revoke-all` 一致，仅按认证凭证中的 uid 删除当前用户全部 PAT（跨应用，包含已过期记录），幂等。调用应用须启用、已审批且 `direct_auth_enabled=1`；用户与客户端身份均不接受请求参数覆盖。

## 7. 消费面：用 PAT 读游戏数据

### 7.1 `GET /pat/ak-accounts/operators`

- 鉴权：PAT 明文 + `arknights-data.read` scope；上级 `gama-data.read` 与元范围 `all` 可覆盖。
- 请求参数：**无**。控制器方法不声明任何参数，账号完全由 PAT 决定；多传的 `akUid` 等查询参数由框架忽略，不参与取数，也不会报错。**不要依赖请求参数指定账号**，请以签发时写入的 `payload.game_uid` 为准。
- 响应：`Result<OperatorListVO>`，与 `GET /oauth2/ak-accounts/operators` **逐字段一致**，共用同一取数服务；`Cache-Control: private, no-cache`（只允许私人缓存且每次回源校验）。

### 7.2 明日方舟目标账号如何决定

| 令牌 | 行为 |
| --- | --- |
| 新令牌（带 `tlc_pat_`） | 取 `payload.game_uid`，显式以 `source=skland` 进入业务层，绕过「akUid 等于本人 uid 时按手动版读取」的撞号兼容分支，**强制核对 `ak_account_binding`**；未绑定返回 `80008` |
| 旧令牌（无前缀 64 位 hex） | 不指定账号，按「用户默认来源 → 最近变更」取数，等价于 OAuth 版不传 `akUid` |

### 7.3 明日方舟与 OAuth 版的差异速查

| 维度 | `GET /oauth2/ak-accounts/operators` | `GET /pat/ak-accounts/operators` |
| --- | --- | --- |
| 凭证 | access_token（Redis，默认 2 小时） | PAT（库内明文，签发时定有效期） |
| 目标账号 | 请求参数 `akUid` + 可选 `source` | 令牌自身决定，接口不声明查询参数（多余参数被忽略） |
| 响应结构 | `OperatorListVO` | 完全一致 |
| 缓存头 | `private, no-cache` | 完全一致 |

### 7.4 `GET /pat/ef-accounts/player-data`（终末地玩家数据）

- 鉴权：PAT 明文 + `endfield-data.read`；上级 `gama-data.read` 与元范围 `all` 可覆盖。只有 `arknights-data.read` 或 `endfield-data.write` 不可读取。
- 请求参数：**无**。`roleId`、`serverId`、`uid` 等查询参数均不参与取数，不能覆盖 PAT 中的角色与用户。
- 新令牌：`payload.game_uid` 对应终末地角色 `roleId`，trim 后必填；`payload.server_id` 对应服务器 ID，trim 后省略或空白按 `""` 定位。服务层按 `roleId + serverId + 当前用户 uid` 校验 `ef_account_binding`，未绑定返回 `80008`，不回退其它服务器或角色。
- 旧令牌：忽略 payload，不指定角色或服务器；读取当前用户已绑定角色中最近保存的玩家数据，与 OAuth 版不传参数一致。按玩家数据保存时间倒序，同一时间按角色、服务器升序；无可用数据返回 `10002`。
- 响应：`Result<EfPlayerDataVO>`，字段为 `roleId`、`serverId`、`playerData`（JSON 对象）、`updateTime`；与 `GET /oauth2/ef-accounts/player-data` 一致，共用 `EfPlayerDataService#getPlayerData`。绑定存在但数据行缺失时返回成功、`data=null`。缓存头为 `Cache-Control: private, no-cache`。
- 格式约束：`game_uid` trim 后不超过 32 位可见 ASCII 字符，`server_id` trim 后不超过 16 位可见 ASCII 字符，非法返回 `10002`。已有 PAT 缺少服务器字段时按空串定位；若实际绑定使用 `"1"`，需重新签发并填入 `server_id: "1"`。

签发示例（使用会话凭证调用 `POST /user/pat`，或按 6.1 使用 refresh_token 调用 `POST /oauth2/pat`）：

```json
{
  "name": "终末地数据同步",
  "expires_in": 2592000,
  "scope": "endfield-data.read",
  "payload": {
    "game_uid": "123456789",
    "server_id": "1",
    "game_name": "终末地",
    "source": "script"
  }
}
```

读取示例：

```bash
curl https://<host>/pat/ef-accounts/player-data \
  -H 'Authorization: Bearer <PAT明文>'
```

`game_name` 与 `source` 仍为展示信息，游戏读取权限由 scope 决定。本接口仅读取已保存的玩家数据；角色列表、数据上传与寻访记录接口见 `docs/oauth/OAuth终末地数据接口文档.md`。

## 8. 旧令牌迁移与消费

存量自定义令牌（`oauth_custom_token`）会由一次性迁移任务写入 `tlc_pat`，明文原样保留、`payload=null`（不带来源列，靠无前缀的 64 位 hex 形态识别）。**搬迁是全量的**：所有未过期行都会写入，不判断该令牌在 Redis 中是否还有明细，因此已被撤销的令牌会重新变成可用凭证（详见下方端点说明）。迁移同时删除对应的 Redis 键 `uc:oauth:access:{token}`，因此：

- **迁移后旧令牌不能再打 `/oauth2/**`**，应改打 `/pat/ak-accounts/operators`（同一串明文，无需更换）。
- 打旧链路会返回 `90009`，这是预期行为，不是回归。
- 消费端与迁移**必须同批上线**，否则会出现「旧令牌两边都不认」的失效窗口。

### 内部迁移端点 `GET /internal/pat-migration/run`

一次性运维端点，**不设开关**：随应用启动直接注册，无参数、无鉴权、同步执行；用 `GET` 而非 `POST`，运维在浏览器地址栏直接访问即可触发。既然没有开关，暴露面只能靠部署侧兜住——必须限定内网访问（网关、安全组或白名单），不得对外网开放。前置条件是新链路已能校验 PAT、旧表已备份、迁移期间停写；**跑完立即删除该 Controller 并重新发布**，不要把它留在后续版本里。

服务层 `run()` 已加锁串行：重复触发会排队执行，后一次因明文已入库被唯一键忽略而不再插入，`migrated + failed == total` 的口径不会被并发双跑打乱。

响应 `data`：`total`（待处理总数）、`migrated`（本批处理成功数）、`skipped`（全量搬迁下恒为 0，保留字段仅为兼容既有响应结构）、`failed`（整批异常，不阻断后续）、`elapsedMs`。满足 `migrated + failed == total`；重复调用幂等，不会翻倍。

> **搬迁口径：全量，不判有效性。** 旧表里「用户撤销过授权、Redis 明细已删但签发记录仍在」的行会一并迁成**可用 PAT**（即「复活」）。该取舍已确认接受，用来换取迁移不必逐条往返 Redis、整轮可在秒级完成。若某次部署不能接受，必须改用按 Redis 明细筛选的版本，不能直接跑本端点。

## 9. 其它约束

- PAT 的 `scope` 是**签发时快照**，用户后续调整应用授权不会回溯到已签发的令牌；需要变更权限请撤销后重新签发。
- 撤销 = 物理删行，没有软删、墓碑或禁用状态；生效即时。
- `POST /oauth2/pat` 是 `/oauth2/**` 认证域里唯一的协议级例外：不带 access_token，凭请求体 refresh_token 签发；`WebConfig` 只排除根路径，`/list`、`/revoke`、`/revoke-all` 受 access_token 保护。四个管理接口都校验直连应用准入，签发权限与 user 侧一致。
- 阶段一不启用 Redis 校验缓存，每次消费请求按明文查一次库；阶段二若启用 `tlc:pat:*` 缓存，Redis 也需按敏感数据源管理。
- 已有库升级**不能重跑** `db/schema-user.sql`（含 `DROP TABLE IF EXISTS`），需走增量 DDL；本次删除 `tlc_pat.source` 列同样需单独对已有库执行增量变更。
- 旧 6 端点已于 2026-11-19 直接下线（删除，非 410），对应关系见 2.4；**前端若仍调旧地址会 404**，须先切到 `/user/pat/**`、`/oauth2/pat/**`。
- 尚未实现：前端切换验证、旧表导出删除、`last_used_at` 回写、家族索引成员改写、撤销应用授权联动清迁移来的 PAT。详见 `docs/archive/PAT落地核对报告.md` 第 5 节。
