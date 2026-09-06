# 加密客户端 OAuth2 接入文档

## 1. 接入信息

加密客户端使用 Authorization Code + PKCE S256 接入 UserCenter，并通过 `client_secret_post` 完成客户端认证。适用于能够安全保存密钥的后端服务或 BFF，不适用于纯前端、桌面应用等无法保密的程序。

接入前请向管理员取得：

- UserCenter 服务地址：`{UC_BASE_URL}`
- 加密客户端 ID：`{CLIENT_ID}`
- 客户端密钥：`{CLIENT_SECRET}`
- 已登记的回调地址：`{REDIRECT_URI}`
- 已批准的权限范围，例如 `user.read,user.email`

客户端必须满足：

- `authMethod=client_secret_post`
- `ownerEnabled=true`
- `adminApproved=true`
- `grantTypes` 包含 `authorization_code`
- 使用 refresh token 时，`grantTypes` 还必须包含 `refresh_token`

`client_secret` 只能保存在可信后端或密钥管理系统中，不能写入浏览器代码、移动端包、桌面客户端、公开仓库、URL 或日志。当前不支持 `client_secret_basic`，客户端密钥必须通过 HTTPS 表单请求体提交。

`redirect_uri` 必须与登记值完全一致，包括协议、域名、端口、路径和查询参数。

## 2. 调用顺序

```text
1. 业务后端生成 state、code_verifier 和 code_challenge
2. 业务后端把 state 和 code_verifier 保存到服务端会话，再让浏览器跳转 GET /oauth2/authorize
3. UserCenter 完成登录和授权确认
4. UserCenter 携带 code、state 将浏览器跳回业务后端的回调地址
5. 业务后端校验并一次性消费 state
6. 业务后端携带 client_secret 和 code_verifier 调用 POST /oauth2/token
7. 业务后端使用 access_token 调用 GET /oauth2/userinfo，并建立自己的登录会话
8. access_token 到期后，业务后端按需使用 refresh_token 刷新
9. 用户退出登录时，业务后端调用 POST /oauth2/revoke 吊销令牌
```

UserCenter 登录页、登录票据和授权确认页由 UserCenter 自己处理，接入方不需要调用 `/oauth2/ticket`、`/oauth2/consent/info` 或 `/oauth2/consent`。

浏览器只参与页面跳转和携带回调参数。令牌交换、用户信息查询、令牌刷新和吊销均应由业务后端完成，业务后端再向浏览器签发自己的会话凭证。

## 3. 生成 PKCE 参数

每次授权都必须重新生成：

| 参数 | 说明 |
| --- | --- |
| `state` | 随机字符串，用于防止 CSRF，并将回调绑定到发起授权的浏览器会话 |
| `code_verifier` | 43 至 128 个字符的高强度随机字符串 |
| `code_challenge` | `BASE64URL(SHA256(code_verifier))`，不带 `=` padding |
| `code_challenge_method` | 固定为 `S256` |

Node.js 后端示例：

```js
import { createHash, randomBytes } from "node:crypto";

function createAuthorizationState() {
  const state = randomBytes(32).toString("base64url");
  const codeVerifier = randomBytes(64).toString("base64url");
  const codeChallenge = createHash("sha256")
    .update(codeVerifier, "ascii")
    .digest("base64url");

  return { state, codeVerifier, codeChallenge };
}

const authorization = createAuthorizationState();

// 保存到服务端会话或短时存储，并绑定当前浏览器会话；回调成功后一次性删除。
await authorizationStore.save(authorization.state, {
  codeVerifier: authorization.codeVerifier,
  redirectUri: process.env.OAUTH_REDIRECT_URI
}, 300);
```

`state` 和 `code_verifier` 应保存在服务端短时存储中，并设置较短有效期。回调处理时必须先验证 `state` 属于当前浏览器会话，再原子地将其消费，防止重放。

## 4. 发起授权

业务后端让浏览器导航到：

