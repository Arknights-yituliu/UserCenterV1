# OAuth 令牌直签接口文档

## 1. 文档说明

本文档描述 UserCenter 的**令牌直签**接口：调用方直接提交一份「已有凭证」（用户登录会话或 OAuth `refresh_token`），换取指定有效期的 `access_token`。

按凭证类型拆分为两个独立端点，归属域与认证方式均不同：

```http
POST /user/oauth/custom-tokens    # 凭证为当前登录会话（用户态，需携带会话请求头）
POST /oauth2/token/from-refresh   # 凭证为 refresh_token（协议域，凭证在请求体）
```

| 端点 | 凭证 | 认证方式 | 权限基准 | 典型调用方 |
| --- | --- | --- | --- | --- |
| `/user/oauth/custom-tokens` | 当前登录会话 | `UserAuthInterceptor` 从请求头解析 → `UserContext` | 用户对目标应用的有效授权（受自定义授权范围约束） | **本系统自有前端**（用户已登录） |
| `/oauth2/token/from-refresh` | `refresh_token` | 端点内校验凭证，无拦截器 | 刷新令牌记录中的 scope（不重算用户自定义范围） | 已持有 OAuth 刷新令牌的服务端 |

### 1.1 与标准协议的关系

两个端点均**不属于 RFC 6749 定义的任何 grant type**：

| 维度 | 标准 `/oauth2/token` | 本端点 |
| --- | --- | --- |
| 支持的 grant | `authorization_code` / `refresh_token` | 无 `grant_type` 概念，由端点路径决定凭证类型 |
| 客户端认证 | `client_secret_post` | **不需要** `client_secret` |
| 认证依据 | 授权码 / 刷新令牌 + 客户端密钥 | 登录会话（`/user/oauth/custom-tokens`）或刷新令牌（`/token/from-refresh`） |
| 换取目标 | 按客户端登记 TTL 签发 | **调用方指定** `expires_in` |
| 是否轮转 refresh | 轮转模式下会换发 | **不轮转**，凭证保持有效 |

需要强调二者的定位差异：`/user/oauth/custom-tokens` 是**本系统前端的用户自助入口**，与 `GET /user/oauth/custom-tokens`（查我的自定义令牌）同属 `/user` 用户态资源；它没有 `client_secret` 的概念，只认登录会话。`/oauth2/token/from-refresh` 则是**面向服务端**的非标准扩展（如 BackEndV3 → UC）。二者都不面向标准 OAuth 客户端库。协议兼容性说明见《OAuth2 与标准协议差距清单》第 3 节。

### 1.2 典型用途

- **前端自助签发**（`/user/oauth/custom-tokens`）：系统自有前端在用户登录态下，为当前用户签发一个短时 `access_token`，供用户在「我的自定义令牌」中复制复用。
- **最小权限换取**（`/oauth2/token/from-refresh`）：用宽范围的 `refresh_token` 换一个**只含必要权限**的短时令牌，缩小泄露面（见第 6 节）。

## 2. 通用约定

### 2.1 认证

两个端点的认证方式**完全不同**：

- `/user/oauth/custom-tokens`：注册在 `UserAuthInterceptor` 覆盖范围内（`/user/**`），**必须携带登录会话请求头**（`Authorization: Bearer` / `UC-Token`，与项目其他 `/user` 接口一致）。拦截器解析会话后把 `uid`、来源 `clientId` 注入 `UserContext`，因此**请求体中不需要传 `uc_token`**。
- `/oauth2/token/from-refresh`：**不注册任何拦截器**，HTTP 层不校验会话，认证完全由请求体中的 `refresh_token` 承担。

两者都**不需要 `client_secret`**。凭证不得出现在 URL 查询串、日志或监控中。

### 2.2 请求格式

`Content-Type: application/x-www-form-urlencoded`。

参数仅接受**下划线命名**（与项目其他接口一致）：`client_id`、`expires_in`、`scope`、`name`、`refresh_token`。

### 2.3 响应格式

统一为 `{code, msg, data}`；是否成功以响应体 `code` 判断，不能只看 HTTP 状态码。

## 3. 请求参数

