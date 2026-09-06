# OAuth2 与标准协议差距清单

> 基于当前代码实现（OAuthController、OAuthTokenServiceImpl、OAuthTokenVO 等）对照 RFC 6749 / 6750 / 7636 / 7009 / 7662 的逐项评估。
> 用途：记录与标准 OAuth2 的偏差，为后续协议合规改造与接入方说明提供依据。

## 1. 参照标准

| 标准              | 主题                                            |
| --------------- | --------------------------------------------- |
| RFC 6749        | OAuth 2.0 授权框架（authorize / token 端点、授权码模式）    |
| RFC 6750        | Bearer Token 使用（资源请求头、401 + WWW-Authenticate） |
| RFC 7636        | PKCE（S256）                                    |
| RFC 7009        | Token 吊销                                      |
| RFC 7662        | Token 自省（introspection）                       |
| RFC 9700 / 6819 | OAuth 相关安全最佳实践（refresh token 轮换等）             |

## 2. 结论速览

| 等级 | 差距项                          | 互操作影响                  | 建议                             |
| -- | ---------------------------- | ---------------------- | ------------------------------ |
| 高  | 令牌/错误响应包裹业务 envelope         | 标准 OAuth 客户端库无法解析      | 协议端点输出标准结构                     |
| 高  | 错误全部 HTTP 200 + `{code,msg}` | 标准客户端无法识别 4xx/error 分支 | 协议端点按 RFC 返回状态码与 `{error,...}` |
| 高  | scope 使用逗号分隔                 | 空格分隔的标准调用方会被拒          | 改空格或双格式兼容                      |
| 中  | 资源端验证依赖共享 Redis              | 授权/资源服务分离部署时失效         | 视架构决定上 introspection           |
| 中  | 缺少 introspection 端点          | 第三方资源方无法自省             | 同上                             |
| 低  | refresh token 固定复用不轮换        | 合规但非最佳实践               | 记录为取舍，暂不强制                     |
| 低  | 吊销 refresh 不级联派生 access            | 已收敛为明确语义（方向 B），无行为漏洞 | 记录为取舍（见 3.3）  |
| 说明 | userinfo / 登录会话为自定义扩展        | 属 OIDC 范畴，非 OAuth2 差距  | 接入方需标准 OIDC 时再评估               |

## 3. 详细差距

### 3.1 协议面（影响标准客户端互操作）

#### 3.1.1 令牌响应外层包裹业务 envelope

- **现状**：`/oauth2/token`、`/oauth2/revoke`、`/oauth2/userinfo` 返回 `{code, msg, data:{...}}`，令牌字段嵌套在 `data` 内；`data` 内字段名符合标准（`access_token`/`token_type`/`expires_in`/`refresh_token`/`scope`）。
- **标准**：RFC 6749 §5.1 要求 token 端点响应顶层即为 `access_token`、`token_type`、`expires_in` 等字段。
- **影响**：依赖标准库（AppAuth、openid-client 等）的第三方客户端解析不到令牌，只能手写 HTTP。
- **优先级**：高。

#### 3.1.2 错误模型：HTTP 200 + 业务码

- **现状**：业务错误统一抛 `BusinessException`，由 GlobalExceptionHandler 转换为 HTTP 200 + `{code,msg,data:null}`（含 80001 未登录、90009 令牌无效、90004 授权码无效等）。仅配置冲突（409）、参数校验（400）、BadRequest（400）例外。
- **标准**：
  - token 端点：HTTP 400/401 + JSON `{error, error_description, ...}`（RFC 6749 §5.2），如 `invalid_grant`、`invalid_client`、`unsupported_grant_type`；
  - 资源端点：HTTP 401 + `WWW-Authenticate: Bearer realm="...", error="invalid_token"`（RFC 6750 §3）。
- **影响**：标准客户端无法用 `error` 字段做分支；网关与监控链路看不到 4xx 语义。
- **优先级**：高。

#### 3.1.3 scope 使用英文逗号分隔

- **现状**：客户端 scopes 以逗号存储；`normalizeScope` 用 `split(",")`/`String.join(",")` 归一化、校验、回传；token 记录与响应均保留逗号串。
- **标准**：scope 是空格分隔字符串（`scope-token *( SP scope-token )`）。
- **影响**：按标准以空格传多个 scope 的调用方会被视为一个整体标识而校验失败。
- **优先级**：高（改造需服务端 + 接入文档 + 存量数据同步）。

#### 3.1.4 资源服务器验证方式：共享 Redis，无 introspection

- **现状**：`resolveAccessToken` 直接读 `uc:oauth:access:{token}` key 判断有效性，`OAuthAuthInterceptor` 依赖该结果。令牌为不透明随机串，非 JWT。
- **标准**：RFC 7662 定义 introspection 端点；JWT 方案则用本地验签。标准并未强制资源方必须走 introspection。
- **影响**：授权服务器与资源服务器必须共享同一 Redis（或同一套部署）；第三方资源方无法独立验证令牌。
- **优先级**：中（取决于是否有资源服务分离部署诉求）。

