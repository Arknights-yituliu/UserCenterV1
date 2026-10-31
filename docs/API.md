# UserCenter 项目 API 文档

> 鉴权方式说明：
>
> - **用户会话**：登录后返回的会话 token，请求头携带 `Authorization: Bearer {token}` 或 `UC-Token: {token}`，由 `UserAuthInterceptor` 校验 Redis 会话（`/user/**`、`/auth/logout`）。
> - **OAuth access_token**：OAuth 授权签发的访问令牌，请求头携带 `Authorization: Bearer {access_token}`（或 `UC-Token`），由 `OAuthAuthInterceptor` 校验，部分接口额外要求 scope 或直连认证开关。
> - **协议级认证**：OAuth 协议端点自带认证（client_id + client_secret、授权码、refresh_token、PKCE、一次性票据等），在服务层校验，不走拦截器。
> - **签名认证**：Ed25519 验签 + 时间窗 + nonce 防重放（仅内部迁移端点）。

## 一、公开接口（无需鉴权）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/` | GET | 否 | - | 根路径健康检查，返回后端启动成功提示，用于部署后验证服务可用。 |
| `/auth/send-code` | POST | 否 | - | 发送邮箱验证码（注册/换绑/重置密码等用途），按 IP 与邮箱限流防滥用。 |
| `/auth/register` | POST | 否 | - | 注册（邮箱+用户名+密码+昵称+验证码），成功后自动登录并返回会话 token。来源客户端经 `UC-Client-Id` 头标识。 |
| `/auth/login` | POST | 否 | - | 登录（密码或邮箱验证码两种方式），成功后返回会话 token，前端后续以 Authorization 或 UC-Token 头携带。 |
| `/auth/reset-code` | POST | 否 | - | 发送重设密码验证码，发到账号绑定邮箱，无需登录，按 IP 限流。 |
| `/auth/reset-password` | POST | 否 | - | 通过邮箱验证码重设密码，无需登录，成功后踢出该账号全部会话。 |
| `/open/oauth/scopes` | GET | 否 | - | 公开查询系统全部可选 OAuth 权限（code+中文描述），不含元范围 all，无用户数据。 |
| `/open/schedule/{id}` | GET | 否 | - | 按排班表 ID 公开查询完整排班表内容，无需登录，供外部只读访问。 |
| `/open/ak-operator-statistics/result` | GET | 否 | - | 公开查询干员养成数据统计结果，读取离线跑批结果表（30 分钟刷新一轮），不触发全表扫描。 |

## 二、用户会话鉴权接口（`Authorization: Bearer {token}` 或 `UC-Token`）