两个端点共享业务参数，区别在于凭证来源：`/user/oauth/custom-tokens` 的凭证是**请求头中的登录会话**，`/oauth2/token/from-refresh` 的凭证是**请求体中的 `refresh_token`**。

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `refresh_token` | `/oauth2/token/from-refresh` 必填 | OAuth 刷新令牌，本端点的认证凭证 |
| `client_id` | 条件必填 | 目标客户端 ID。`/user/oauth/custom-tokens` 会话未携带来源客户端时必须传；`/oauth2/token/from-refresh` 时可不传，传了则须与令牌归属一致 |
| `expires_in` | 是 | 期望的 `access_token` 有效期（秒），必须为大于 0 的整数 |
| `scope` | 否 | 期望收敛到的权限范围，英文逗号分隔。省略表示沿用凭证原有范围 |
| `name` | 是 | 本次签发的名称，用户用于在「我的自定义令牌」中区分用途；去空白后非空且不超过 50 字符 |

约束：

- `/user/oauth/custom-tokens` **不需要也不接受** `uc_token` 参数：登录态由请求头会话提供，传了会被忽略。
- `/oauth2/token/from-refresh` 的 `refresh_token` 为必填，缺失返回 `10001`。
- `expires_in` **不受客户端配置的 TTL 限制**，由调用方自行保证合理（建议贴近实际调用窗口，不宜过长）。
- `name` 为**必填**：每次调用成功都会在 `oauth_custom_token` 表落一条记录（见第 5.4 节），供用户回看。

## 4. 响应

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "access_token": "access_token_value",
    "token_type": "Bearer",
    "expires_in": 600,
    "scope": "user.read"
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `access_token` | 新签发的访问令牌 |
| `token_type` | 固定 `Bearer` |
| `expires_in` | 实际有效期（秒），即请求值 |
| `scope` | 本次令牌实际携带的权限，逗号分隔 |
| `refresh_token` | **不返回**。本端点不签发新刷新令牌 |

## 5. 端点语义

### 5.1 会话直签（`POST /user/oauth/custom-tokens`，凭证为登录会话）

1. `UserAuthInterceptor` 从请求头解析登录会话，注入 `UserContext`；未登录或会话失效返回 `80001`。
2. 确定目标客户端：优先取会话记录的来源客户端 `clientId`（`UserContext.getClientId()`），为空时使用请求参数 `client_id`；两者都为空返回 `10001`。
3. 授权范围取 **`resolveGrantScope(client, null, uid)`**，即用户对当前应用的有效授权——**用户自定义授权范围表（`oauth_user_scope`）在此生效**，用户在确认页的裁剪/追加会被延续。

### 5.2 刷新令牌直签（`POST /oauth2/token/from-refresh`，凭证 `refresh_token`）

1. 读取刷新令牌记录，取 `uid`、`clientId`、`scope`、`familyId`；记录不存在返回 `90009`。
2. 若请求带了 `client_id`，必须与令牌归属的 `clientId` 完全一致，否则返回 `90009`（防止跨应用换发）。
3. 授权范围**直接沿用刷新令牌记录中的 `scope`**（不重新计算用户自定义范围）。

### 5.3 共同行为

- 新 `access_token` 写入 Redis，并登记到该用户的令牌反向索引 `uc:uid:oauth:{uid}`。
- 仅 `/oauth2/token/from-refresh` 路径：若刷新令牌带有 `familyId`（客户端开启了 `rotate_refresh_token`），新令牌**加入该令牌家族**：此后该家族被判定泄露或按应用整体撤回授权时，它会被一并作废。
- **不消费、不轮转凭证**：`refresh_token` 与登录会话在本调用后依然有效，可重复换取。
- **不写 `oauth_user_scope`**：本端点只做「换发更小范围的令牌」，不修改用户的授权记录。

### 5.4 签发记录落库（`oauth_custom_token`）

签发成功后，按请求的 `name` 在 `oauth_custom_token` 表落一条记录：

| 列 | 内容 |
| --- | --- |
| `uid` | 签发用户 |
| `client_id` | 目标客户端 ID |
| `name` | 请求方填写的名称（≤50 字符） |
| `token` | 本次签发的 `access_token` **明文** |
| `scope` | 本次令牌实际携带的权限范围 |
| `expires_in` / `expire_time` | 本次有效期（秒）与到期时间 |
| `create_time` / `update_time` | 记录创建/更新时间 |

