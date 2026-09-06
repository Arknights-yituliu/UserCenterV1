# OAuth2 客户端管理接口文档

## 1. 文档说明

本文档描述 UserCenter OAuth2 客户端自助管理接口，供客户端管理页面和第三方开发者后台接入使用。

- 接口前缀：`/user/oauth/client`
- 请求格式：注册和更新接口使用 `application/json`；其他接口无请求体
- 响应格式：统一为 `{code,msg,data}`
- 身份认证：使用 UserCenter 登录会话，不使用 OAuth access token
- 权限边界：当前用户只能查询和操作自己名下的客户端

所有管理接口都必须携带以下任一请求头：

```http
Authorization: Bearer <UC_SESSION_TOKEN>
```

或：

```http
UC-Token: <UC_SESSION_TOKEN>
```

推荐统一使用 `Authorization: Bearer`。

## 2. 统一响应

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

失败响应：

```json
{
  "code": 10002,
  "msg": "客户端名称不能为空",
  "data": null
}
```

调用方必须以响应体中的 `code` 判断业务是否成功，不能只判断 HTTP 状态码。

## 3. 客户端类型

### 3.1 公共客户端

适用于无后端 Web 应用、桌面应用等不能安全保存密钥的程序。

```json
{
  "authMethod": "none"
}
```

行为：

- 不生成、不保存、不返回 `client_secret`。
- 注册响应中的 `clientSecret` 明确为 `null`。
- 使用 Authorization Code + PKCE S256。
- 换码、刷新和吊销时只提交 `client_id`，不能依赖客户端密钥。
- 不支持密钥轮换。

### 3.2 加密客户端

适用于后端服务或 BFF 等能够安全保存密钥的程序。

```json
{
  "authMethod": "client_secret_post"
}
```

行为：

- 注册时生成 `client_secret`，明文仅在注册响应中返回一次。
- 服务端只保存 BCrypt 哈希，之后无法查询原始密钥。
- 换码、刷新和吊销时通过表单参数提交 `client_id` 和 `client_secret`。
- 可通过密钥轮换接口生成新密钥，旧密钥立即失效。

当前不支持 `client_secret_basic`。

## 4. 字段约束

### 4.1 注册字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `clientName` | string | 是 | 客户端名称，最长 128 个字符 |
| `redirectUris` | string[] | 是 | 回调地址白名单，1 至 10 个 |
| `scopes` | string[] | 是 | 允许申请的权限范围，不能为空 |
| `grantTypes` | string[] | 是 | 授权类型，必须包含 `authorization_code` |
| `authMethod` | string | 是 | `none` 或 `client_secret_post` |
| `websiteOrigin` | string | 否 | 无后端 Web 应用或网站的 Origin，最长 255 个字符 |
| `accessTokenTtl` | number | 否 | access token 有效期，单位秒，最小 60 |
| `refreshTokenTtl` | number | 否 | refresh token 有效期，单位秒，最小 300 |

`grantTypes` 只允许以下值：

- `authorization_code`
- `refresh_token`

未登记 `refresh_token` 时，授权码兑换成功后不会签发 refresh token，也不能调用刷新流程。

### 4.2 地址规则

`redirectUris` 规则：

- 必须是绝对 URI。
- 生产环境必须使用 HTTPS。
- 本地开发允许 HTTP，但主机只能是 `localhost`、`127.0.0.1` 或 `::1`。
- 禁止 user-info 和 fragment。
- 授权时按完整字符串精确匹配，包括协议、主机、端口、路径和查询参数。
- 单项不能包含英文逗号，不能带首尾空格。

有效示例：

```text
https://spa.example.com/oauth/callback
http://localhost:5173/oauth/callback
http://127.0.0.1:5173/oauth/callback
http://[::1]:5173/oauth/callback
```

无效示例：

```text
http://spa.example.com/oauth/callback
http://localhost.example.com/oauth/callback
https://user@spa.example.com/oauth/callback
https://spa.example.com/oauth/callback#fragment
```

`websiteOrigin` 只能包含协议、主机和可选端口，例如：