### 3.2 语义细节（符合规范但需注意）

| 项                                      | 现状                                          | 评估                                                       |
| -------------------------------------- | ------------------------------------------- | -------------------------------------------------------- |
| refresh token 不轮换                      | 刷新仅签发新 access，refresh 固定复用（固定凭证模型）          | RFC 6749 §6 允许；RFC 9700/6819 建议轮换。属已确认的取舍，文档与代码注释需保持一致口径 |
| 刷新响应省略 refresh\_token/scope            | 不换发时 refresh\_token 返回 null 省略；scope 未变省略   | 合规（§5.1 允许省略）                                            |
| client 认证仅 client\_secret\_post / none | 无 client\_secret\_basic                     | 合规：AS 至少支持一种标准方法即可                                       |
| redirect\_uri 可省略                      | `@RequestParam(required=false)`，省略时依赖服务端白名单 | 单回调客户端合规；多回调客户端**必须**传 redirect\_uri 区分，需确认省略时的服务端兜底策略   |
| 无 token\_type\_hint                    | revoke 自动探测令牌类型                             | RFC 7009 hint 为可选，合规                                     |
| state 原样回传                             | 服务端不校验，由客户端校验                               | 合规                                                       |
| 仅授权码 + 强制 PKCE                         | requirePkce 固定 S256                         | 超出标准的加固，非差距                                              |

### 3.3 已收敛的取舍项（曾为内部不一致，现已明确为方向 B）

- **吊销 refresh token 不级联派生 access**：refresh 记录不保存与派生 access 的父子关联，`revokeToken` 吊销 refresh 时只删除 refresh 本身；此前派生的 access_token 按各自 TTL 自然过期，反向索引死成员由「概率性惰性清理」收敛。代码注释、接口/Controller javadoc 与接入文档已同步为一致口径。
  - 影响说明：吊销 refresh 后，仍在有效期内、早前换出的 access_token 暂不失效，最长存活至其过期；
  - 如需"吊销即全面中断"，标准做法是引导用户在用户中心「撤回对该应用的授权」（`revokeClientAuthorization`，按 uid+client 全量清理，见 docs/用户授权应用管理接口文档.md）。

### 3.4 OAuth2 之外的扩展说明（非差距）

- **登录会话模型**：使用 UC 会话 token / uc\_ticket 而非浏览器 Cookie 会话重定向，属纯 API 化的产品改造，协议结果（code 回跳）与标准等价。
- **userinfo 自定义结构**：无 OIDC `sub`、`id_token`、Discovery/JWKS。仅当接入方要求标准 OIDC 时才构成差距。
- **授权确认 API 化**：consent 采用「服务端返回回跳 URL，前端执行跳转」的接口化模型。

## 4. 已确认的合规/加分项（避免改造时误伤）

- response\_type 仅支持 `code`（更安全，RFC 推荐）。
- 授权码一次性消费，重放被拒。
- PKCE S256 对公共客户端强制。
- client\_secret 仅存 BCrypt 哈希，明文只回传一次。
- revoke 对外幂等成功，不泄露令牌有效性（防探测）。
- 授权失败在无法安全回跳时直接报错而非跳转（防开放重定向）。
- 令牌随机串、无固定格式可枚举。
- 吊销、过期后 Lua 原子校验与 Redis TTL 双保险。

## 5. 改造建议与顺序

> 均为破坏性变更，需与现有接入方（见 docs 下接入文档）同步评估，建议仅对 `/oauth2/**` 协议端点启用标准行为，`/user/**` 用户侧接口保持 `200 + envelope` 不变。

1. **协议端点响应去 envelope**：`/oauth2/token`、`/oauth2/revoke`、`/oauth2/userinfo` 直接输出标准结构（token 顶层字段、revoke 空 200、userinfo 顶层对象）。
2. **错误模型对齐 RFC**：token/资源端点按场景返回 400/401，body 为 `{error, error_description}`；资源端 401 附加 `WWW-Authenticate`。内部业务码保留在日志与审计，对外以 `error` 为准。
3. **scope 分隔符对齐**：改为空格（或入参兼容空格与逗号两种、出参统一空格），同步接入文档。
4. **redirect\_uri 策略**：注册多回调时强制必传；单回调客户端可省略，文档明确。
5. ~~refresh 记录关联派生 access~~（已按 3.3 方向 B 收敛：吊销 refresh 不级联，语义已在代码与接入文档中同步一致，不再列入改造项）。
6. **introspection 端点**：仅在授权/资源分离部署诉求出现时实施（RFC 7662）。

## 6. 附：互操作自测建议

- 使用任意标准 OAuth 库（如 Go `golang.org/x/oauth2`、Node `openid-client`、Python `requests-oauthlib`）走完整授权码 + PKCE + 刷新流程，验证能否直接解析。
- 用 curl 检查 token 端点错误场景的 HTTP 状态码与 body 结构是否符合 §5.2。