- 该表是**查询投影**，令牌的权威仍在 Redis（TTL 由 Redis 控制）；写库失败只记录告警，**不阻断签发**。
- 记录通过用户侧接口 [第 10 节](#10-查询我的自定义令牌) 回看，**包含已过期记录**（过期与否由返回的剩余秒数体现，不依赖定时任务）。

## 6. scope 收敛语义（最小权限）

`scope` 只能**缩小**，不能扩大：

| 传入情况 | 结果 |
| --- | --- |
| 省略 | 沿用凭证原有范围 |
| 为凭证范围的子集 | 新令牌只携带该子集，响应 `scope` 即该值 |
| 含凭证范围之外的权限 | `90003`「scope 不能超出凭证已有权限」 |
| 含系统不支持的标识 | `90003`「存在系统不支持的授权范围」 |
| 空字符串 | `10001`「scope 不能为空」 |

补充规则：

- 判定基准是**凭证自身已有范围**。会话直签路径的基准即第 5.1 节的 `resolveGrantScope` 结果，因此同样受用户自定义授权范围约束。
- 凭证范围若为元范围 `all`，视为覆盖全部已知权限，任何系统已知的具体权限都属其子集。
- 请求集合中的每一项都必须是系统已知标识，未知字符串一律拒绝，防止任意值被写入令牌。

该约束使本端点**无法被用于提权**：即使凭证泄露，攻击者能拿到的权限也不会超过凭证本身。

## 7. 错误码

| HTTP | code | 含义 | 处理建议 |
| --- | --- | --- | --- |
| 200 | `10001` | 参数错误：`expires_in` 非正、`/oauth2/token/from-refresh` 未传 `refresh_token`、`scope` 为空串、`name` 为空或超过 50 字符、会话与请求均无 `client_id` | 修正请求参数 |
| 200 | `40001` | 系统繁忙（未处理异常） | 稍后重试并联系管理员 |
| 200 | `80001` | 未登录或登录已失效（`/user/oauth/custom-tokens` 会话无效） | 引导用户重新登录后重试 |
| 200 | `90001` | 客户端不存在或已停用 | 检查 `client_id`，停止调用并联系管理员 |
| 200 | `90003` | 权限范围不合法（超范围或未知标识） | 改为凭证已有范围的子集 |
| 200 | `90009` | 令牌无效、已过期，或与 `client_id` 不匹配 | 清除本地凭证并重新授权 |
| 200 | `90013` | 客户端待管理员审批或已被封禁 | 联系管理员完成审批或解除封禁 |

## 8. 安全注意事项

1. **无 `client_secret` 保护**：`/oauth2/token/from-refresh` 仅凭 `refresh_token` 本身认证，等价于「持有刷新令牌即可换发访问令牌」。凭证必须以服务端密钥管理方式保存，严禁下发到浏览器或客户端应用。`/user/oauth/custom-tokens` 则由登录会话认证，其安全边界等同用户自身的登录态。
2. **必须使用 HTTPS**；`/oauth2/token/from-refresh` 应通过网关对来源 IP 做白名单约束（本端点本身不做来源校验）。
3. **`expires_in` 由调用方决定**，务必按最小必要原则设置；本端点不会替调用方收敛过长有效期。
4. **不要把 `scope` 当作提权手段**：它在设计上只能下降。若业务确实需要为用户增加授权，应走用户本人的授权流程（确认页 `POST /oauth2/consent` 或用户侧接口 `POST /user/oauth/grants/scopes/grant`），这两处有明确的用户意图确认。
5. **日志脱敏**：凭证、`access_token` 明文不得写入日志、监控或错误上报。
6. **`access_token` 明文落库**：按需求本端点签发的 `access_token` 会以明文写入 `oauth_custom_token` 表，供用户在「我的自定义令牌」中复制复用。该表因此成为敏感数据源：需限制数据库访问权限、纳入备份加密与审计；如需下线该能力，应把 `token` 列加密存储或改为仅存摘要（届时用户将无法回看令牌原文）。

## 9. 调用示例

### 9.1 前端会话签发（沿用范围）

```http
POST {UC_BASE_URL}/user/oauth/custom-tokens
Authorization: Bearer {UC_SESSION_TOKEN}
Content-Type: application/x-www-form-urlencoded

expires_in=600
&name=dashboard-readonly
```

### 9.2 前端会话签发（收敛到只读）

```http
POST {UC_BASE_URL}/user/oauth/custom-tokens
Authorization: Bearer {UC_SESSION_TOKEN}
Content-Type: application/x-www-form-urlencoded

expires_in=600
&scope=user.read
&name=dashboard-readonly
```

### 9.3 用刷新令牌换取短时令牌

```http
POST {UC_BASE_URL}/oauth2/token/from-refresh
Content-Type: application/x-www-form-urlencoded

refresh_token={REFRESH_TOKEN}
&expires_in=300
&scope=config.read
&name=cron-config-sync
```

上述请求成功后返回新的 `access_token`，原 `refresh_token` 继续有效。

## 10. 查询自定义令牌

提供两个入口：用户侧（登录态，看本人全部记录）与 OAuth 侧（access_token，只看本应用记录且仅限已开通直连认证的客户端）。

### 10.1 用户侧查询（登录态）

查看当前用户在**本端点**签发的全部自定义有效期令牌（**含已过期记录**），供「我的自定义令牌」页面展示。

```http
GET /user/oauth/custom-tokens
```

- 认证：走用户登录态（`/user/**` 由 `UserAuthInterceptor` 保护），从会话取 `uid`，**无需传参**；非本人数据不返回。
- 排序：按签发时间 `create_time` 倒序。
- 过期标注：每条记录返回剩余秒数 `expiresInSeconds`（`TIMESTAMPDIFF(SECOND, NOW(), expire_time)`），**≤0 即已过期**，前端据此展示状态；服务端不做过滤，也没有清理定时任务。
- 吊销：作废其中一条令牌请用第 12.1 节的 `POST /user/oauth/custom-tokens/revoke`。

响应示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": 1001,
      "clientId": "back-end-v3",
      "clientName": "业务后台",
      "name": "dashboard-readonly",
      "token": "access_token_value",
      "scope": "user.read",
      "expiresIn": 600,
      "expireTime": "2026-10-09T10:30:00",
      "createdAt": "2026-10-09T10:20:00",
      "expiresInSeconds": 420
    }
  ]
}
```

| 字段 | 说明 |
| --- | --- |
| `id` | 记录主键 |
| `clientId` | 目标客户端 ID |
| `clientName` | 应用名称；应用被删除/停用时为空 |
| `name` | 签发时填写的名称 |
| `token` | `access_token` **明文**（安全说明见第 8 节第 6 条） |
| `scope` | 本次令牌携带的权限范围 |
| `expiresIn` | 签发时指定的有效期（秒） |
| `expireTime` | 到期时间 |
| `createdAt` | 签发时间 |
| `expiresInSeconds` | 剩余有效期（秒），**≤0 即已过期** |

### 10.2 OAuth 侧查询（access_token，仅限直连客户端）

第三方应用后端可用**自己持有的 `access_token`** 查询本应用为该用户签发的自定义令牌，无需用户登录态：

```http
GET /oauth2/custom-tokens
Authorization: Bearer {ACCESS_TOKEN}
```

也可用 `UC-Token: {ACCESS_TOKEN}`。

- 认证：由 `OAuthAuthInterceptor` 完成，使用标准 `access_token`（`refresh_token` 提交会被判为无效令牌）。
- 准入：服务层强制校验该应用**已启用、已由管理员审批且已开通直连认证能力**（`direct_auth_enabled=1`）。未开通返回 `90014`，未审批返回 `90013`，客户端无效返回 `90001`。该能力只授予管理员显式开通的机密客户端，公共客户端与普通第三方应用无法调用，避免凭普通 access_token 收割用户的自定义令牌明文。
- 过滤：`uid` 与 `client_id` 均取自该 access_token 记录，**只返回该应用名下的记录**，不会读到该用户在其它应用的自定义令牌明文。
- 响应结构与字段与第 10.1 节的用户侧接口完全一致（含 `clientId`、`clientName` 等），其余约定（排序、过期标注）相同。
- 安全约束同第 8 节：返回的 `token` 为 `access_token` 明文，调用方必须服务端保存，严禁下发到浏览器。
- 残留风险：本接口不做 scope 收敛校验。若某次签发的 access_token 使用 `/oauth2/token/from-refresh` 收窄过 scope，它仍能读到本应用名下更宽 scope 的自定义令牌明文。直连客户端属于受信后端，故按此设计；如需彻底堵住，须额外按「只返回 scope ⊆ 调用方 scope 的记录」过滤。
- 吊销：作废其中一条令牌请用第 12.2 节的 `POST /oauth2/custom-tokens/revoke`。

## 11. 查询可用授权范围（公开）

直签请求的 `scope` 参数只能从平台支持的标识中选择（见第 6 节）。为避免调用方自行拼接非法标识，提供一个**无需登录**的公开查询接口：

```http
GET /open/oauth/scopes
```

- **无需任何凭证**：公开接口，不校验登录态，也不要求 OAuth `access_token`。
- **数据来源**：`OAuthScope` 枚举中可自助配置的范围，与《用户授权权限管理接口文档》的 `selectableScopes` 口径一致（每项为 `{code, desc}`）。
- **不含元范围 `all`**：`all` 仅限管理员手工写库授予，不会出现在本接口。
- **无用户维度数据**：只返回静态枚举元数据，不含任何用户授权信息；某应用“已授予哪些权限”属用户维度，需登录后走 `GET /user/oauth/grants/scopes`，本接口不提供。

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    { "code": "user.read", "desc": "读取你的用户 ID、昵称和头像" },
    { "code": "user.profile", "desc": "读取并修改你的个人资料" },
    { "code": "config.read", "desc": "读取该应用名下的用户配置，以及查看你的排班表" },
    { "code": "config.write", "desc": "创建、修改或删除该应用名下的用户配置，以及保存或删除你的排班表" },
    { "code": "gama-data.read", "desc": "读取用户游戏数据" },
    { "code": "gama-data.write", "desc": "写入用户游戏数据" }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 权限标识，即直签请求 `scope` 参数的合法取值 |
| `desc` | string | 权限中文描述 |

当前可选范围（6 项）：

| 标识 | 说明 |
| --- | --- |
| `user.read` | 读取你的用户 ID、昵称和头像 |
| `user.profile` | 读取并修改你的个人资料 |
| `config.read` | 读取该应用名下的用户配置，以及查看你的排班表 |
| `config.write` | 创建、修改或删除该应用名下的用户配置，以及保存或删除你的排班表 |
| `gama-data.read` | 读取用户游戏数据 |
| `gama-data.write` | 写入用户游戏数据 |

**注意**：本接口返回的是“系统全部可选权限”**全集**，仅用于展示可选项；某次直签实际能携带的权限仍受第 6 节收敛规则约束——不得超过凭证自身已有范围，否则返回 `90003`。

## 12. 吊销自定义令牌

第 10 节的两个查询入口各自对应一个**吊销**入口，凭证与第 10 节一一对应：用户侧用登录态，OAuth 侧用 `access_token`。二者都完成「**令牌立即失效 + 删除签发记录**」两步，语义一致，差异仅在**归属口径**与**准入**。

### 12.1 用户侧吊销（登录态）

用户在「我的自定义令牌」列表中主动作废自己名下的一条令牌：

```http
POST /user/oauth/custom-tokens/revoke
Authorization: Bearer {UC_SESSION_TOKEN}
Content-Type: application/json

