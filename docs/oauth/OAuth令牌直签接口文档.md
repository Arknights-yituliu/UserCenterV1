# OAuth 令牌直签接口文档

## 1. 文档说明

本文档描述 UserCenter 的**令牌直签**接口：调用方直接提交一份「已有凭证」（用户登录会话 `uc_token` 或 OAuth `refresh_token`），换取指定有效期的 `access_token`。

```http
POST /oauth2/token/custom
POST /oauth2/token/from-credential
```

两个路径指向同一处理逻辑，等价，任选其一。

### 1.1 与标准协议的关系

本端点**不属于 RFC 6749 定义的任何 grant type**，是面向服务端间（如 BackEndV3 → UC）的非标准扩展：

| 维度 | 标准 `/oauth2/token` | 本端点 |
| --- | --- | --- |
| 支持的 grant | `authorization_code` / `refresh_token` | 无 `grant_type` 概念，按凭证类型分流 |
| 客户端认证 | `client_secret_post` | **不需要** `client_secret` |
| 认证依据 | 授权码 / 刷新令牌 + 客户端密钥 | 凭证本身（`uc_token` 或 `refresh_token`） |
| 换取目标 | 按客户端登记 TTL 签发 | **调用方指定** `expires_in` |
| 是否轮转 refresh | 轮转模式下会换发 | **不轮转**，凭证保持有效 |

因此本端点不面向标准 OAuth 客户端库，调用方需自行构造表单请求。协议兼容性说明见《OAuth2 与标准协议差距清单》第 3 节。

### 1.2 典型用途

- **统一令牌**：业务后端已持有 UC 登录会话（`uc_token`），为下游服务换发一个短时 OAuth `access_token`，避免下游直接依赖会话体系。
- **最小权限换取**：用宽范围的 `refresh_token` 换一个**只含必要权限**的短时令牌，缩小泄露面（见第 6 节）。

## 2. 通用约定

### 2.1 认证

端点**不注册任何拦截器**（`interceptor/WebConfig.java` 仅覆盖 `/user/**`、`/oauth2/userinfo`、`/oauth2/config/**` 等），HTTP 层不校验 Bearer 头，也**不需要 `client_secret`**。

认证完全由请求体中的凭证承担：

- 提交 `refresh_token` → 以该刷新令牌的有效性与归属为准；
- 提交 `uc_token` → 以该登录会话的有效性为准。

调用方必须通过 HTTPS 传输，凭证不得出现在 URL 查询串、日志或监控中。

### 2.2 请求格式

`Content-Type: application/x-www-form-urlencoded`。

参数仅接受**下划线命名**（与同域 `/oauth2/token`、`/oauth2/authorize` 等端点一致）：`uc_token`、`refresh_token`、`client_id`、`expires_in`、`scope`、`name`。

### 2.3 响应格式

统一为 `{code, msg, data}`；是否成功以响应体 `code` 判断，不能只看 HTTP 状态码。

## 3. 请求参数

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `uc_token` | 二选一 | UC 登录会话令牌 |
| `refresh_token` | 二选一 | OAuth 刷新令牌 |
| `client_id` | 条件必填 | 目标客户端 ID。`uc_token` 未携带来源客户端时必须传；提交 `refresh_token` 时可不传，传了则须与令牌归属一致 |
| `expires_in` | 是 | 期望的 `access_token` 有效期（秒），必须为大于 0 的整数 |
| `scope` | 否 | 期望收敛到的权限范围，英文逗号分隔。省略表示沿用凭证原有范围 |
| `name` | 是 | 本次签发的名称，用户用于在「我的自定义令牌」中区分用途；去空白后非空且不超过 50 字符 |

约束：

- `uc_token` 与 `refresh_token` **必须且只能提供一个**，同时提供或都不提供均报错。
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

## 5. 凭证分流语义

### 5.1 提交 `uc_token`

1. 在 Redis 中读取会话，取 `uid`；会话不存在或已过期返回 `80001`。
2. 确定目标客户端：优先取会话记录的来源客户端 `clientId`，为空时使用请求参数 `client_id`。
3. 授权范围取 **`resolveGrantScope(client, null, uid)`**，即用户对当前应用的有效授权——**用户自定义授权范围表（`oauth_user_scope`）在此生效**，用户在确认页的裁剪/追加会被延续。

### 5.2 提交 `refresh_token`

1. 读取刷新令牌记录，取 `uid`、`clientId`、`scope`、`familyId`；记录不存在返回 `90009`。
2. 若请求带了 `client_id`，必须与令牌归属的 `clientId` 完全一致，否则返回 `90009`（防止跨应用换发）。
3. 授权范围**直接沿用刷新令牌记录中的 `scope`**（不重新计算用户自定义范围）。

### 5.3 共同行为