```text
https://spa.example.com
http://localhost:5173
```

不能包含路径、查询参数、fragment、user-info 或末尾 `/`。

注意：`websiteOrigin` 只是客户端登记信息，不会自动加入服务端 CORS 白名单。上线前仍需由服务端管理员把准确 Origin 加入 `user-center.oauth.allowed-origins`。

## 5. 注册客户端

```http
POST /user/oauth/client/register
Authorization: Bearer <UC_SESSION_TOKEN>
Content-Type: application/json
```

### 5.1 注册无后端 Web 应用的公共客户端

请求：

```json
{
  "clientName": "Example Web App",
  "redirectUris": [
    "https://spa.example.com/oauth/callback",
    "http://localhost:5173/oauth/callback"
  ],
  "scopes": ["user.read", "user.email"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "authMethod": "none",
  "websiteOrigin": "https://spa.example.com",
  "accessTokenTtl": 7200,
  "refreshTokenTtl": 7776000
}
```

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "clientId": "cl_0123456789abcdef01234567",
    "clientSecret": null,
    "authMethod": "none",
    "clientName": "Example Web App",
    "ownerEnabled": true,
    "adminApproved": false,
    "directAuthEnabled": false
  }
}
```

### 5.2 注册加密客户端

请求：

```json
{
  "clientName": "Example Backend",
  "redirectUris": ["https://backend.example.com/oauth/callback"],
  "scopes": ["user.read", "user.email"],
  "grantTypes": ["authorization_code", "refresh_token"],
  "authMethod": "client_secret_post",
  "websiteOrigin": "https://backend.example.com"
}
```

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "clientId": "cl_0123456789abcdef01234567",
    "clientSecret": "sk_0123456789abcdef0123456789abcdef",
    "authMethod": "client_secret_post",
    "clientName": "Example Backend",
    "ownerEnabled": true,
    "adminApproved": false,
    "directAuthEnabled": false
  }
}
```

`clientSecret` 只显示一次。客户端管理页面应在成功后明确提示开发者立即保存，离开页面后不能再次查看。

注册成功后系统固定启用以下安全策略：

- `requirePkce=true`
- `requireAuthConsent=true`
- `ownerEnabled=true`
- `adminApproved=false`
- `directAuthEnabled=false`

新客户端创建后默认处于待管理员审批状态，不能发起授权、兑换授权码、刷新令牌或使用旧系统直连认证。管理员审批通过后 `adminApproved` 变为 `true`，客户端才可使用 OAuth2；直连登录和直连注册还需要管理员单独把 `directAuthEnabled` 开启。客户端自助管理接口不能修改这两个管理员字段。

客户端实际可用条件为：

```text
ownerEnabled && adminApproved
```

直连登录和直连注册的可用条件为：

```text
ownerEnabled && adminApproved && directAuthEnabled
```

单个开发者账号可创建的客户端数量受服务端配置限制，超过上限返回 `90012`。

## 6. 查询客户端列表

