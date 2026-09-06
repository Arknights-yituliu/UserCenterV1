# 无后端 Web 应用 OAuth2 接入文档

## 1. 接入信息

无后端 Web 应用使用 Authorization Code + PKCE S256 接入 UserCenter，不使用 `client_secret`。

接入前请向管理员取得：

- UserCenter 服务地址：`{UC_BASE_URL}`
- 公共客户端 ID：`{CLIENT_ID}`
- 已登记的回调地址：`{REDIRECT_URI}`
- 已批准的权限范围，例如 `user.read,user.email`

客户端必须满足：

- `authMethod=none`
- `ownerEnabled=true`
- `adminApproved=true`
- 无后端 Web 应用的 Origin 已登记到 `oauth_client_origin` 并通过管理员审核

`redirect_uri` 必须与登记值完全一致，包括协议、域名、端口、路径和查询参数。

## 2. 调用顺序

```text
1. 无后端 Web 应用生成 state、code_verifier 和 code_challenge
2. 浏览器跳转 GET /oauth2/authorize
3. UserCenter 完成登录和授权确认
4. UserCenter 携带 code、state 跳回无后端 Web 应用
5. 无后端 Web 应用校验 state，调用 POST /oauth2/token 换取令牌
6. 无后端 Web 应用使用 access_token 调用 GET /oauth2/userinfo
7. access_token 到期后，按需使用 refresh_token 刷新
8. 退出登录时调用 POST /oauth2/revoke 吊销令牌
```

UserCenter 登录页、登录票据和授权确认页由 UserCenter 自己处理，接入方不需要调用 `/oauth2/ticket`、`/oauth2/consent/info` 或 `/oauth2/consent`。

## 3. 生成 PKCE 参数

每次授权都必须重新生成：

| 参数 | 说明 |
| --- | --- |
| `state` | 随机字符串，用于防止 CSRF |
| `code_verifier` | 43 至 128 个字符的高强度随机字符串 |
| `code_challenge` | `BASE64URL(SHA256(code_verifier))`，不带 `=` padding |
| `code_challenge_method` | 固定为 `S256` |

现代浏览器可以直接使用 Web Crypto API 生成参数：

```js
function toBase64Url(bytes) {
  const binary = String.fromCharCode(...bytes);
  return btoa(binary)
    .replaceAll("+", "-")
    .replaceAll("/", "_")
    .replace(/=+$/, "");
}

function randomBase64Url(byteLength) {
  const bytes = crypto.getRandomValues(new Uint8Array(byteLength));
  return toBase64Url(bytes);
}

async function createPkceParameters() {
  const state = randomBase64Url(32);
  const codeVerifier = randomBase64Url(64);
  const verifierBytes = new TextEncoder().encode(codeVerifier);
  const digest = await crypto.subtle.digest("SHA-256", verifierBytes);

  return {
    state,
    codeVerifier,
    codeChallenge: toBase64Url(new Uint8Array(digest)),
    codeChallengeMethod: "S256"
  };
}

const pkce = await createPkceParameters();

// 回调页需要读取 state 和 codeVerifier；换码完成后立即删除。
sessionStorage.setItem("uc.oauth.pkce", JSON.stringify({
  state: pkce.state,
  codeVerifier: pkce.codeVerifier
}));
```

发起授权时，分别将 `pkce.state`、`pkce.codeChallenge` 和
`pkce.codeChallengeMethod` 放入授权请求。回调后读取保存的
`pkce.codeVerifier`，作为 `/oauth2/token` 的 `code_verifier` 参数。

## 4. 发起授权

将浏览器导航到：

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
| `client_id` | 是 | 公共客户端 ID |
| `redirect_uri` | 是 | 已登记的精确回调地址 |
| `scope` | 否 | 逗号分隔，必须是客户端已批准范围的子集；不传时使用全部已批准范围 |
| `state` | 是 | 无后端 Web 应用生成的随机字符串 |
| `code_challenge` | 是 | PKCE challenge |
| `code_challenge_method` | 是 | 固定为 `S256` |

用户未登录时，UserCenter 会先跳转登录页；需要确认授权时，会再跳转授权确认页。接入方只需保持浏览器导航流程。

授权成功后跳回：

```http
{REDIRECT_URI}?code={AUTHORIZATION_CODE}&state={STATE}
```

用户拒绝授权时跳回：

```http
{REDIRECT_URI}?error=access_denied&state={STATE}
```

