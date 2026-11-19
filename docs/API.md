# UserCenter 项目 API 文档

> 名称兼容：示例统一使用 `TCL-Token`、`TCL-Client-Id`、`tcl_ticket`；对应入口仍接受旧名称 `UC-Token`、`UC-Client-Id`、`uc_ticket`。同一项新旧名称同时提供且非空值不一致时返回 `10001`；令牌解析仍优先使用 `Authorization: Bearer`。此说明不新增端点的认证要求，也不改变归档接口的历史状态。

> 鉴权方式说明：
>
> - **用户会话**：登录后返回的会话 token，请求头携带 `Authorization: Bearer {token}` 或 `TCL-Token: {token}`，由 `UserAuthInterceptor` 校验 Redis 会话（`/user/**`、`/auth/logout`）。
> - **OAuth access_token**：OAuth 授权签发的访问令牌，请求头携带 `Authorization: Bearer {access_token}`（或 `TCL-Token`），由 `OAuthAuthInterceptor` 校验，部分接口额外要求 scope 或直连认证开关。
> - **PAT（个人访问令牌）**：独立于上述两种凭证的第三类凭证，请求头携带 `Authorization: Bearer {PAT}` 或 `TCL-Token: {PAT}`，由 `PatAuthInterceptor` 按**令牌明文**查 `tlc_pat` 校验 `/pat/**`。两代令牌都认：带 `tlc_pat_` 前缀的 72 字符新令牌，以及迁移进来的无前缀 64 位 hex 旧令牌。`/oauth2/**` 链路不认 PAT，PAT 也不能当 access_token 用。
> - **协议级认证**：OAuth 协议端点自带认证（client_id + client_secret、授权码、refresh_token、PKCE、一次性票据等），在服务层校验，不走拦截器。
> - **签名认证**：Ed25519 验签 + 时间窗 + nonce 防重放（仅 `/oauth2/internal/migrate-token`；PAT 迁移端点无鉴权，靠内网隔离）。
> - **访问密钥**：请求参数 `key` 携带明文密钥，服务端只保存其 SHA-256 摘要并做常量时间比对，与后台控制台的校验方式一致；缺失或不匹配返回 `80008 无权限操作`。
>
> **URL 一级前缀即认证域**：`/user/**` 会话、`/oauth2/**` access_token、`/pat/**` PAT、`/open/**` 完全公开、`/internal/**` 不对外。PAT 的管理面（签发/列表/撤销）在 `/user/pat/**` 与 `/oauth2/pat/**`，用的是会话或 access_token；**唯一例外**是 `POST /oauth2/pat`，它凭请求体的 refresh_token 做协议级认证，已从 access_token 拦截器排除。只有消费面 `/pat/**` 才把 PAT 当请求凭证。详见 `docs/pat/PAT接口文档.md`。

## 一、公开接口（无需鉴权）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/` | GET | 否 | - | 根路径健康检查，返回后端启动成功提示，用于部署后验证服务可用。 |
| `/auth/send-code` | POST | 否 | - | 发送邮箱验证码（注册/换绑/重置密码等用途），按 IP 与邮箱限流防滥用。 |
| `/auth/register` | POST | 否 | - | 注册（邮箱+用户名+密码+昵称+验证码），成功后自动登录并返回会话 token。来源客户端经**必填**头 `TCL-Client-Id` 标识（缺失返回 `10001`，不创建账号）。 |
| `/auth/login` | POST | 否 | - | 登录（密码或邮箱验证码两种方式），成功后返回会话 token，前端后续以 Authorization 或 TCL-Token 头携带。须携带**必填**头 `TCL-Client-Id`（缺失或超 64 字符返回 `10001`）。 |
| `/auth/reset-code` | POST | 否 | - | 发送重设密码验证码，发到账号绑定邮箱，无需登录，按 IP 限流。 |
| `/auth/reset-password` | POST | 否 | - | 通过邮箱验证码重设密码，无需登录，成功后踢出该账号全部会话。 |
| `/open/oauth/scopes` | GET | 否 | - | 公开查询系统全部可选 OAuth 权限（code+中文描述），不含元范围 all，无用户数据。 |
| `/open/schedule/{id}` | GET | 否 | - | 按排班表 ID 公开查询完整排班表内容，无需登录，供外部只读访问。 |
| `/open/ak-operator-statistics/result` | GET | 否 | - | 公开查询干员养成数据统计结果，读取离线跑批结果表（每小时 05 分刷新一轮），不触发全表扫描。 |
| `/open/ak-operator-statistics/v2/result` | GET | 否 | - | 干员统计结果 V2：内容与 V1 一致，模组分布字段改用 `equipA`/`equipX`/`equipY`/`equipD`/`equipB`，与源表列名对齐。 |
| `/open/ak-skin-statistics/result` | GET | 是 | 访问密钥（`key` 参数） | 查询皮肤持有统计结果，一行一个皮肤（拥有账号数、参与统计账号总数），读取离线跑批结果表（每小时 10 分刷新一轮），不触发全表扫描。需携带 `?key=明文密钥`，与后台控制台共用同一把密钥。 |