{ "token": "{ACCESS_TOKEN}" }
```

- 认证：走用户登录态（`/user/**` 由 `UserAuthInterceptor` 保护），从会话取 `uid`；未登录返回 `80001`。
- 归属：**只按 `uid` 限定，可跨应用**。用户是自己所有令牌的归属者，因此无需指明 `client_id`，一条请求即可吊销名下任意应用签发的令牌。
- 准入：**无直连认证能力要求**。这是用户自助操作，非第三方应用身份，不校验 `direct_auth_enabled`。
- 请求体：`token` 为签发时返回的 `access_token` **明文**，必填，长度 ≤128。

### 12.2 OAuth 侧吊销（access_token，仅限直连客户端）

第三方应用后端用**自己持有的 `access_token`** 吊销本应用为该用户签发的一条令牌，无需用户登录态：

```http
POST /oauth2/custom-tokens/revoke
Authorization: Bearer {ACCESS_TOKEN}
Content-Type: application/json

{ "token": "{ACCESS_TOKEN_TO_REVOKE}" }
```

也可用 `UC-Token: {ACCESS_TOKEN}` 提交认证凭证。

- 认证：由 `OAuthAuthInterceptor` 完成，使用标准 `access_token`（`refresh_token` 提交会被判为无效令牌）。
- 归属：**`uid` + `client_id` 双重限定**，只能吊销本应用为该用户签发的令牌。凭 token 字符串无法跨用户、跨应用越权吊销。
- 准入：同第 10.2 节，服务层强制校验该应用**已启用、已审批且已开通直连认证能力**（`direct_auth_enabled=1`）。未开通返回 `90014`，未审批返回 `90013`，客户端无效返回 `90001`。

### 12.3 共同行为

- **吊销令牌**：从 Redis 删除该 `access_token`（`uc:oauth:access:*`），令牌**立即失效**；同时摘除用户令牌反向索引 `uc:uid:oauth:{uid}` 中的对应成员。
- **删除记录**：从 `oauth_custom_token` 表删除该签发记录，使其从第 10 节列表中消失。
- **执行顺序**：先删 Redis（吊销即时生效），再删 DB 记录，与按应用整体撤销授权（`POST /user/oauth/grants/revoke`）一致。
- **幂等**：令牌不存在、已过期（Redis 无 key）或记录不存在时，均返回成功，不报错——避免被用来探测令牌有效性。
- **归属校验前置**：删 Redis 前先读取记录比对归属，归属不符则不触碰 Redis；DB 删除同样带归属条件，未命中即幂等成功。

### 12.4 请求与响应

请求体：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `token` | 是 | 待吊销的 `access_token` 明文，长度 ≤128；两侧接口字段一致 |

`Content-Type: application/json`。

成功响应（空结果）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

### 12.5 与「按应用整体撤销授权」的区别

| 维度 | 本接口（吊销单条令牌） | `POST /user/oauth/grants/revoke` |
| --- | --- | --- |
| 粒度 | **一条** `access_token` | 某应用下**全部**令牌 + 授权记录 |
| 依据 | `token` 明文定位 | `client_id` |
| 台账影响 | **删除**该条 `oauth_custom_token` 记录 | 置该应用台账为已吊销 |
| 语义 | 作废一条自签令牌 | 撤销我对该应用的授权 |

### 12.6 调用示例

用户侧吊销：

```http
POST {UC_BASE_URL}/user/oauth/custom-tokens/revoke
Authorization: Bearer {UC_SESSION_TOKEN}
Content-Type: application/json

{ "token": "access_token_value" }
```

OAuth 侧吊销：

```http
POST {UC_BASE_URL}/oauth2/custom-tokens/revoke
Authorization: Bearer {ACCESS_TOKEN}
Content-Type: application/json

{ "token": "access_token_value_to_revoke" }
```

### 12.7 错误码

除第 7 节通用错误码外：

| HTTP | code | 含义 | 处理建议 |
| --- | --- | --- | --- |
| 200 | `10001` | `token` 为空或超过 128 字符 | 修正请求体 |
| 200 | `80001` | 未登录或登录已失效（用户侧） | 引导用户重新登录后重试 |
| 200 | `90001` | 客户端不存在或已停用（OAuth 侧） | 检查凭证，停止调用并联系管理员 |
| 200 | `90013` | 客户端待管理员审批或已被封禁（OAuth 侧） | 联系管理员完成审批或解除封禁 |
| 200 | `90014` | 客户端未开通直连认证能力（OAuth 侧） | 联系管理员开通该能力 |

**安全提示**：请求体中的 `token` 为明文凭证，调用方必须**服务端保存与传输**，严禁下发到浏览器或写入日志（同第 8 节第 5 条）。