回调页必须先确认返回的 `state` 与发起授权时保存的值完全一致。校验失败时立即终止流程，不得兑换授权码。

## 5. 授权码换取令牌

```http
POST {UC_BASE_URL}/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&client_id={CLIENT_ID}
&code={AUTHORIZATION_CODE}
&redirect_uri={URL_ENCODED_REDIRECT_URI}
&code_verifier={CODE_VERIFIER}
```

参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `grant_type` | 是 | 固定为 `authorization_code` |
| `client_id` | 是 | 公共客户端 ID |
| `code` | 是 | 回调得到的一次性授权码 |
| `redirect_uri` | 是 | 必须与发起授权时的值完全一致 |
| `code_verifier` | 是 | 与 `code_challenge` 对应的原始随机串 |

公共客户端不得提交 `client_secret`。

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

授权码只能使用一次。换码成功或失败后，都不要在 URL、日志或错误上报中记录授权码和 `code_verifier`。

## 6. 获取用户信息

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

`access_token` 缺失、无效或已过期时，UserCenter 的 OAuth 拦截器返回错误码 `80001`（HTTP 状态仍为 200），不会返回 `90009`：

```json
{
  "code": 80001,
  "msg": "未登录或登录已失效",
  "data": null
}
```

收到 `80001` 时应清除本地 access_token / refresh_token，引导用户重新发起授权。

## 7. 刷新令牌

仅当客户端登记了 `refresh_token` grant 且换码响应返回了 refresh token 时调用。

```http
POST {UC_BASE_URL}/oauth2/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
&client_id={CLIENT_ID}
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

公共客户端刷新时同样不得提交 `client_secret`。

## 8. 吊销令牌

退出登录或不再使用令牌时调用：

```http
POST {UC_BASE_URL}/oauth2/revoke
Content-Type: application/x-www-form-urlencoded

client_id={CLIENT_ID}&token={TOKEN}
```

`token` 可以是 access token 或 refresh token。吊销 refresh token 只使其本身失效，此前派生的 access token 会按各自有效期自然过期（如需立即中断全部访问，请在用户中心撤回对该应用的授权）。

成功响应：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

接口是幂等的。令牌不存在、已经失效或不属于当前客户端时，也会返回成功。

## 9. 统一错误响应

除授权跳转外，接口使用统一响应结构。调用方必须检查响应体中的 `code`，不能只判断 HTTP 状态码。

```json
{
  "code": 90008,
  "msg": "PKCE 校验失败",
  "data": null
}
```

常见错误码：

| code | 含义 | 接入方处理 |
| --- | --- | --- |
| `10001` | 参数错误（例如 authorize 的 `response_type` 仅支持 `code`） | 按本文档参数表传参 |
| `40001` | 系统繁忙（请求缺少必填参数时的兜底返回；或客户端要求授权确认但服务端未配置 `consent-page-url`） | 先补全必填参数；仍复现时联系管理员检查 OAuth 服务端配置 |
| `80001` | 未登录或登录已失效（拦截器返回：未携带或无效/过期的 access_token 访问受保护资源接口；authorize 未配置登录页跳转时，未登录请求同样返回此码） | 清除令牌并重新发起授权 |
| `90001` | 客户端不存在或所有者已停用 | 停止登录并联系管理员 |
| `90002` | 回调地址不匹配 | 检查 `redirect_uri` 是否与登记值完全一致 |
| `90003` | scope 未授权 | 只申请已批准的 scope |
| `90004` | 授权码无效或过期 | 重新发起授权 |
| `90005` | 授权码已使用 | 重新发起授权，不要重试旧 code |
| `90006` | grant type 未登记 | 检查客户端授权类型配置 |
| `90008` | PKCE 校验失败 | 检查本次请求保存的 `code_verifier` |
| `90009` | 令牌无效或已过期（含 refresh token 已被吊销） | 清除令牌并重新授权 |
| `90013` | 客户端待审批或已被管理员封禁 | 联系管理员完成审批或解除封禁 |

## 10. 接入检查

- 无后端 Web 应用中不存在 `client_secret`。
- 每次授权都生成新的 `state` 和 PKCE 参数。
- 回调页在换码前校验 `state`。
- `redirect_uri` 在授权和换码请求中完全一致。
- 表单请求使用 `application/x-www-form-urlencoded`。
- API 成功与否按响应体 `code` 判断。
- 刷新成功后更新本地 access token；refresh token 固定复用，无需替换。
- 日志、URL、监控和错误上报不记录令牌或 `code_verifier`。