## 二、用户会话鉴权接口（`Authorization: Bearer {token}` 或 `TCL-Token`）

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
| `/user/ak-accounts/operators/save` | POST | 是 | 用户会话 | 批量保存角色信息、干员数据与物品存量：有则比较更新，无则新增，未传入的不处理，返回干员增/改/未变统计。 |
| `/user/ak-accounts/manual-operators/save` | POST | 是 | 用户会话 | 保存本人手动修改版干员数据：有则比较更新，无则新增，未传入的不处理。 |

### 终末地玩家数据（`/user/ef-accounts/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/ef-accounts` | GET | 是 | 用户会话 | 查询已绑定的终末地角色列表（不含玩家数据正文），按最近导入时间倒序。 |
| `/user/ef-accounts/player-data` | GET | 是 | 用户会话 | 不传参数默认读取已绑定角色中最新导入的玩家数据；也可传 `roleId`、可选 `serverId` 指定角色，须已绑定到当前用户；无可用默认数据返回 `10002`；响应 Cache-Control 为 private, no-cache。 |
| `/user/ef-accounts/player-data/save` | POST | 是 | 用户会话 | 保存终末地玩家数据：整份 JSON 文本直接落库，已有则覆盖，首次上传同时建立角色绑定。 |

### 终末地寻访记录（`/user/ef-pool-records/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/ef-pool-records/create-task` | POST | 是 | 用户会话 | 创建寻访记录导入任务并记录归属用户（匿名接口 `/ef/pool-record/create-task` 已下线）。 |
| `/user/ef-pool-records/check-task` | GET | 是 | 用户会话 | 查询任务状态，仅任务创建者可见。 |
| `/user/ef-pool-records/accounts` | GET | 是 | 用户会话 | 查询当前用户导入过寻访记录的终末地账户列表，按导入时间倒序。 |
| `/user/ef-pool-records/record` | GET | 是 | 用户会话 | 按 `roleId` 读取我的干员与武器寻访记录，须已绑定当前用户；响应 Cache-Control 为 private, no-cache。 |

### 第三方应用授权管理（`/user/oauth/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/oauth/grants` | GET | 是 | 用户会话 | 查看我授权过的第三方应用列表，按应用分组，组内为逐次授权记录。 |
| `/user/oauth/grants/revoke` | POST | 是 | 用户会话 | 撤销我对某第三方应用的整体授权：删除该应用全部令牌并置台账为已吊销。 |
| `/user/oauth/grants/scopes` | GET | 是 | 用户会话 | 查看我对某应用的授权范围（已授予权限+可选权限），供前端渲染勾选状态。 |
| `/user/oauth/grants/scopes/grant` | POST | 是 | 用户会话 | 为已授权应用追加权限（只增不减），同步到已签发令牌，立即生效。 |
| `/user/oauth/grants/scopes/revoke` | POST | 是 | 用户会话 | 取消对某应用的已授权权限（只减不增，剩余不可为空），同步到已签发令牌。 |

### 个人访问令牌自助管理（`/user/pat/**`）