### 认证相关

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/auth/logout` | POST | 是 | 用户会话 | 登出，删除 Redis 会话使当前 token 立即失效，由 UserAuthInterceptor 统一校验登录态。 |

### 用户资料与设备管理（`/user/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/profile` | GET | 是 | 用户会话 | 获取当前登录用户的资料（uid、用户名、昵称、头像、邮箱等）。 |
| `/user/profile/update` | POST | 是 | 用户会话 | 修改个人资料（昵称/头像）。 |
| `/user/password` | POST | 是 | 用户会话 | 修改密码，需验证旧密码，成功后按策略处理会话。 |
| `/user/email/bind` | POST | 是 | 用户会话 | 为无邮箱账号绑定新邮箱，需验证新邮箱所有权（验证码）。 |
| `/user/email/send-change-code` | POST | 是 | 用户会话 | 发送换绑邮箱验证码，发到当前绑定邮箱，按 IP 限流，前端无需传邮箱。 |
| `/user/email/change` | POST | 是 | 用户会话 | 换绑邮箱，需同时验证旧邮箱与新邮箱的验证码。 |
| `/user/sessions` | GET | 是 | 用户会话 | 查看当前用户的登录设备列表（各会话的设备、IP、时间等）。 |
| `/user/sessions/{token}/kick` | POST | 是 | 用户会话 | 踢指定设备下线，删除对应 Redis 会话，实时生效；仅能操作自己名下会话。 |

### 用户配置（`/user/config/**`，client_id 取自登录会话）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/config/save` | POST | 是 | 用户会话 | 保存用户配置：id 为空创建，非空按 id 覆盖更新（无并发控制）。返回配置 id 与内容 hash。 |
| `/user/config/save-if-match` | POST | 是 | 用户会话 | 条件更新配置（save-if-match）：hash 不匹配返回 409 与最新 hash，防止并发覆盖。 |
| `/user/config/quota` | GET | 是 | 用户会话 | 查询当前用户全局配置的配额使用情况（字节数）。 |
| `/user/config/list` | GET | 是 | 用户会话 | 按分类查询当前客户端下的用户配置，可按版本、名称过滤，client_id 取自登录上下文。 |
| `/user/config/delete` | POST | 是 | 用户会话 | 按 id 物理删除用户配置，仅限本人名下。 |

### 用户排班表（`/user/schedule/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/schedule/save` | POST | 是 | 用户会话 | 保存排班表（创建或覆盖更新），归属当前登录用户。 |
| `/user/schedule/list` | GET | 是 | 用户会话 | 按用户名查询自己的排班表列表，仅能查本人名下排班。 |
| `/user/schedule/delete` | POST | 是 | 用户会话 | 删除自己的排班表，按 id 且校验归属。 |

### 游戏账号与干员数据（`/user/ak-accounts/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/ak-accounts` | GET | 是 | 用户会话 | 查询可用的游戏数据来源列表（森空岛账号 + 手动上传，含来源标识，不含干员正文）。 |
| `/user/ak-accounts/operators` | GET | 是 | 用户会话 | 全量读取干员数据：`akUid` 必填，`source` 可选（`manual` 手动版 / `skland` 森空岛自动导入）；不传 `source` 时按 `akUid` 是否等于当前用户中心 UID 自动识别手动条目。响应 Cache-Control 为 private, no-cache。 |
| `/user/ak-accounts/operators/save` | POST | 是 | 用户会话 | 批量保存角色信息与干员数据：有则比较更新，无则新增，未传入的不处理，返回增/改/未变统计。 |
| `/user/ak-accounts/manual-operators/save` | POST | 是 | 用户会话 | 保存本人手动修改版干员数据：有则比较更新，无则新增，未传入的不处理。 |

### 终末地玩家数据（`/user/ef-accounts/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/ef-accounts/player-data/save` | POST | 是 | 用户会话 | 保存终末地玩家数据：整份 JSON 文本直接落库，已有则覆盖，首次上传同时建立角色绑定。 |

### 第三方应用授权管理（`/user/oauth/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/oauth/grants` | GET | 是 | 用户会话 | 查看我授权过的第三方应用列表，按应用分组，组内为逐次授权记录。 |
| `/user/oauth/grants/revoke` | POST | 是 | 用户会话 | 撤销我对某第三方应用的整体授权：删除该应用全部令牌并置台账为已吊销。 |
| `/user/oauth/grants/scopes` | GET | 是 | 用户会话 | 查看我对某应用的授权范围（已授予权限+可选权限），供前端渲染勾选状态。 |
| `/user/oauth/grants/scopes/grant` | POST | 是 | 用户会话 | 为已授权应用追加权限（只增不减），同步到已签发令牌，立即生效。 |
| `/user/oauth/grants/scopes/revoke` | POST | 是 | 用户会话 | 取消对某应用的已授权权限（只减不增，剩余不可为空），同步到已签发令牌。 |
| `/user/oauth/custom-tokens` | GET | 是 | 用户会话 | 查看我签发的自定义有效期 access_token 列表（含已过期记录），按签发时间倒序。 |
| `/user/oauth/custom-tokens` | POST | 是 | 用户会话 | 用当前登录会话直签自定义有效期 access_token，可选 scope 收敛（只能减权不能扩权），需填名称。 |
| `/user/oauth/custom-tokens/revoke` | POST | 是 | 用户会话 | 吊销我名下的一条自定义令牌：Redis 立即失效并删除记录，幂等，可跨应用吊销自己的令牌。 |

### OAuth 客户端自助管理（`/user/oauth/client/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/oauth/client/scopes` | GET | 是 | 用户会话 | 查询平台可自助配置的 OAuth 授权范围，供客户端注册/编辑页选择，不含元范围 all。 |
| `/user/oauth/client/register` | POST | 是 | 用户会话 | 注册 OAuth 客户端（默认待管理员审批），公共客户端无 secret，机密客户端明文 secret 仅返回一次。 |
| `/user/oauth/client/list` | GET | 是 | 用户会话 | 查询我的客户端列表（不返回 secret），按 owner_uid 隔离。 |
| `/user/oauth/client/{clientId}` | GET | 是 | 用户会话 | 查询客户端详情（不返回 secret），仅限自己名下的客户端。 |
| `/user/oauth/client/{clientId}/update` | POST | 是 | 用户会话 | 更新客户端信息，client_id/authMethod/grantTypes 不可修改。 |
| `/user/oauth/client/{clientId}/rotate-secret` | POST | 是 | 用户会话 | 轮换客户端密钥：新 secret 明文仅此一次返回，旧值立即失效。 |
| `/user/oauth/client/{clientId}/disable` | POST | 是 | 用户会话 | 停用客户端，停用后授权/换 token 均返回 90001 错误。 |
| `/user/oauth/client/{clientId}/enable` | POST | 是 | 用户会话 | 启用已停用的客户端。 |
| `/user/oauth/client/{clientId}/delete` | POST | 是 | 用户会话 | 删除客户端（不可恢复），级联吊销其名下全部令牌。 |

## 三、OAuth 协议端点（协议级认证，服务层校验）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/authorize` | GET | 是 | 用户会话或 uc_ticket | 授权码签发：第三方网站引导浏览器跳转至此，校验登录会话或跨站登录票据后 302 跳回回调地址，支持 PKCE。 |
| `/oauth2/authorize` | POST | 是 | 用户会话或 uc_ticket | 授权码签发 POST 变体：跨站登录页可用表单 POST 携带 uc_ticket，避免票据出现在 URL/日志中，参数与 GET 一致。 |
| `/oauth2/ticket` | POST | 是 | 用户会话 | 签发跨站登录票据：携带本系统会话 token 调用，换取短时一次性凭证，供 authorize 跳转时使用（替代跨站 Cookie）。 |
| `/oauth2/consent/info` | GET | 是 | 用户会话 | 授权确认信息查询：确认页加载时调用，校验登录态与确认单归属，返回客户端名称、申请权限、回调地址。 |
| `/oauth2/consent` | POST | 是 | 用户会话 | 授权确认/拒绝：同意则签发授权码并返回回跳地址，拒绝则回跳 error=access_denied，确认单一次性消费。 |
| `/oauth2/token` | POST | 是 | client_id+client_secret / 授权码 / refresh_token / PKCE | OAuth 令牌交换与刷新：支持 authorization_code 和 refresh_token 两种 grant_type，机密客户端需校验 secret。 |
| `/oauth2/token/from-refresh` | POST | 是 | refresh_token | 用已有 refresh_token 直签指定有效期的 access_token，权限可收敛（只减不增），不消费不轮转刷新令牌，需填名称。 |
| `/oauth2/revoke` | POST | 是 | client_id+client_secret | 吊销令牌（RFC 7009）：自动识别 access_token/refresh_token 并立即失效，幂等，不泄露令牌是否有效。 |

## 四、OAuth access_token 鉴权接口（`Authorization: Bearer {access_token}`）

### 用户信息

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/userinfo` | GET | 是 | OAuth access_token + `user.read` scope | 获取授权用户信息（uid、用户名、昵称、头像），供无自有账户体系的接入方直接以 UC 用户登录。 |

### 用户配置（`/oauth2/config/**`，client_id 取自 OAuth token）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/config/save` | POST | 是 | OAuth access_token | 保存用户配置（OAuth 版），client_id 由服务端令牌签发不可伪造，只能读写自己站点名下配置。 |
| `/oauth2/config/save-if-match` | POST | 是 | OAuth access_token | 条件更新配置（save-if-match），hash 不匹配返回 409 与最新 hash，防并发覆盖。 |
| `/oauth2/config/quota` | GET | 是 | OAuth access_token | 查询当前 OAuth 用户的配置配额使用情况（字节数）。 |
| `/oauth2/config/list` | GET | 是 | OAuth access_token | 按分类查询令牌对应客户端名下的用户配置，可按版本、名称过滤。 |
| `/oauth2/config/delete` | POST | 是 | OAuth access_token | 按 id 物理删除用户配置，归属按令牌用户校验。 |

### 排班表（`/oauth2/schedule/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/schedule/list` | GET | 是 | OAuth access_token + `config.read` scope | 查询当前令牌用户自己的排班表列表，按更新时间倒序，用户取自 access_token。 |
| `/oauth2/schedule/save` | POST | 是 | OAuth access_token + `config.write` scope | 保存排班表（创建/覆盖更新），用户取自 access_token，请求参数不可指定。 |
| `/oauth2/schedule/delete` | POST | 是 | OAuth access_token + `config.write` scope | 删除自己的排班表，归属按 access_token 中的用户校验。 |

### 游戏账号与干员数据（`/oauth2/ak-accounts/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/ak-accounts` | GET | 是 | OAuth access_token + `gama-data.read` scope | 查询当前 OAuth 用户可用的数据来源列表（森空岛账号 + 手动上传，含来源标识）。 |
| `/oauth2/ak-accounts/operators` | GET | 是 | OAuth access_token + `gama-data.read` scope | 全量读取干员数据：`akUid` 必填，`source` 可选（`manual` / `skland`），不传时按 `akUid` 是否等于当前令牌用户 UID 自动识别手动条目。响应仅允许私人缓存且每次回源校验。 |
| `/oauth2/ak-accounts/operators/save` | POST | 是 | OAuth access_token + `gama-data.write` scope | 批量保存角色信息与干员数据，目标账号取自请求体 playerInfo.akUid。 |
| `/oauth2/ak-accounts/manual-operators/save` | POST | 是 | OAuth access_token + `gama-data.write` scope | 保存当前令牌用户本人的手动修改版干员数据，有则比较更新，无则新增。 |

### 自定义令牌（仅限已开通直连认证的客户端）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/custom-tokens` | GET | 是 | OAuth access_token + 直连认证开关（direct_auth_enabled=1） | 查询本应用为该用户签发的自定义令牌列表（含过期记录），只能看自己应用名下记录。 |
| `/oauth2/custom-tokens/revoke` | POST | 是 | OAuth access_token + 直连认证开关（direct_auth_enabled=1） | 吊销本应用签发的一条自定义令牌，归属按 uid+clientId 双重限定，幂等。 |

## 五、旧系统直连登录对接端点

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/direct-session` | POST | 是 | client_id + client_secret | 直连登录-发起会话：旧系统后端以密钥换取短时会话凭证（channel），前端持凭证调直连接口，secret 不暴露给浏览器。 |
| `/oauth2/direct-login` | POST | 是 | channel 短时凭证 + 账号密码/邮箱验证码 | 直连登录-提交凭证：前端直接向 UC 提交登录凭证（不经旧系统后端），校验通过返回一次性登录票据。 |
| `/oauth2/direct-register` | POST | 是 | channel 短时凭证 + 注册信息（邮箱验证码） | 直连注册：注册信息由浏览器直接提交 UC 创建用户，返回一次性登录票据供旧系统后端兑换用户信息。 |
| `/oauth2/direct-user` | POST | 是 | client_id + client_secret + 一次性票据 | 直连登录-兑换用户信息：凭一次性票据兑换用户信息并签发 OAuth 令牌，校验票据归属与未消费，需开通直连认证。 |

## 六、内部迁移端点（跨公网，端点自带认证）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/internal/migrate-token` | POST | 是 | Ed25519 验签 + 时间窗 + nonce 防重放（ts/nonce/kid/sig） | 迁移兑换：BackEndV3 服务端凭旧自签 token 对应的 uid 换取一对全新 UC 令牌（access+refresh）。 |