```http
GET {UC_BASE_URL}/oauth2/authorize
    ?response_type=code
    &client_id={CLIENT_ID}
    &redirect_uri={URL_ENCODED_REDIRECT_URI}
    &scope=user.read,user.email
    &state={STATE}
    &code_challenge={CODE_CHALLENGE}
    &code_challenge_method=S256
```

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `response_type` | 是 | 固定为 `code` |
| `client_id` | 是 | 加密客户端 ID |
| `redirect_uri` | 是 | 已登记的精确回调地址 |
| `scope` | 否 | 逗号分隔，必须是客户端已批准范围的子集；不传时使用全部已批准范围 |
| `state` | 是 | 业务后端生成并绑定到当前浏览器会话的随机字符串 |
| `code_challenge` | 是 | PKCE challenge |
| `code_challenge_method` | 是 | 固定为 `S256` |

授权请求不能携带 `client_secret`。用户未登录时，UserCenter 会先跳转登录页；需要确认授权时，会再跳转授权确认页。接入方只需保持浏览器导航流程。

授权成功后跳回业务后端：

```http
{REDIRECT_URI}?code={AUTHORIZATION_CODE}&state={STATE}
```

用户拒绝授权时跳回：

```http
{REDIRECT_URI}?error=access_denied&state={STATE}
```

业务后端必须先确认返回的 `state` 与当前会话中保存的值完全一致，再处理 `code` 或 `error`。校验失败时立即终止流程，不得兑换授权码。

## 5. 授权码换取令牌

业务后端通过服务端请求调用：

```http
POST {UC_BASE_URL}/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&client_id={CLIENT_ID}
&client_secret={CLIENT_SECRET}
&code={AUTHORIZATION_CODE}
&redirect_uri={URL_ENCODED_REDIRECT_URI}
&code_verifier={CODE_VERIFIER}
```

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `grant_type` | 是 | 固定为 `authorization_code` |
| `client_id` | 是 | 加密客户端 ID |
| `client_secret` | 是 | 加密客户端密钥，通过表单请求体提交 |
| `code` | 是 | 回调得到的一次性授权码 |
| `redirect_uri` | 是 | 必须与发起授权时的值完全一致 |
| `code_verifier` | 是 | 与 `code_challenge` 对应的原始随机串 |

不要使用 HTTP Basic Authentication 传递客户端密钥，当前服务端只支持 `client_secret_post`。

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "access_token": "access_token_value",
    "token_type": "Bearer",
    "expires_in": 7200,
    "refresh_token": "refresh_token_value",
    "scope": "user.read,user.email"
  }
}
```

只有客户端登记了 `refresh_token` grant 时才会签发 refresh token；否则响应体不包含 `refresh_token` 字段（`access_token`、`token_type`、`expires_in` 照常返回）。

授权码只能成功兑换一次。业务后端应立即删除本次授权对应的 `state` 和 `code_verifier`，并且不得在日志、监控或错误上报中记录 `client_secret`、授权码、`code_verifier` 或令牌。

## 6. 获取用户信息

业务后端使用 access token 调用：

```http
GET {UC_BASE_URL}/oauth2/userinfo
Authorization: Bearer {ACCESS_TOKEN}
```

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "uid": 10001,
    "email": "user@example.com",
    "userName": "orange-user",
    "nickname": "Orange",
    "avatar": "https://example.com/avatar.png"
  }
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `uid` | UserCenter 全局用户 ID，接入方应使用它关联本地用户 |
| `email` | 仅授权范围包含 `user.email` 时返回，否则为 `null` 或省略 |
| `userName` | 用户名，可能为 `null` |
| `nickname` | 用户昵称，可能为 `null` |
| `avatar` | 头像地址，可能为 `null` |

业务后端完成用户关联后，应向浏览器签发自己的安全会话 Cookie。除非业务协议明确需要，否则不要把 UserCenter 的 access token 或 refresh token 返回给浏览器。

## 7. 刷新令牌

仅当客户端登记了 `refresh_token` grant 且换码响应返回了 refresh token 时调用。

```http
POST {UC_BASE_URL}/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&client_id={CLIENT_ID}
&client_secret={CLIENT_SECRET}
&refresh_token={REFRESH_TOKEN}
```

成功响应格式与授权码换取令牌相同，仅返回新的 access token（refresh_token 未变、scope 未变，均不返回）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "access_token": "access_token_value_2",
    "token_type": "Bearer",
    "expires_in": 7200
  }
}
```