- 新 `access_token` 写入 Redis，并登记到该用户的令牌反向索引 `uc:uid:oauth:{uid}`。
- 若刷新令牌带有 `familyId`（客户端开启了 `rotate_refresh_token`），新令牌**加入该令牌家族**：此后该家族被判定泄露或按应用整体撤回授权时，它会被一并作废。
- **不消费、不轮转凭证**：`refresh_token` 与 `uc_token` 在本调用后依然有效，可重复换取。
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

- 判定基准是**凭证自身已有范围**。`uc_token` 路径的基准即第 5.1 节的 `resolveGrantScope` 结果，因此同样受用户自定义授权范围约束。
- 凭证范围若为元范围 `all`，视为覆盖全部已知权限，任何系统已知的具体权限都属其子集。
- 请求集合中的每一项都必须是系统已知标识，未知字符串一律拒绝，防止任意值被写入令牌。

该约束使本端点**无法被用于提权**：即使凭证泄露，攻击者能拿到的权限也不会超过凭证本身。

## 7. 错误码

| HTTP | code | 含义 | 处理建议 |
| --- | --- | --- | --- |
| 200 | `10001` | 参数错误：`expires_in` 非正、凭证未二选一、`scope` 为空串、`name` 为空或超过 50 字符 | 修正请求参数 |
| 200 | `40001` | 系统繁忙（未处理异常） | 稍后重试并联系管理员 |
| 200 | `80001` | 未登录或登录已失效（`uc_token` 无效） | 引导用户重新登录后重试 |
| 200 | `90001` | 客户端不存在或已停用 | 检查 `client_id`，停止调用并联系管理员 |
| 200 | `90003` | 权限范围不合法（超范围或未知标识） | 改为凭证已有范围的子集 |
| 200 | `90009` | 令牌无效、已过期，或与 `client_id` 不匹配 | 清除本地凭证并重新授权 |
| 200 | `90013` | 客户端待管理员审批或已被封禁 | 联系管理员完成审批或解除封禁 |

## 8. 安全注意事项

1. **无 `client_secret` 保护**：端点仅凭凭证本身认证，等价于「持有刷新令牌即可换发访问令牌」。凭证必须以服务端密钥管理方式保存，严禁下发到浏览器或客户端应用。
2. **必须使用 HTTPS**，且应通过网关对来源 IP 做白名单约束（本端点本身不做来源校验）。
3. **`expires_in` 由调用方决定**，务必按最小必要原则设置；本端点不会替调用方收敛过长有效期。
4. **不要把 `scope` 当作提权手段**：它在设计上只能下降。若业务确实需要为用户增加授权，应走用户本人的授权流程（确认页 `POST /oauth2/consent` 或用户侧接口 `POST /user/oauth/grants/scopes/grant`），这两处有明确的用户意图确认。
5. **日志脱敏**：凭证、`access_token` 明文不得写入日志、监控或错误上报。
6. **`access_token` 明文落库**：按需求本端点签发的 `access_token` 会以明文写入 `oauth_custom_token` 表，供用户在「我的自定义令牌」中复制复用。该表因此成为敏感数据源：需限制数据库访问权限、纳入备份加密与审计；如需下线该能力，应把 `token` 列加密存储或改为仅存摘要（届时用户将无法回看令牌原文）。

## 9. 调用示例

### 9.1 用会话令牌换取（沿用范围）

```http
POST {UC_BASE_URL}/oauth2/token/custom
Content-Type: application/x-www-form-urlencoded

uc_token={UC_SESSION_TOKEN}
&client_id={CLIENT_ID}
&expires_in=600
&name=dashboard-readonly
```

### 9.2 用会话令牌换取（收敛到只读）

```http
POST {UC_BASE_URL}/oauth2/token/custom
Content-Type: application/x-www-form-urlencoded

uc_token={UC_SESSION_TOKEN}
&client_id={CLIENT_ID}
&expires_in=600
&scope=user.read
&name=dashboard-readonly
```

### 9.3 用刷新令牌换取短时令牌

```http
POST {UC_BASE_URL}/oauth2/token/from-credential
Content-Type: application/x-www-form-urlencoded

refresh_token={REFRESH_TOKEN}
&expires_in=300
&scope=config.read
&name=cron-config-sync
```

上述请求成功后返回新的 `access_token`，原 `refresh_token` 继续有效。

## 10. 查询我的自定义令牌

查看当前用户在**本端点**签发的全部自定义有效期令牌（**含已过期记录**），供「我的自定义令牌」页面展示。

```http
GET /user/oauth/custom-tokens
```

- 认证：走用户登录态（`/user/**` 由 `UserAuthInterceptor` 保护），从会话取 `uid`，**无需传参**；非本人数据不返回。
- 排序：按签发时间 `create_time` 倒序。
- 过期标注：每条记录返回剩余秒数 `expiresInSeconds`（`TIMESTAMPDIFF(SECOND, NOW(), expire_time)`），**≤0 即已过期**，前端据此展示状态；服务端不做过滤，也没有清理定时任务。

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