```http
GET /user/oauth/client/list
Authorization: Bearer <UC_SESSION_TOKEN>
```

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "clientId": "cl_0123456789abcdef01234567",
      "clientName": "Example Web App",
      "authMethod": "none",
      "grantTypes": ["authorization_code", "refresh_token"],
      "redirectUris": ["https://spa.example.com/oauth/callback"],
      "scopes": ["user.read", "user.email"],
      "requirePkce": true,
      "requireAuthConsent": true,
      "websiteOrigin": "https://spa.example.com",
      "ownerEnabled": true,
      "adminApproved": false,
      "directAuthEnabled": false,
      "createTime": "2026-09-04 12:00:00"
    }
  ]
}
```

列表按创建时间倒序返回，不包含 `clientSecret`。

## 7. 查询客户端详情

```http
GET /user/oauth/client/{clientId}
Authorization: Bearer <UC_SESSION_TOKEN>
```

示例：

```http
GET /user/oauth/client/cl_0123456789abcdef01234567
```

`data` 字段结构与列表中的单个对象相同，不包含 `clientSecret`。

查询不属于当前用户的客户端时返回 `80008`；客户端不存在时返回 `90001`。

## 8. 更新客户端

```http
POST /user/oauth/client/{clientId}/update
Authorization: Bearer <UC_SESSION_TOKEN>
Content-Type: application/json
```

请求：

```json
{
  "clientName": "Example Web App Production",
  "redirectUris": [
    "https://spa.example.com/oauth/callback",
    "http://localhost:5173/oauth/callback"
  ],
  "scopes": ["user.read", "user.email"],
  "websiteOrigin": "https://spa.example.com",
  "accessTokenTtl": 7200,
  "refreshTokenTtl": 7776000
}
```

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

创建后不能修改：

- `clientId`
- `authMethod`
- `grantTypes`
- `requirePkce`
- `requireAuthConsent`
- `adminApproved`
- `directAuthEnabled`

更新接口采用完整覆盖语义，`clientName`、`redirectUris` 和 `scopes` 必须全部提交，不能只提交单个变更字段。

## 9. 轮换客户端密钥

仅适用于 `authMethod=client_secret_post` 的加密客户端。

```http
POST /user/oauth/client/{clientId}/rotate-secret
Authorization: Bearer <UC_SESSION_TOKEN>
```

响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "clientId": "cl_0123456789abcdef01234567",
    "clientSecret": "sk_new_secret_value",
    "authMethod": "client_secret_post",
    "clientName": "Example Backend",
    "ownerEnabled": true,
    "adminApproved": true,
    "directAuthEnabled": true
  }
}
```

轮换成功后旧密钥立即失效，新密钥明文仅返回一次。公共客户端调用此接口返回：

```json
{
  "code": 10003,
  "msg": "公共客户端没有可轮换的密钥",
  "data": null
}
```

## 10. 停用客户端

```http
POST /user/oauth/client/{clientId}/disable
Authorization: Bearer <UC_SESSION_TOKEN>
```

停用后，新的授权、授权码兑换和 refresh token 刷新将被拒绝。成功响应的 `data` 为 `null`。

停用操作不会删除客户端，可稍后重新启用。

## 11. 启用客户端

```http
POST /user/oauth/client/{clientId}/enable
Authorization: Bearer <UC_SESSION_TOKEN>
```

成功响应的 `data` 为 `null`。如果客户端仍在等待管理员审批或已被管理员封禁，用户不能自行启用，返回 `90013`。管理员审批只会令 `adminApproved` 变为 `true`；如果所有者曾主动停用客户端，审批后仍需所有者调用本接口启用。

## 12. 删除客户端

```http
POST /user/oauth/client/{clientId}/delete
Authorization: Bearer <UC_SESSION_TOKEN>
```

删除行为：

- 级联吊销该客户端名下全部 access token 和 refresh token。
- 物理删除客户端记录。
- 操作不可恢复。

客户端管理页面应在删除前进行明确的二次确认，并展示客户端名称和 `clientId`，避免误删。

## 13. 管理员审批

当前仓库没有管理员身份和权限管理模块，因此客户端自助接口不提供审批或直连认证开通操作。两项操作必须由可信管理后台或受控数据库运维流程执行，不能把修改 `adminApproved`、`directAuthEnabled` 的能力开放给普通用户。

管理 API 和数据库都使用正向语义。API 字段为 `ownerEnabled`、`adminApproved`、`directAuthEnabled`，对应数据库列为 `owner_enabled`、`admin_approved`、`direct_auth_enabled`。

已有数据库在部署新版应用前，必须于维护窗口执行 [OAuth 客户端状态字段迁移脚本](../src/main/resources/db/oauth_client_state_columns_migration.sql)。迁移会保留原所有者启停状态，并把原反向的管理员封禁值转换为正向审批值。

还必须执行 [OAuth 客户端直连认证字段迁移脚本](../src/main/resources/db/oauth_client_direct_auth_migration.sql)。该脚本默认关闭所有现有客户端的直连认证能力；部署前应核实现有直连接入方并建立可信客户端白名单。