refresh token 为固定凭证：

- 默认 90 天有效（从签发起算），有效期内可反复刷新，服务端不删除、不换发。
- 每次刷新只签发新的 access token；本地无需更新 refresh token。
- 刷新返回 `90009` 时表示 refresh token 已过期或被吊销，清除本地令牌并重新发起授权。

每次刷新都必须提交当前有效的 `client_secret`。客户端密钥轮换后，应立即让所有后端实例使用新密钥；旧密钥不能继续刷新令牌。

## 8. 吊销令牌

用户退出登录或不再使用令牌时，由业务后端调用：

```http
POST {UC_BASE_URL}/oauth2/revoke
Content-Type: application/x-www-form-urlencoded

client_id={CLIENT_ID}
&client_secret={CLIENT_SECRET}
&token={TOKEN}
```

`token` 可以是 access token 或 refresh token。吊销 refresh token 时，它派生的 access token（同一客户端名下）会被级联吊销。

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

接口是幂等的。令牌不存在、已经失效或不属于当前客户端时，也会返回成功。即使如此，加密客户端仍必须提交正确的 `client_secret` 才能调用吊销接口。

## 9. 统一错误响应

除授权跳转外，接口使用统一响应结构。调用方必须检查响应体中的 `code`，不能只判断 HTTP 状态码。

```json
{
  "code": 90007,
  "msg": "客户端密钥校验失败",
  "data": null
}
```

常见错误码：

| code | 含义 | 接入方处理 |
| --- | --- | --- |
| `90001` | 客户端不存在或所有者已停用 | 停止登录并联系管理员 |
| `90002` | 回调地址不匹配 | 检查 `redirect_uri` 是否与登记值完全一致 |
| `90003` | scope 未授权 | 只申请已批准的 scope |
| `90004` | 授权码无效或过期 | 重新发起授权 |
| `90005` | 授权码已使用 | 重新发起授权，不要重试旧 code |
| `90006` | grant type 未登记 | 检查客户端授权类型配置 |
| `90007` | 客户端密钥校验失败 | 检查后端使用的密钥是否缺失、错误或已经轮换 |
| `90008` | PKCE 校验失败 | 检查本次请求保存的 `code_verifier` |
| `90009` | 令牌无效或已过期（含 refresh token 已被吊销） | 清除令牌并重新授权 |
| `90013` | 客户端待审批或已被管理员封禁 | 联系管理员完成审批或解除封禁 |

## 10. 接入检查

- `client_secret` 只存在于可信后端或密钥管理系统中。
- 生产环境的 OAuth 接口和回调地址均使用 HTTPS。
- 每次授权都生成新的 `state` 和 PKCE 参数，并绑定当前浏览器会话。
- 回调后先校验并一次性消费 `state`，再兑换授权码。
- `redirect_uri` 在授权和换码请求中完全一致。
- 换码、刷新和吊销均由业务后端完成，并使用 `client_secret_post`。
- 表单请求使用 `application/x-www-form-urlencoded`，不使用 `client_secret_basic`。
- UserCenter 令牌保存在后端，浏览器使用业务系统自己的安全会话。
- API 成功与否按响应体 `code` 判断。
- 刷新成功后更新本地 access token；refresh token 固定复用，无需替换。
- 日志、URL、监控和错误上报不记录密钥、授权码、PKCE verifier 或令牌。
- 客户端密钥轮换后，所有后端实例同步切换到新密钥。