> PAT 是独立于会话与 OAuth access_token 的长期凭证，**全链路明文**：服务端保存令牌明文本身，列表接口可随时回看。以下接口本身用登录会话鉴权，PAT 不作为本组接口的凭证。

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/user/pat` | POST | 是 | 用户会话 | 签发一枚 PAT：`name`、`expires_in` 必填（有效期上限 10 年零 1 个月），`scope` 只能在可自助范围内收敛（不接受 `all`），`payload` 可选，`client_id` 只能留空或填保留标识 `this_land_cloud`（传其它值返回 `10001`）。返回令牌明文；`payload.game_uid` 决定新令牌在消费端读取哪个游戏账号，签发后不可修改。 |
| `/user/pat/list` | GET | 是 | 用户会话 | 查看我的 PAT 列表（含已过期记录），按签发时间倒序；返回完整 `token` 明文、`tokenPrefix`、`scope`、有效期、`payload` 与剩余秒数。响应含凭证，须走 TLS 且不得写入访问日志/前端埋点。 |
| `/user/pat/revoke` | POST | 是 | 用户会话 | 按 `id` 撤销一条 PAT（物理删行），幂等；传他人 id 返回成功但不删任何行。 |
| `/user/pat/revoke-all` | POST | 是 | 用户会话 | 作废本人名下全部 PAT。改密、删除应用等账号侧安全事件**不联动** PAT，需用户自助调用。 |

> 换令牌走「撤销 + 重新签发」，没有轮换接口；需要继承名称/有效期/payload 时由前端用列表项预填签发表单。

> **payload 是固定结构实体 `PatPayload`**，DB 仍存 JSON 文本、读取时才反序列化成对象。**所有字段均可为空**（可传 `{}`、只填部分，或完全不传），未知键静默忽略、缺失字段为 `null`，序列化后 ≤ 4KB。当前字段：`game_uid`（string，消费端据此定位游戏账号；接受 `gameUid` 别名）、`game_name`（string，游戏名称，可空、不参与鉴权；接受 `gameName` 别名）与 `source`（string，令牌来源标识，由签发方自填如 `web`/`mini_app`/`script`，可空、不校验、仅透传）。列表与签发响应只回传已定义字段，不再原样返回任意 KV；新增字段无需迁移存量数据。

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
| `/oauth2/authorize` | GET | 是 | 用户会话或 tcl_ticket | 授权码签发：第三方网站引导浏览器跳转至此，校验登录会话或跨站登录票据后 302 跳回回调地址，支持 PKCE。 |
| `/oauth2/authorize` | POST | 是 | 用户会话或 tcl_ticket | 授权码签发 POST 变体：跨站登录页可用表单 POST 携带 tcl_ticket，避免票据出现在 URL/日志中，参数与 GET 一致。 |
| `/oauth2/ticket` | POST | 是 | 用户会话 | 签发跨站登录票据：携带本系统会话 token 调用，换取短时一次性凭证，供 authorize 跳转时使用（替代跨站 Cookie）。 |
| `/oauth2/consent/info` | GET | 是 | 用户会话 | 授权确认信息查询：确认页加载时调用，校验登录态与确认单归属，返回客户端名称、申请权限、回调地址。 |
| `/oauth2/consent` | POST | 是 | 用户会话 | 授权确认/拒绝：同意则签发授权码并返回回跳地址，拒绝则回跳 error=access_denied，确认单一次性消费。 |
| `/oauth2/token` | POST | 是 | client_id+client_secret / 授权码 / refresh_token / PKCE | OAuth 令牌交换与刷新：支持 authorization_code 和 refresh_token 两种 grant_type，机密客户端需校验 secret。 |
| `/oauth2/revoke` | POST | 是 | client_id+client_secret | 吊销令牌（RFC 7009）：自动识别 access_token/refresh_token 并立即失效，幂等，不泄露令牌是否有效。 |

## 四、OAuth access_token 鉴权接口（`Authorization: Bearer {access_token}`）

### 用户信息

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/userinfo` | GET | 是 | OAuth access_token + `user.read` scope | 获取授权用户信息（uid、用户名、昵称、头像），供无自有账户体系的接入方直接以 TCL 用户登录。 |

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
| `/oauth2/ak-accounts` | GET | 是 | OAuth access_token + `arknights-data.read` scope | 查询当前 OAuth 用户可用的数据来源列表（森空岛账号 + 手动上传，含来源标识）。 |
| `/oauth2/ak-accounts/operators` | GET | 是 | OAuth access_token + `arknights-data.read` scope | 全量读取干员数据：`akUid` 必填，`source` 可选（`manual` / `skland`），不传时按 `akUid` 是否等于当前令牌用户 UID 自动识别手动条目。响应仅允许私人缓存且每次回源校验。 |
| `/oauth2/ak-accounts/operators/save` | POST | 是 | OAuth access_token + `arknights-data.write` scope | 批量保存角色信息、干员数据与物品存量，目标账号取自请求体 playerInfo.akUid。 |
| `/oauth2/ak-accounts/manual-operators/save` | POST | 是 | OAuth access_token + `arknights-data.write` scope | 保存当前令牌用户本人的手动修改版干员数据，有则比较更新，无则新增。 |