管理员核对客户端名称、所有者、回调地址、Origin、权限范围和用途后，可执行等价于以下状态变更的操作：

```sql
UPDATE oauth_client
SET admin_approved = 1
WHERE id = '<审核通过的 client_id>'
  AND admin_approved = 0;
```

审批通过后，如果 `ownerEnabled=true`，客户端立即可用；如果所有者已经主动停用，仍需所有者重新启用。审批拒绝时保持 `adminApproved=false`。

管理员为已审批的加密客户端开通直连登录和直连注册：

```sql
UPDATE oauth_client
SET direct_auth_enabled = 1
WHERE id = '<审核通过的 client_id>'
  AND admin_approved = 1
  AND auth_methods = 'client_secret_post';
```

关闭时把 `direct_auth_enabled` 改回 `0`。服务端会在发起会话、提交登录或注册信息、兑换 ticket 时重新校验，所以关闭后尚未使用的 channel 和 ticket 也不能继续完成认证。管理员审批不会自动开通直连认证。

## 14. 常见错误码

| code | 含义 | 常见场景 |
| --- | --- | --- |
| `200` | 操作成功 | 请求已完成 |
| `10001` | 参数错误 | 未知 `authMethod`、未知 grant type、非法 URI |
| `10002` | 参数校验失败 | 必填字段为空、长度或 TTL 不符合要求 |
| `10003` | 非法操作 | 公共客户端尝试轮换密钥 |
| `40001` | 系统繁忙 | 未处理的服务端异常 |
| `80001` | 未登录或登录已失效 | 缺少或使用无效 UC 会话 token |
| `80008` | 无权限操作 | 操作了其他用户的客户端 |
| `90001` | 客户端不存在或已停用 | clientId 不存在，或 OAuth 流程使用了停用客户端 |
| `90012` | 客户端数量已达上限 | 当前账号无法继续注册客户端 |
| `90013` | 客户端待管理员审批或已被封禁 | 未审批客户端发起 OAuth 请求，或尝试自行解除管理员控制 |
| `90014` | 客户端未开通直连认证能力 | 未列入可信白名单的客户端调用直连登录或直连注册 |

错误消息可能包含更具体的参数说明，前端可展示 `msg`，但业务分支应以 `code` 为准。

## 15. 客户端管理页面建议

客户端列表建议至少展示：

- 客户端名称和 `clientId`
- 客户端类型：公共客户端或加密客户端
- 状态：待审批、启用、停用或管理员封禁
- 直连认证能力：已开通或未开通（只读）
- 回调地址和网站 Origin
- 授权类型与 scopes
- 创建时间

操作规则：

- 公共客户端隐藏“轮换密钥”操作。
- 加密客户端注册或轮换后，仅在结果页显示一次密钥并提示立即保存。
- 待审批或管理员封禁时禁用“启用”操作，并展示管理员审批提示。
- 删除操作必须二次确认。
- 保存回调地址时保持原始完整字符串，不能擅自移除端口、路径或查询参数。
- `websiteOrigin` 发生变化时，提醒管理员同步检查服务端 CORS 白名单。

## 16. 无后端 Web 应用创建后的接入要求

注册公共客户端后，无后端 Web 应用还需要满足以下条件才能完成 OAuth2 登录：

1. 每次发起授权前生成随机 `state` 和 PKCE `code_verifier`。
2. 使用 SHA-256 计算 `code_challenge`，并提交 `code_challenge_method=S256`。
3. 回调后先校验 `state`，再使用 `client_id`、授权码、精确的 `redirect_uri` 和 `code_verifier` 换取令牌。
4. 浏览器请求中不得包含或持久化 `client_secret`。
5. 实际页面 Origin 必须已经加入服务端静态 CORS 白名单。

OAuth2 授权、换码、刷新、吊销和 userinfo 接口不属于本文档范围，参见 [无后端 Web 应用 OAuth2 公共客户端设计](./无后端Web应用OAuth2公共客户端设计.md)和[无后端 Web 应用 OAuth2 接入文档](./无后端Web应用OAuth2接入文档.md)。