### 终末地玩家数据（`/oauth2/ef-accounts/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/ef-accounts` | GET | 是 | OAuth access_token + `endfield-data.read` scope | 查询当前令牌用户已绑定的终末地角色列表（不含玩家数据正文），按最近导入时间倒序。 |
| `/oauth2/ef-accounts/player-data` | GET | 是 | OAuth access_token + `endfield-data.read` scope | 不传参数默认读取已绑定角色中最新导入的玩家数据；也可传 `roleId`、可选 `serverId` 指定角色，须已绑定到令牌用户；无可用默认数据返回 `10002`；响应 Cache-Control 为 private, no-cache。 |
| `/oauth2/ef-accounts/player-data/save` | POST | 是 | OAuth access_token + `endfield-data.write` scope | 保存终末地玩家数据：整份 JSON 文本直接落库，已有则覆盖，首次上传同时建立角色绑定。 |

### 终末地寻访记录（`/oauth2/ef-pool-records/**`）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/ef-pool-records/create-task` | POST | 是 | OAuth access_token + `endfield-data.write` scope | 创建寻访记录导入任务，任务归属取令牌用户 uid，返回 taskId 供轮询。 |
| `/oauth2/ef-pool-records/check-task` | GET | 是 | OAuth access_token + `endfield-data.read` scope | 查询任务状态，仅任务创建者可见，完成时返回 uid / roleId / nickName。 |
| `/oauth2/ef-pool-records/accounts` | GET | 是 | OAuth access_token + `endfield-data.read` scope | 查询当前令牌用户导入过寻访记录的终末地账户列表，按导入时间倒序。 |
| `/oauth2/ef-pool-records/record` | GET | 是 | OAuth access_token + `endfield-data.read` scope | 按 `roleId` 读取干员与武器寻访记录，须已绑定当前令牌用户；响应 Cache-Control 为 private, no-cache。 |

### 个人访问令牌（应用视角，仅限已开通直连认证的客户端）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/pat` | POST | 是（refresh_token） | **无 access_token**；请求体 `refresh_token` 协议级校验 + 直连认证开关（direct_auth_enabled=1） | 直连应用为当前用户签发 PAT，参数校验与可选权限和 `/user/pat` 一致（`name`、`expires_in` 必填，`scope`、`payload` 可选）。`client_id` 取凭证所属应用、不接受调用方指定。`scope` 可选系统全部自助范围，不受 refresh_token 自身 scope 限制；不可签发 `all`。refresh_token 不消费、不轮转。凭证无效 `90009`，未开通直连 `90014`，非法 scope `90003`。 |
| `/oauth2/pat/list` | GET | 是 | OAuth access_token + 直连认证开关（direct_auth_enabled=1） | 查询当前用户的全部 PAT（含完整令牌明文及已过期记录），按 uid 隔离，包含用户自助及其它应用签发的令牌；clientId 仅用于准入校验，未开通直连认证返回 `90014`。 |
| `/oauth2/pat/revoke` | POST | 是 | OAuth access_token + 直连认证开关（direct_auth_enabled=1） | 按 `id` 撤销当前用户的一条 PAT（与 user 侧一致），也支持按 `token` 明文撤销；两者必须且只能提供一个。按 uid + id/token 精确匹配，跨应用、幂等，不能撤销其它用户的令牌。 |
| `/oauth2/pat/revoke-all` | POST | 是 | OAuth access_token + 直连认证开关（direct_auth_enabled=1） | 与 user 侧一致，撤销当前用户跨应用的全部 PAT，仅按 access_token 的 uid 删除，幂等。 |

## 五、旧系统直连登录对接端点

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/direct-session` | POST | 是 | client_id + client_secret | 直连登录-发起会话：旧系统后端以密钥换取短时会话凭证（channel），前端持凭证调直连接口，secret 不暴露给浏览器。 |
| `/oauth2/direct-login` | POST | 是 | channel 短时凭证 + 账号密码/邮箱验证码 | 直连登录-提交凭证：前端直接向 TCL 提交登录凭证（不经旧系统后端），校验通过返回一次性登录票据。 |
| `/oauth2/direct-register` | POST | 是 | channel 短时凭证 + 注册信息（邮箱验证码） | 直连注册：注册信息由浏览器直接提交 TCL 创建用户，返回一次性登录票据供旧系统后端兑换用户信息。 |
| `/oauth2/direct-user` | POST | 是 | client_id + client_secret + 一次性票据 | 直连登录-兑换用户信息：凭一次性票据兑换用户信息并签发 OAuth 令牌，校验票据归属与未消费，需开通直连认证。 |

## 六、内部迁移端点（不对外，仅内网访问）

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/oauth2/internal/migrate-token` | POST | 是 | Ed25519 验签 + 时间窗 + nonce 防重放（ts/nonce/kid/sig） | 迁移兑换：BackEndV3 服务端凭旧自签 token 对应的 uid 换取一对全新 TCL 令牌（access+refresh）。 |
| `/internal/pat-migration/run` | GET | 否 | 无鉴权；**不设开关、随应用启动注册**，必须限定内网访问 | 触发旧自定义令牌向 `tlc_pat` 的全量迁移（旧令牌保留原样明文写入）。**搬迁不判有效性**：`oauth_custom_token` 中所有未过期行都会写入，已被撤销（Redis 明细已删、签发记录仍在）的令牌会重新变成可用 PAT，该取舍已确认接受。每批 500 行批量 `INSERT IGNORE` 后，按 uid 摘除 `uc:uid:oauth:{uid}` 索引成员并批量 `DEL` 旧 key。幂等，返回 `total`/`migrated`/`skipped`（恒 0）/`failed`/`elapsedMs`，满足 `migrated + failed == total`。一次性运维端点：前置条件是新链路已能校验 PAT、旧表已备份、迁移期间停写；**跑完立即删除该 Controller 并重新发布**。服务层方法已加锁串行，重复触发会排队执行，已入库明文被唯一键忽略，不会翻倍。 |

## 七、PAT 鉴权接口（`Authorization: Bearer {PAT}` 或 `TCL-Token`）

> 本组是 PAT 的**消费面**：PAT 在此作为请求凭证，由 `PatAuthInterceptor` 独立校验，不经过 `OAuthAuthInterceptor`。凭证无效、格式不符或已过期统一返回 `80001`；scope 不足或账号不归属返回 `80008`；payload 非法返回 `10002`。

| API 路径 | 方法 | 是否鉴权 | 鉴权方式 | 描述 |
|---|---|---|---|---|
| `/pat/ak-accounts/operators` | GET | 是 | PAT + `arknights-data.read` scope（`gama-data.read` 或 `all` 可覆盖） | 读取 PAT 对应用户的游戏账号干员全量数据，**目标账号不接受请求参数**：带 `tlc_pat_` 前缀的新令牌取 `payload.game_uid` 并强制核对绑定关系；无前缀的迁移旧令牌没有 payload，按「用户默认来源 → 最近变更」取数。接口不声明任何查询参数，多传的参数由框架忽略、不参与取数。响应 `Cache-Control: private, no-cache`，结构与 `/oauth2/ak-accounts/operators` 完全一致（共用同一取数服务）。 |
| `/pat/ak-accounts/operators/save` | POST | 是 | PAT + `arknights-data.write` scope（`gama-data.write` 或 `all` 可覆盖） | 上传角色信息与干员数据，请求及响应与 OAuth 上传接口一致。目标账号取自 `playerInfo.akUid`，不受 PAT payload 绑定账号限制，无需预先绑定；用户身份取自 PAT，首次上传自动建立绑定。复用同一保存服务，支持可选物品、皮肤与 playerData，沿用校验、事务和按账号限流。 |
| `/pat/ef-accounts/player-data` | GET | 是 | PAT + `endfield-data.read` scope（`gama-data.read` 或 `all` 可覆盖） | 读取终末地玩家数据，新令牌按 `payload.game_uid`（roleId）+ `payload.server_id`（省略按空串）精确校验当前用户角色绑定；旧令牌读取当前用户已绑定角色中最近保存的数据。无查询参数，不能覆盖目标角色、服务器或用户。响应与 `/oauth2/ef-accounts/player-data` 一致，含 `Cache-Control: private, no-cache`。 |

> 迁移完成后旧令牌的 `uc:oauth:access:{token}` 已被删除，只能走本组接口；**消费端与阶段二迁移必须同批上线**，否则会出现旧令牌两边都不认的失效窗口。
