# UC / uc 缩写替换为 TCL / tcl 的详细清单

编制日期：2026-11-19

状态：已按本清单完成源码、注释、接口文档及 AGENTS.md 替换，尚未部署。表中的“现有名称”和源码行号记录执行前基线，改动后的准确位置以当前源码为准。Redis 范围已确认：14 个短期前缀直接替换，不做旧键兼容；9 个关联前缀继续保留。用户已接受此次切换造成的短期流程中断、状态重置及可能需要重新登录等影响。

核对依据：当前工作区的 Java 调用链、Controller 映射、拦截器配置及文档。文中行号为编制时的位置，后续修改可能使行号变化。

按文件列出的现有代码、目标替换片段和调用核对位置见 [UC 缩写替换代码明细](/C:/dev_projects/UserCenterV1/docs/UC缩写替换代码明细.md)。

## 1. 替换范围与阅读方式

目标命名按本次要求执行：`uc → tcl`、`UC → TCL`、驼峰局部变量按统一解析器重构清理（`ucToken` 被消除，`ucTicket → tclTicket`）。现有的 `tlc / TLC / Tlc` 是另一组拼写，本次不自动改成 `tcl`。

本清单区分三类事项：

- **可直接替换**：局部变量、服务简称、注释、纯文档占位符，以及第 4.1 节的 14 个短期 Redis 前缀；短期键直接从 uc: 切换为 tcl:，不读取、复制、迁移或回退旧键。
- **可替换，但仍兼容旧接口名称**：请求头和请求参数。此次确认仅取消 Redis 旧键兼容；UC-Token、UC-Client-Id、uc_ticket 的旧名称支持仍按第 2 节保留。
- **暂不替换**：第 4.2 节的 9 个会话/令牌及关联前缀、会话扫描表达式、已有凭证值、客户端标识值、现有 tlc 命名和未经核对的浏览器存储名称。

“受影响的 API”指名称读取或相关说明关联的接口；**API 的 URL 路径本身不改名**。表中 A—E 为第 5 节的对外名称影响分组，该节逐条列出 HTTP 方法、完整路径和路由来源。Redis 前缀的影响另见第 4 节；新增影响的 9 个接口补充在第 5 节 F 组。

| 分组 | 范围 | 当前接口数 | 对应替换项 |
| --- | --- | ---: | --- |
| A | 用户会话鉴权接口，含登出 | 50 | `UC-Token → TCL-Token` |
| B | OAuth access_token 资源接口 | 27 | `UC-Token → TCL-Token` |
| C | OAuth 登录用户解析接口 | 5 | `UC-Token → TCL-Token`、`uc_ticket → tcl_ticket` |
| D | PAT 消费接口 | 1 | `UC-Token → TCL-Token` |
| E | 登录、注册入口 | 2 | `UC-Client-Id → TCL-Client-Id` |

按 HTTP 方法与路径计数，令牌请求头涉及 **83 个接口**；来源客户端请求头另外涉及 **2 个接口**。这不表示所有请求都会受影响：使用现有 `Authorization: Bearer` 的请求不需要因改缩写而更换请求头。

## 2. 运行代码可替换清单

### 2.1 对外名称与提示

| 编号 | 现有名称 | 目标名称 | 涉及位置 | 受影响的 API 路径 | 处理要求 |
| --- | --- | --- | --- | --- | --- |
| R01 | 用户会话头 `UC-Token` | `TCL-Token` | [UserAuthInterceptor.java:109](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/interceptor/UserAuthInterceptor.java:109)；相关注释在 21、99 行 | A 组全部 50 个接口：`/user/**` 与 `POST /auth/logout`，逐条见第 5 节 | 同时接受新旧头；保留 `Authorization: Bearer` 优先。不可直接删除旧头，否则旧客户端会被判定未登录。 |
| R02 | 通用令牌头 `UC-Token` | `TCL-Token` | [RequestUtil.java:50](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RequestUtil.java:50)；相关注释在 40 行；调用方包括 OAuth/PAT 拦截器、OAuth 登录用户解析和登出控制器 | B、C、D 组全部接口，另有 `POST /auth/logout`；共 34 个方法与路径组合，其中登出与 R01 重叠 | 会话、OAuth access_token、PAT 仅共享取头逻辑，原有各自校验规则不变；新增头后仍须兼容旧头。 |
| R03 | `UC-Client-Id` 请求头 | `TCL-Client-Id` | [AuthController.java:33](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:33)；读取位置 73、90 行；相关注释 80 行 | `POST /auth/register`<br>`POST /auth/login` | 新旧头都能提供来源客户端标识；标识值本身、现有会话中的 `clientId` 和数据库归属不改。直接删除旧头支持会使旧前端登录、注册返回缺少参数。 |
| R04 | `uc_ticket`，控制器中的读取位置 | `tcl_ticket` | [OAuthController.java:186](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:186)；相关注释 90、179 行 | `GET /oauth2/authorize`<br>`POST /oauth2/authorize` | 此处读取仅用于判断是否打印“携带跨站登录票据”的日志；实际用户解析在 R05。两处须保持名称兼容一致。 |
| R05 | `uc_ticket`，服务层实际解析入口 | `tcl_ticket` | [OAuthTokenServiceImpl.java:150](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:150)，`resolveLoginUid`；调用点 211、258、409、460 行；相关注释 141、264 行 | `GET /oauth2/authorize`<br>`POST /oauth2/authorize`<br>`POST /oauth2/ticket`<br>`GET /oauth2/consent/info`<br>`POST /oauth2/consent` | 兼容旧参数名，选择一个票据后只消费一次。请求参数名仍兼容旧名，新票据继续使用原 TTL 和消费逻辑；Redis 按第 4.1 节直接切换，旧前缀下的票据不再读取，需重新发起流程。 |
| R06 | `缺少来源客户端标识（UC-Client-Id）` | `缺少来源客户端标识（TCL-Client-Id，兼容 UC-Client-Id）` | [AuthServiceImpl.java:704](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:704)；相关注释 692 行 | `POST /auth/register`<br>`POST /auth/login` | 兼容期提示同时列出新旧名称；业务码、必填校验、trim 和长度限制保持不变。 |

R05 的范围由调用链确定，而非仅依据接口说明：

| 现有名称 | 目标名称 | 涉及位置 | 受影响的 API 路径 | 当前读取方式 |
| --- | --- | --- | --- | --- |
| `uc_ticket` | `tcl_ticket` | `resolveLoginUid → buildAuthorizeRedirectUrl`，[OAuthTokenServiceImpl.java:258](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:258) | `GET /oauth2/authorize`、`POST /oauth2/authorize` | `HttpServletRequest.getParameter`，读取 query / 表单参数 |
| `uc_ticket` | `tcl_ticket` | `resolveLoginUid → createLoginTicket`，[OAuthTokenServiceImpl.java:211](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:211) | `POST /oauth2/ticket` | 同上；常规用途是会话头签发票据，当前共享解析器也会读取该参数 |
| `uc_ticket` | `tcl_ticket` | `resolveLoginUid → getConsentInfo`，[OAuthTokenServiceImpl.java:409](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:409) | `GET /oauth2/consent/info` | query 参数，或原有会话请求头 |
| `uc_ticket` | `tcl_ticket` | `resolveLoginUid → confirmAuthorization`，[OAuthTokenServiceImpl.java:460](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:460) | `POST /oauth2/consent` | JSON 请求体仍是 `pending_id / approve / scopes`；票据若通过此入口传入，应在 query 中，不新增 JSON 字段 |

### 2.2 仅局部变量改名

| 编号 | 现有名称 | 目标名称 | 涉及位置 | 受影响的 API 路径 | 处理要求 |
| --- | --- | --- | --- | --- | --- |
| V01 | `ucToken` | 移除重复变量，委托统一解析器 | [RequestUtil.java:50](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RequestUtil.java:50)（其余行：51、52） | B、C、D 组及 `POST /auth/logout` | 已移除原变量，由 RequestUtil.compatibleValue 统一解析；按 R02 保留旧请求头兼容。 |
| V02 | `ucToken` | 移除重复变量，委托统一解析器 | [UserAuthInterceptor.java:109](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/interceptor/UserAuthInterceptor.java:109)（其余行：110） | A 组 | 已移除原变量并委托 RequestUtil.resolveToken；不涉及存量会话格式。 |
| V03 | `ucTicket` | `tclTicket` | [OAuthController.java:186](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:186)（其余行：190） | `GET /oauth2/authorize`、`POST /oauth2/authorize` | 仅局部变量改名；请求参数兼容需按 R04、R05 单独处理。 |

### 2.3 兼容行为约定（已实现）

1. `Authorization: Bearer` 的既有优先级保持不变；新增 `TCL-Token` 后再回退旧 `UC-Token`。没有优先命中的 Bearer 时，新旧名称同时携带、trim 后的非空值不一致则返回参数错误 10001；来源头和票据参数采用同样的冲突规则。
2. 来源客户端头的新旧名称只改变传递方式，不改变客户端标识值，不修改会话、配置和登录日志的关联字段。
3. 票据解析应先选定一个新/旧参数值，再执行原有一次性消费逻辑，不依次消费两枚票据。
4. 文档对外使用新名称并说明旧名兼容；停止旧名支持不包含在本清单的首次替换范围内。
5. [CorsConfig.java:39](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/config/CorsConfig.java:39) 当前允许任意请求头，项目内未发现必须同步改名的 CORS 请求头白名单。外部网关和前端仓库未在本次核对范围内。

## 3. 注释与文档可替换清单

本节均为文字、注释或示例改名。表中的 API 是说明关联范围，**只改文字不会改变运行行为**；请求头和参数示例已与第 2 节实现同步更新，保留旧名称兼容说明。

### 3.1 Java 与测试配置中的业务简称

第 2 节六个 Java 文件里的相关注释一并同步。其他可直接更新的业务简称如下：

| 现有名称 | 目标名称 | 涉及位置 | 受影响的 API 路径 |
| --- | --- | --- | --- |
| UC 会话 token | TCL 会话 token | [OAuthClientAdminController.java:26](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:26) | `GET /user/oauth/client/scopes`<br>`POST /user/oauth/client/register`<br>`GET /user/oauth/client/list`<br>`GET /user/oauth/client/{clientId}`<br>`POST /user/oauth/client/{clientId}/update`<br>`POST /user/oauth/client/{clientId}/rotate-secret`<br>`POST /user/oauth/client/{clientId}/disable`<br>`POST /user/oauth/client/{clientId}/enable`<br>`POST /user/oauth/client/{clientId}/delete` |
| UC-Token；全新 UC 令牌 | TCL-Token（兼容 UC-Token）；全新 TCL 令牌 | [OAuthTokenService.java:41](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/OAuthTokenService.java:41)（其余行：185） | `POST /oauth2/ticket`<br>`POST /oauth2/internal/migrate-token` |
| UC 令牌 | TCL 令牌 | [OAuthMigrateService.java:10](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/OAuthMigrateService.java:10)（其余行：19） | `POST /oauth2/internal/migrate-token` |
| UC 侧只存公钥 | TCL 侧只存公钥 | [OAuthMigrateServiceImpl.java:32](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:32) | `POST /oauth2/internal/migrate-token` |
| 全新的 UC 令牌；UC 侧 | 全新的 TCL 令牌；TCL 侧 | [OAuthInternalController.java:41](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthInternalController.java:41)（其余行：47） | `POST /oauth2/internal/migrate-token` |
| 浏览器直接提交 UC | 浏览器直接提交 TCL | [OAuthLegacyLoginController.java:24](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:24)（其余行：67、93） | `POST /oauth2/direct-session`<br>`POST /oauth2/direct-login`<br>`POST /oauth2/direct-register`<br>`POST /oauth2/direct-user` |
| UC 会话 token | TCL 会话 token | [ServerLoginVO.java:14](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/auth/ServerLoginVO.java:14) | `POST /oauth2/direct-user`（返回该视图；注释用于区分会话 token） |
| 前端直接向 UC 提交 | 前端直接向 TCL 提交 | [DirectLoginTicketVO.java:8](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/oauth/DirectLoginTicketVO.java:8) | `POST /oauth2/direct-login`<br>`POST /oauth2/direct-register` |
| UC → BackEndV3 | TCL → BackEndV3 | [MigrateTokenVO.java:7](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/oauth/MigrateTokenVO.java:7) | `POST /oauth2/internal/migrate-token` |
| 以 UC 用户作为登录账号 | 以 TCL 用户作为登录账号 | [UserInfoVO.java:9](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/oauth/UserInfoVO.java:9) | `GET /oauth2/userinfo` |
| BackEndV3 → UC；UC 侧 | BackEndV3 → TCL；TCL 侧 | [MigrateTokenRequest.java:4](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/dto/oauth/MigrateTokenRequest.java:4)（其余行：11） | `POST /oauth2/internal/migrate-token` |
| UC 侧只存公钥 | TCL 侧只存公钥 | [application-test.yml:92](/C:/dev_projects/UserCenterV1/src/main/resources/application-test.yml:92) | `POST /oauth2/internal/migrate-token`，仅配置注释，不改配置键和值 |

### 3.2 当前文档逐文件清单

| 现有名称 | 目标名称 | 涉及位置 | 受影响的 API 路径 |
| --- | --- | --- | --- |
| `UC-Token` | TCL-Token，并注明兼容旧名 | [README.md:23](/C:/dev_projects/UserCenterV1/README.md:23) | A、B、C、D 组；同段 Redis `uc:` 说明保留 |
| `UC-Token；UC-Client-Id；uc_ticket；UC 业务简称` | TCL-Token；TCL-Client-Id；tcl_ticket；TCL | [API.md:5](/C:/dev_projects/UserCenterV1/docs/API.md:5)（其余行：6、7、20、21、30、138、139、152、211、212、219、222） | A—E 组；`POST /oauth2/direct-login`、`POST /oauth2/direct-register`、`POST /oauth2/internal/migrate-token` 仅改业务描述 |
| `UC-Token；UC-Client-Id` | TCL-Token；TCL-Client-Id，均注明旧名兼容 | [认证接口文档.md:29](/C:/dev_projects/UserCenterV1/docs/user/认证接口文档.md:29)（其余行：34、36、39、130、160、184、197、210、227、281） | `POST /auth/login`、`POST /auth/register`、`POST /auth/logout`；返回 token 后的 A 组使用说明 |
| `UC-Token；UC-Client-Id` | TCL-Token；TCL-Client-Id，注明旧名兼容 | [用户配置接口文档.md:22](/C:/dev_projects/UserCenterV1/docs/user/用户配置接口文档.md:22)（其余行：30、160、417） | `POST /user/config/save`<br>`POST /user/config/save-if-match`<br>`GET /user/config/quota`<br>`GET /user/config/list`<br>`POST /user/config/delete`<br>`POST /auth/login`、`POST /auth/register`（来源标识说明） |
| `UC-Token` | TCL-Token，注明旧名兼容 | [用户游戏数据接口文档.md:22](/C:/dev_projects/UserCenterV1/docs/user/用户游戏数据接口文档.md:22) | `GET /user/ak-accounts`<br>`GET /user/ak-accounts/operators`<br>`POST /user/ak-accounts/default/update`<br>`DELETE /user/ak-accounts`<br>`GET /user/ak-accounts/items`<br>`GET /user/ak-accounts/skins`<br>`POST /user/ak-accounts/operators/save`<br>`POST /user/ak-accounts/manual-operators/save` |
| `UC-Token` | TCL-Token，注明旧名兼容 | [终末地玩家数据接口文档.md:22](/C:/dev_projects/UserCenterV1/docs/user/终末地玩家数据接口文档.md:22) | `GET /user/ef-accounts`<br>`GET /user/ef-accounts/player-data`<br>`POST /user/ef-accounts/player-data/save` |
| `UC-Token` | TCL-Token，注明旧名兼容 | [终末地寻访记录接口文档.md:249](/C:/dev_projects/UserCenterV1/docs/user/终末地寻访记录接口文档.md:249) | `POST /user/ef-pool-records/create-task`<br>`GET /user/ef-pool-records/check-task`<br>`GET /user/ef-pool-records/accounts`<br>`GET /user/ef-pool-records/record` |
| `UC-Token` | TCL-Token，注明旧名兼容 | [用户排班表接口文档.md:22](/C:/dev_projects/UserCenterV1/docs/user/用户排班表接口文档.md:22) | `POST /user/schedule/save`<br>`GET /user/schedule/list`<br>`POST /user/schedule/delete` |
| `UC-Token` | TCL-Token，注明旧名兼容 | [用户授权应用管理接口文档.md:20](/C:/dev_projects/UserCenterV1/docs/user/用户授权应用管理接口文档.md:20) | `GET /user/oauth/grants`<br>`POST /user/oauth/grants/revoke` |
| `UC-Token` | TCL-Token，注明旧名兼容 | [用户授权权限管理接口文档.md:27](/C:/dev_projects/UserCenterV1/docs/user/用户授权权限管理接口文档.md:27) | `GET /user/oauth/grants/scopes`<br>`POST /user/oauth/grants/scopes/grant`<br>`POST /user/oauth/grants/scopes/revoke` |
| `UC_SESSION_TOKEN；UC-Token；UC 会话` | TCL_SESSION_TOKEN；TCL-Token（兼容旧名）；TCL 会话 | [OAuth2客户端管理文档.md:16](/C:/dev_projects/UserCenterV1/docs/oauth/OAuth2客户端管理文档.md:16)（其余行：22、126、204、309、345、362、410、445、456、465、497） | `GET /user/oauth/client/scopes`<br>`POST /user/oauth/client/register`<br>`GET /user/oauth/client/list`<br>`GET /user/oauth/client/{clientId}`<br>`POST /user/oauth/client/{clientId}/update`<br>`POST /user/oauth/client/{clientId}/rotate-secret`<br>`POST /user/oauth/client/{clientId}/disable`<br>`POST /user/oauth/client/{clientId}/enable`<br>`POST /user/oauth/client/{clientId}/delete` |
| `UC_BASE_URL` | TCL_BASE_URL（示例占位符） | [无后端Web应用OAuth2接入文档.md:9](/C:/dev_projects/UserCenterV1/docs/oauth/无后端Web应用OAuth2接入文档.md:9)（其余行：97、138、183、216、275） | `GET /oauth2/authorize`<br>`POST /oauth2/token`<br>`GET /oauth2/userinfo`<br>`POST /oauth2/revoke`；占位符改名不改变这些路径 |
| `UC_BASE_URL` | TCL_BASE_URL（示例占位符） | [加密客户端OAuth2接入文档.md:9](/C:/dev_projects/UserCenterV1/docs/oauth/加密客户端OAuth2接入文档.md:9)（其余行：87、130、179、214、260） | `GET /oauth2/authorize`<br>`POST /oauth2/token`<br>`GET /oauth2/userinfo`<br>`POST /oauth2/revoke`；占位符改名不改变这些路径 |
| `UC-Token；<uc-host>` | TCL-Token（兼容旧名）；<tcl-host>（占位符） | [OAuth用户资料接口文档.md:18](/C:/dev_projects/UserCenterV1/docs/oauth/OAuth用户资料接口文档.md:18)（其余行：190、193、199） | `GET /oauth2/userinfo`<br>`POST /oauth2/profile/nickname`<br>`POST /oauth2/profile/avatar` |
| `UC 业务简称；uc\\_ticket（Markdown 转义写法）；方案标题里的 UC` | TCL；tcl\\_ticket；标题里的 TCL（被引用资料也须同步核对） | [OAuth2与标准协议差距清单.md:84](/C:/dev_projects/UserCenterV1/docs/oauth/OAuth2与标准协议差距清单.md:84)（其余行：90、91、92、99） | C 组及 `POST /oauth2/internal/migrate-token`；59 行 Redis 实际 key 保留 |
| `UC 服务简称／方案标题` | TCL 服务简称／方案标题；UC_ACCESS_TOKEN 暂保留 | [OAuth游戏数据接口文档.md:42](/C:/dev_projects/UserCenterV1/docs/oauth/OAuth游戏数据接口文档.md:42) | B 组游戏资源：`/oauth2/ak-accounts`、`/oauth2/ak-accounts/operators`、`/oauth2/ak-accounts/default/update`、`/oauth2/ak-accounts/items`、`/oauth2/ak-accounts/operators/save`、`/oauth2/ak-accounts/manual-operators/save`，方法详见 B 组；存量兑换 API 位于外部 BackEndV3，本仓库无法核实 |
| `UC-Token` | TCL-Token，注明旧名兼容 | [PAT接口文档.md:58](/C:/dev_projects/UserCenterV1/docs/pat/PAT接口文档.md:58) | `GET /pat/ak-accounts/operators`；A 组 `/user/pat` 管理接口；`GET /oauth2/pat/list`、`POST /oauth2/pat/revoke`；根路径 `POST /oauth2/pat` 仍以 refresh_token 认证 |

`UC_BASE_URL`、`UC_SESSION_TOKEN` 在以上位置是文档占位符，本仓库未发现同名运行配置。修改占位符不代表修改实际域名、部署环境变量、浏览器存储或令牌值。

### 3.3 归档资料、规则和不应误替换的内容

| 现有名称 | 目标名称 | 涉及位置 | 受影响的 API 路径 | 建议 |
| --- | --- | --- | --- | --- |
| `UC-Token` | `TCL-Token`，附旧名说明 | [PAT消费端接口设计.md:77](/C:/dev_projects/UserCenterV1/docs/archive/PAT消费端接口设计.md:77) | `GET /pat/ak-accounts/operators` | 如需统一归档用语，可改说明；41、242 行真实 Redis key 保留。 |
| `uc-token`（小写写法） | `TCL-Token`，附旧名说明 | [PAT改造方案-最终版.md:25](/C:/dev_projects/UserCenterV1/docs/archive/PAT改造方案-最终版.md:25) | 历史 PAT 管理说明，当前管理接口为 A 组 `/user/pat`；不是新增接口 | HTTP 请求头大小写不敏感，此处归入同一头名称；历史方案和 Redis 示例不做机械替换。 |
| `UC`、`UC-Token`、`UC-Client-Id`、`UC_BASE_URL`、`UC_SESSION_TOKEN` | 对应 `TCL` 名称；请求头须注明兼容 | [OAuth令牌直签接口文档.md:38](/C:/dev_projects/UserCenterV1/docs/archive/OAuth令牌直签接口文档.md:38)（其余行：51、116、198、199、211、212、223、224、237、310、386、409、457、458、467） | 包含历史 `/user/oauth/custom-tokens`、`/oauth2/token/from-refresh`、`/oauth2/custom-tokens/revoke` 等，当前 Controller 未注册，不能计入本轮真实 API 清单 | 如更新简称，只更新文案，不把历史接口描述成当前接口。 |
| `uc_token` | 暂保留；不新增 `tcl_token` 参数 | [OAuth令牌直签接口文档.md:51](/C:/dev_projects/UserCenterV1/docs/archive/OAuth令牌直签接口文档.md:51)（其余行：80） | 历史 `/user/oauth/custom-tokens` 说明 | 原文说明“不需要也不接受该参数”，不是当前运行参数。 |
| “存量 uc 不动，新增用 tlc” | 已改为“新增业务简称 TCL/tcl；14 个短期前缀 tcl:；9 个关联前缀 uc:；现有 tlc 和浏览器键保留” | [AGENTS.md:17](/C:/dev_projects/UserCenterV1/AGENTS.md:17) | 无直接 API；约束后续开发 | 已同步更新规则；现有 `tlc` 实体、表和凭证前缀不受影响。 |
| `SUCCESS`、`success`、`NoSuchAlgorithmException`、`bucket`、`getClientId` 等单词中的字母片段 | 保持原样 | Java 标识符、第三方 API 和配置项 | 无此次简称替换影响 | 不是 UC 缩写，禁止按子串全局替换。 |

## 4. Redis 前缀替换与保留清单

本节按用户最新确认调整：**14 个短期前缀直接从 `uc:` 替换为 `tcl:`，不做旧键兼容；9 个会话、令牌及其关联前缀继续保留**。用户接受旧验证码、授权码、票据和确认单失效，登录/授权流程中断，以及失败计数、锁、限流和 nonce 状态在新前缀下重新开始。原有长期登录态和令牌不主动清理；发生短期流程失败时可重新发码、重新授权或重新登录。源码中的前缀常量已替换；未连接或操作线上 Redis，实际切换在部署后生效。

以下期限来自当前代码和允许读取的测试配置，**未直接查询线上 Redis 的实际 TTL**。用户策略见 [UserAccentProperties.java:15](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/config/UserAccentProperties.java:15)，OAuth 策略见 [OAuthProperties.java:19](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/config/OAuthProperties.java:19)，直连策略见 [OAuthDirectProperties.java:15](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/config/OAuthDirectProperties.java:15)；nonce 见 [OAuthMigrateServiceImpl.java:149](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:149) 和 [application-test.yml:86](/C:/dev_projects/UserCenterV1/src/main/resources/application-test.yml:86)。客户端可覆盖 access/refresh 有效期，不能把默认值当成所有线上键的固定 TTL。

### 4.1 直接替换的短期 Redis 前缀（14 个，不做旧键兼容）

| 现有名称 | 目标名称 | 有效期 | 涉及位置（前缀定义） | 受影响的 API 路径 | 切换要求 |
| --- | --- | --- | --- | --- | --- |
| `uc:code:` | `tcl:code:` | 5 分钟 | [PREFIX_CODE:16](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:16) | `POST /auth/send-code`、`POST /auth/reset-code`、`POST /auth/reset-password`、`POST /auth/register`、`POST /auth/login`、`POST /oauth2/direct-login`、`POST /oauth2/direct-register`、`POST /user/email/bind`、`POST /user/email/send-change-code`、`POST /user/email/change` | 直接切换；旧验证码不再读取，校验失败后重新获取。新验证码 TTL 仍为 5 分钟。 |
| `uc:login:fail:` | `tcl:login:fail:` | 15 分钟 | [PREFIX_LOGIN_FAIL:19](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:19) | `POST /auth/login`、`POST /oauth2/direct-login` | 直接切换；新前缀从零累计失败次数，旧计数不迁移、不回退。 |
| `uc:lock:` | `tcl:lock:` | 3 分钟 | [PREFIX_LOGIN_LOCK:22](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:22) | `POST /auth/login`、`POST /oauth2/direct-login` | 直接切换；旧前缀下的锁不再检查，可能提前解除原有登录锁定；新锁仍使用原时长。 |
| `uc:rate:` | `tcl:rate:` | 按业务为 2 秒～1 小时，详见 4.3 | [PREFIX_RATE:25](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:25) | `POST /auth/send-code`、`POST /auth/reset-code`、`POST /auth/reset-password`、`POST /auth/register`、`POST /auth/login`、`POST /oauth2/direct-login`、`POST /oauth2/direct-register`、`POST /user/email/bind`、`POST /user/email/send-change-code`、`POST /user/email/change`；`POST /oauth2/direct-session`、`POST /oauth2/internal/migrate-token`；游戏数据保存与寻访任务接口详见 4.3 | 直接切换；原有额度、计数和短锁不继承，新前缀按原规则重新开始。 |
| `uc:oauth:code:` | `tcl:oauth:code:` | 5 分钟 | [PREFIX_OAUTH_CODE:28](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:28) | `GET /oauth2/authorize`、`POST /oauth2/authorize`、`POST /oauth2/consent`、`POST /oauth2/token` | 直接切换；旧授权码无法兑换，需要重新发起授权。新授权码仍由现有 Lua 原子消费。 |
| `uc:oauth:code:used:` | `tcl:oauth:code:used:` | 消费时写入，写入后 5 分钟 | [PREFIX_OAUTH_CODE_USED:31](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:31) | `POST /oauth2/token` | 直接切换；旧已用标记不再读取。旧授权码记录也不再读取，不复制旧码到新命名空间。 |
| `uc:oauth:ticket:` | `tcl:oauth:ticket:` | 5 分钟 | [PREFIX_OAUTH_TICKET:49](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:49) | `GET /oauth2/authorize`、`POST /oauth2/authorize`、`POST /oauth2/ticket`、`GET /oauth2/consent/info`、`POST /oauth2/consent` | 直接切换；旧跨站登录票据失效，重新获取票据；请求参数名称兼容与 Redis 旧键兼容分别处理。 |
| `uc:oauth:ticket:used:` | `tcl:oauth:ticket:used:` | 尝试消费时写入，写入后 5 分钟 | [PREFIX_OAUTH_TICKET_USED:52](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:52) | `GET /oauth2/authorize`、`POST /oauth2/authorize`、`POST /oauth2/ticket`、`GET /oauth2/consent/info`、`POST /oauth2/consent` | 直接切换；不继承旧已用标记。新票据仍按现有逻辑记录已用状态。 |
| `uc:oauth:consent:` | `tcl:oauth:consent:` | 5 分钟 | [PREFIX_OAUTH_CONSENT:55](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:55) | `GET /oauth2/authorize`、`POST /oauth2/authorize`、`GET /oauth2/consent/info`、`POST /oauth2/consent` | 直接切换；旧授权确认单查不到，已经打开的确认页需要重新发起授权。 |
| `uc:oauth:consent:used:` | `tcl:oauth:consent:used:` | 尝试消费时写入，写入后 5 分钟 | [PREFIX_OAUTH_CONSENT_USED:58](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:58) | `POST /oauth2/consent` | 直接切换；旧标记不继承，新确认单按现有消费逻辑处理。 |
| `uc:oauth:direct:channel:` | `tcl:oauth:direct:channel:` | 5 分钟 | [PREFIX_DIRECT_CHANNEL:67](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:67) | `POST /oauth2/direct-session`、`POST /oauth2/direct-login`、`POST /oauth2/direct-register` | 直接切换；旧直连 channel 失效，需要重新发起直连登录/注册。 |
| `uc:oauth:direct:ticket:` | `tcl:oauth:direct:ticket:` | 1 分钟 | [PREFIX_DIRECT_TICKET:70](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:70) | `POST /oauth2/direct-login`、`POST /oauth2/direct-register`、`POST /oauth2/direct-user` | 直接切换；旧直连登录票据不能兑换，需重新获取。 |
| `uc:oauth:direct:used:` | `tcl:oauth:direct:used:` | 尝试消费时写入，写入后 1 分钟 | [PREFIX_DIRECT_USED:73](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:73) | `POST /oauth2/direct-user` | 直接切换；旧防重放标记不继承，新票据按原逻辑处理。 |
| `uc:oauth:migrate:nonce:` | `tcl:oauth:migrate:nonce:` | 2 × migrate.clock-skew-seconds；测试配置为 240 秒（4 分钟） | [PREFIX_OAUTH_MIGRATE_NONCE:76](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:76) | `POST /oauth2/internal/migrate-token` | 直接切换；旧 nonce 已用记录不继承。仍在允许时间窗内的旧合法签名请求可能被再次接受；该状态重置已纳入本次接受范围，签名和时窗校验本身不变。 |

### 4.2 继续保留的会话、令牌及关联前缀（9 个）

本表按业务关联保留，不只看 TTL 长短；因此包含 60 秒的 refresh:next 和默认 2 小时的 access。现有凭证值、序列化字段、剩余有效期及吊销语义均保持不变。

| 现有名称 | 目标名称 | 当前有效期 / 存活方式 | 涉及位置 | 受影响的 API 路径 | 保留原因 |
| --- | --- | --- | --- | --- | --- |
| `uc:token:` | 保持 `uc:token:` | 90 天 | [PREFIX_TOKEN:13](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:13) | A 组；C 组的会话读取 | 用户登录态；改前缀会导致现有会话无法读取。 |
| `uc:oauth:access:` | 保持 `uc:oauth:access:` | 默认 2 小时，客户端可单独配置 | [PREFIX_OAUTH_ACCESS:34](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:34) | B 组；`POST /oauth2/token`、`POST /oauth2/revoke` 等令牌流程 | 虽然比 refresh 短，仍是现有访问凭证，并与长期 refresh、索引和吊销流程关联，本轮保留。 |
| `uc:oauth:refresh:` | 保持 `uc:oauth:refresh:` | 默认 90 天，客户端可单独配置 | [PREFIX_OAUTH_REFRESH:37](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:37) | `POST /oauth2/token`、`POST /oauth2/pat`、用户授权管理及其他令牌签发/吊销流程 | 长期刷新凭证，直接切换会导致旧客户端无法续期。 |
| `uc:oauth:refresh:used:` | 保持 `uc:oauth:refresh:used:` | 旧 refresh 剩余有效期，可能长达 90 天或客户端配置期限 | [PREFIX_OAUTH_REFRESH_USED:40](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:40) | `POST /oauth2/token`；刷新重放识别 | 轮转墓碑用于识别重放，不能只保留 refresh 而丢失其关联状态。 |
| `uc:oauth:refresh:next:` | 保持 `uc:oauth:refresh:next:` | 60 秒（刷新轮转宽限期） | [PREFIX_OAUTH_REFRESH_NEXT:43](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:43) | `POST /oauth2/token`；刷新宽限期幂等 | 短期例外：与长期 refresh/墓碑联动，单独切换可能使合法重试被当作重放，触发整族令牌吊销。 |
| `uc:oauth:family:` | 保持 `uc:oauth:family:` | 与家族成员有效期关联，TTL 按需要延长 | [PREFIX_OAUTH_FAMILY:46](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:46) | `POST /oauth2/token` 等令牌家族管理 | 保留令牌家族索引和整族吊销能力。 |
| `uc:uid:session:` | 保持 `uc:uid:session:` | 当前写入逻辑未设置 TTL | [PREFIX_UID_SESSION:61](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:61) | A 组会话管理；登录、登出及会话吊销 | 用户会话反向索引，必须继续覆盖旧会话；不趁改名新增过期策略。 |
| `uc:uid:oauth:` | 保持 `uc:uid:oauth:` | 当前写入逻辑未设置 TTL | [PREFIX_UID_OAUTH:64](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:64) | 用户授权管理、OAuth 令牌签发和吊销 | OAuth 令牌反向索引，保留授权管理和撤销能力。 |
| `uc:oauth:migrate:current:` | 保持 `uc:oauth:migrate:current:` | 与对应 refresh 有效期一致，默认 90 天 | [PREFIX_OAUTH_MIGRATE_CURRENT:79](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:79) | `POST /oauth2/internal/migrate-token`；上一轮迁移凭证映射 | 记录上一轮迁移签发的 refresh，供下次替换时撤销，不可丢失映射。 |

### 4.3 短期前缀的读写调用点与限流细分

直接替换 RedisKeyUtil 中的 14 个常量及其注释；现有构建方法、业务调用语句和 TTL 保持原样。下列位置用于核对读、写、删除和 Lua 传参会全部使用新的 tcl: 键，不新增兼容存储层或 legacy 方法。

| 现有名称 | 目标名称 | 涉及位置（定义之外的调用点） | 受影响的 API 路径 |
| --- | --- | --- | --- |
| `uc:code:` | `tcl:code:` | [EmailCodeServiceImpl.java:112](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:112) 写入、135 行读取；AuthServiceImpl 的注册/登录/重设密码及 UserServiceImpl 的邮箱校验共用它 | `POST /auth/send-code`、`POST /auth/reset-code`、`POST /auth/reset-password`、`POST /auth/register`、`POST /auth/login`、`POST /oauth2/direct-login`、`POST /oauth2/direct-register`、`POST /user/email/bind`、`POST /user/email/send-change-code`、`POST /user/email/change` |
| `uc:login:fail:`、`uc:lock:` | 对应 `tcl:` 前缀 | [AuthServiceImpl.java:757](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:757) 锁检查；833、842、846、859、860 行失败计数、锁写入与清理 | `POST /auth/login`、`POST /oauth2/direct-login` |
| `uc:rate:` | `tcl:rate:` | [RedisRateLimiter.java:28](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisRateLimiter.java:28) 及以下业务调用；游戏上传通过 setIfAbsent 直接写入，不能只修改通用限流器 | 下表逐项列出 |
| `uc:oauth:code:`、`uc:oauth:code:used:` | 对应 `tcl:` 前缀 | [OAuthTokenServiceImpl.java:339](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:339) 签发；[RedisOAuthTokenStore.java:46](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java:46) 及 51、57、58 行读取/消费；[oauth-code-consume.lua:8](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-code-consume.lua:8) 原子比较、删除和已用标记写入 | `GET /oauth2/authorize`、`POST /oauth2/authorize`、`POST /oauth2/consent`、`POST /oauth2/token` |
| `uc:oauth:ticket:`、`uc:oauth:ticket:used:` | 对应 `tcl:` 前缀 | [OAuthTokenServiceImpl.java:189](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:189) 标记写入、193 行读取、198 行删除、218 行签发；共用 resolveLoginUid | C 组全部 5 个接口 |
| `uc:oauth:consent:`、`uc:oauth:consent:used:` | 对应 `tcl:` 前缀 | [OAuthTokenServiceImpl.java:385](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:385) 签发、404 行查询、451 行已用标记、455 行读取、465 行删除 | `GET /oauth2/authorize`、`POST /oauth2/authorize`、`GET /oauth2/consent/info`、`POST /oauth2/consent` |
| `uc:oauth:direct:channel:`、`uc:oauth:direct:ticket:`、`uc:oauth:direct:used:` | 对应 `tcl:` 前缀 | [AuthServiceImpl.java:394](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:394) channel 签发、433/462 行删除、493 行读取；586 行 ticket 签发、618 行已用标记、623 行读取、627 行删除 | `POST /oauth2/direct-session`、`POST /oauth2/direct-login`、`POST /oauth2/direct-register`、`POST /oauth2/direct-user` |
| `uc:oauth:migrate:nonce:` | `tcl:oauth:migrate:nonce:` | [OAuthMigrateServiceImpl.java:149](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:149) setIfAbsent 防重放；TTL 为 clockSkewSeconds × 2 | `POST /oauth2/internal/migrate-token` |

`uc:rate:` 下所有业务名和 target 后缀保持不变，只计划替换最前面的 `uc:`；例如 `uc:rate:login-ip:{ip} → tcl:rate:login-ip:{ip}`。

| 现有名称（业务键前缀） | 目标名称 | 窗口 | 涉及位置 | 受影响的 API 路径 |
| --- | --- | --- | --- | --- |
| `uc:rate:send-code:`、`uc:rate:send-code-email:` | 对应 `tcl:rate:` 前缀 | 60 秒 | [EmailCodeServiceImpl.java:88](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:88)、105 行 | `POST /auth/send-code`、`POST /auth/reset-code`、`POST /user/email/send-change-code` |
| `uc:rate:send-code-hour:` | `tcl:rate:send-code-hour:` | 1 小时 | [EmailCodeServiceImpl.java:94](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:94) | 同上三个验证码发送入口 |
| `uc:rate:login-ip:` | `tcl:rate:login-ip:` | 60 秒 | [AuthServiceImpl.java:539](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:539) | `POST /auth/login` |
| `uc:rate:register-ip:`、`uc:rate:direct-register-ip:` | 对应 `tcl:rate:` 前缀 | 10 分钟 | [AuthServiceImpl.java:558](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:558)；调用方分别在 138、452 行传入业务名 | `POST /auth/register`、`POST /oauth2/direct-register` |
| `uc:rate:register-email:`、`uc:rate:direct-register-email:` | 对应 `tcl:rate:` 前缀 | 1 小时 | [AuthServiceImpl.java:564](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:564) | `POST /auth/register`、`POST /oauth2/direct-register` |
| `uc:rate:direct-session-ip:`、`uc:rate:direct-session-client-ip:` | 对应 `tcl:rate:` 前缀 | 60 秒 | [AuthServiceImpl.java:506](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:506)、509 行 | `POST /oauth2/direct-session` |
| `uc:rate:direct-login-ip:` | `tcl:rate:direct-login-ip:` | 60 秒 | [AuthServiceImpl.java:517](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:517) | `POST /oauth2/direct-login` |
| `uc:rate:oauth-migrate-ip:`、`uc:rate:oauth-migrate-uid:`、`uc:rate:oauth-migrate-client:` | 对应 `tcl:rate:` 前缀 | 60 秒 | [OAuthMigrateServiceImpl.java:284](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:284)、286、288 行 | `POST /oauth2/internal/migrate-token` |
| `uc:rate:ak-operator-save:` | `tcl:rate:ak-operator-save:` | 2 秒 | [AkAccountServiceImpl.java:475](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AkAccountServiceImpl.java:475)、479 行 | `POST /user/ak-accounts/operators/save`、`POST /oauth2/ak-accounts/operators/save` |
| `uc:rate:ef-player-data-save:` | `tcl:rate:ef-player-data-save:` | 2 秒 | [EfPlayerDataServiceImpl.java:272](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EfPlayerDataServiceImpl.java:272)、276 行 | `POST /user/ef-accounts/player-data/save`、`POST /oauth2/ef-accounts/player-data/save` |
| `uc:rate:ef-pool-record-task:` | `tcl:rate:ef-pool-record-task:` | 60 秒 | [EfPoolRecordTaskService.java:107](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/EfPoolRecordTaskService.java:107)、110 行 | `POST /user/ef-pool-records/create-task`、`POST /oauth2/ef-pool-records/create-task` |

以上限流业务键均属于同一个 PREFIX_RATE，不额外计入“14 个可替换前缀”的数量。

### 4.4 半夜部署与直接切换约定

1. **14 类全部直接替换**：修改 RedisKeyUtil 的短期前缀常量及注释；不双读、不双写、不复制旧键，不新增 legacy 构建方法或兼容存储层。
2. **统一停止旧实例再启动新版本**：计划半夜部署，切换时不让新旧版本并行处理请求。所有新实例统一使用新的短期前缀，避免请求在两套短期状态之间来回切换。
3. **接受短期流程失败**：部署前的验证码、授权码、跨站票据、授权确认单、直连 channel 和票据不再可读；相关用户重新获取验证码、重新发起登录/授权，必要时重新登录。不将保留的长期会话或令牌主动批量吊销。
4. **接受计数与防重放状态重置**：新前缀下登录失败次数、限流额度和上传短锁重新开始；旧锁不再生效。旧 nonce 已用记录不继承，在原允许时间窗内的旧合法签名请求可能被再次接受；新签名请求仍按原验签、时间窗和新 nonce 标记逻辑处理。
5. **保留新键的原有行为**：新键仍使用现有 TTL、计数规则、锁时长及消费流程；授权码沿用现有 compare-and-delete Lua，不扩展双命名空间消费协议。现有代码行为不借此改名重写。
6. **旧短期键自然过期**：无需等待旧 TTL 后才能上线，也不批量删除旧键，不执行 FLUSHDB、前缀扫描删除或迁移脚本。旧值只是不再被新版本读取。
7. **九类关联前缀保持不变**：长期会话/令牌及其索引、refresh:next、migrate-current 和会话扫描表达式保留；三个长期 OAuth Lua 的键入参不改，授权码消费 Lua 正文及 KEYS/ARGV 结构也不改。
8. **回滚也不做短期状态同步**：若回退旧版本，未过期的旧短期键可能重新被读取，新前缀短期键则不再使用；接受在途流程再次失败或状态再次切换。九类保留前缀不随回滚改变。

此次只确认并记录文档方案，没有执行部署、代码替换或 Redis 操作。请求头和请求参数的旧名称兼容仍按第 2 节保留。

### 4.5 其他继续保留的存量标识和浏览器状态

| 现有名称 | 目标名称 | 涉及位置 | 关联 API 路径 | 保留原因 |
| --- | --- | --- | --- | --- |
| `uc:token:*` | 保持原样 | [UserServiceImpl.java:42](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/UserServiceImpl.java:42) | `GET /user/sessions`；`POST /user/sessions/{token}/kick` 按具体 token 取 key，不使用该扫描表达式 | 扫描必须匹配存量会话；不可把保留的 key 前缀与扫描表达式改成不同命名。 |
| Lua 示例里的 `uc:oauth:*`、`uc:uid:oauth:*` | 保持原样 | [oauth-refresh-rotate.lua:10](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-refresh-rotate.lua:10)（其余行：11、12、13、14、15、16）；[oauth-refresh-issue.lua:7](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-refresh-issue.lua:7)（其余行：8、9）；[oauth-migrate-issue.lua:11](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-migrate-issue.lua:11)（其余行：12、13、14、22） | `POST /oauth2/token`、`POST /oauth2/internal/migrate-token` 等令牌存储流程 | 注释应与实际传入 key 一致；脚本键值和调用协议不因改简称而变化。 |
| 测试断言 `uc:uid:oauth:100` | 保持原样 | [PatMigrationServiceImplTest.java:84](/C:/dev_projects/UserCenterV1/src/test/java/com/orange/service/impl/PatMigrationServiceImplTest.java:84) | `GET /internal/pat-migration/run`（仅说明关联，不调用、不执行迁移） | 存储命名保留，断言也应保留。 |
| Redis 实际 key 的注释、SQL 注释及文档说明 | 保持实际名称 | [SessionInfo.java:6](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/dto/SessionInfo.java:6)；[TlcPat.java:15](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/po/TlcPat.java:15)；[PatServiceImpl.java:36](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/PatServiceImpl.java:36)；[OAuthTokenServiceImpl.java:1546](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:1546)；[schema-user.sql:261](/C:/dev_projects/UserCenterV1/src/main/resources/db/schema-user.sql:261) | A、B、C、D 组及令牌维护流程，仅说明关联 | 不是品牌文案；改成不存在的 key 会误导排查。 |
| Redis 实际 key 的当前/历史资料 | 保持实际名称 | [README.md:23](/C:/dev_projects/UserCenterV1/README.md:23)；[API.md:220](/C:/dev_projects/UserCenterV1/docs/API.md:220)（其余行：230）；[PAT整体架构.md:105](/C:/dev_projects/UserCenterV1/docs/pat/PAT整体架构.md:105)（其余行：280、281、317、318、398）；[PAT接口文档.md:285](/C:/dev_projects/UserCenterV1/docs/pat/PAT接口文档.md:285)；[用户授权权限管理接口文档.md:260](/C:/dev_projects/UserCenterV1/docs/user/用户授权权限管理接口文档.md:260)；[OAuth2与标准协议差距清单.md:59](/C:/dev_projects/UserCenterV1/docs/oauth/OAuth2与标准协议差距清单.md:59)；4 份归档文档中的 Redis 示例，其中 [PAT落地核对报告.md:156](/C:/dev_projects/UserCenterV1/docs/archive/PAT落地核对报告.md:156)（其余行：317、320、353） 仅命中此类内容 | 现有令牌、授权管理和 PAT 历史迁移说明 | 文档不能宣称本轮已经改 Redis 存储名称。 |
| `UC_ACCESS_TOKEN` | 本轮保留；未来目标可为 `TCL_ACCESS_TOKEN`，须先核对前端 | [OAuth游戏数据接口文档.md:42](/C:/dev_projects/UserCenterV1/docs/oauth/OAuth游戏数据接口文档.md:42) | OAuth 游戏资源及外部 BackEndV3 的存量兑换流程，外部路径未核实 | 文档描述为前端本地令牌名称；本仓库未包含对应实现，直接改文档并要求前端切换可能导致读不到既有登录状态。 |
| `uc.oauth.pkce` | 本轮保留；未来目标可为 `tcl.oauth.pkce`，须兼容在途流程 | [无后端Web应用OAuth2接入文档.md:82](/C:/dev_projects/UserCenterV1/docs/oauth/无后端Web应用OAuth2接入文档.md:82)，`sessionStorage.setItem` 示例 | `GET /oauth2/authorize` → 客户端回调页 → `POST /oauth2/token` | 属于浏览器 PKCE 状态的存储键，不是普通占位符；只改单端读写会导致在途授权找不到 code_verifier / state。 |
| `tlc_pat`、`TlcPat`、`TlcPatMapper` 及其引用 | 保持原样 | [TlcPat.java:26](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/po/TlcPat.java:26)；[TlcPatMapper.java](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/mapper/TlcPatMapper.java)；[schema-user.sql:269](/C:/dev_projects/UserCenterV1/src/main/resources/db/schema-user.sql:269)（其余行：283、284、285）；[reset.sql:54](/C:/dev_projects/UserCenterV1/src/main/resources/db/reset.sql:54) | `/user/pat` 管理接口、`/oauth2/pat` 系列、`GET /pat/ak-accounts/operators` | 原名称是 `tlc`，不属于本次 `uc` 替换；表和数据库结构不改。 |
| `tlc_pat_` | 保持原样 | [PatUtil.java:21](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/PatUtil.java:21) 及格式校验、签发、测试、文档引用 | `POST /user/pat`、`POST /oauth2/pat`、`GET /pat/ak-accounts/operators` | 已签发 PAT 的格式标识；不改变凭证值和旧凭证识别规则。 |
| `tlc:pat:`、`tlc:uid:pat:`、`tlc:pat:miss:`、`tlc:pat:lock:` | 保持原样 | [RedisKeyUtil.java:82](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:82)（其余行：85、88、91） | PAT 预留缓存和锁命名；当前是否使用以具体调用为准 | 同属 Redis 及现有 `tlc` 命名，不纳入此次替换。 |
| 已签发会话 token、access_token、refresh_token、ticket，以及现有 `clientId` | 值与格式保持原样 | 当前会话和令牌数据；客户端、配置、登录日志的关联数据 | A—E 组及其他令牌流程 | 凭证值和客户端归属不改；长期会话/令牌不重新批量签发，短期 ticket 因旧键不再读取而可能失效，按已接受的直接切换方案重发。 |

本次扫描未发现需随 `uc/UC` 改名的 `uc_` 业务表、UC 命名 Java 类或包。项目的 `UserCenter`、`user_center`、`user-center`、`com.orange`、`orange-auth` 不属于所要求的缩写替换，不连带改名。配置仅核对了允许读取的 `application-test.yml`，其他 yml 未读取。

## 5. 受影响 API 完整列表

A—E 组列出请求头/参数改名的 85 个接口，F 组列出短期 Redis 前缀改名额外涉及的 9 个接口；第 4 节也列出与 A—E 重叠的 Redis 读写影响。以下按当前 Controller 映射核对，路径均保持不变。表内来源链接定位到路由注解；具体认证覆盖依据为 [WebConfig.java:58](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/interceptor/WebConfig.java:58) 及其后续注册逻辑、[OAuthTokenServiceImpl.java:148](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:148) 的共享解析方法。

### A. 用户会话接口（50 个）

| HTTP 方法 | API 路径 | 对应名称变化 | 涉及位置（路由声明） |
| --- | --- | --- | --- |
| GET | `/user/ak-accounts` | `UC-Token → TCL-Token` | [AkAccountController.java:69](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:69) |
| GET | `/user/ak-accounts/operators` | `UC-Token → TCL-Token` | [AkAccountController.java:85](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:85) |
| POST | `/user/ak-accounts/default/update` | `UC-Token → TCL-Token` | [AkAccountController.java:102](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:102) |
| DELETE | `/user/ak-accounts` | `UC-Token → TCL-Token` | [AkAccountController.java:118](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:118) |
| GET | `/user/ak-accounts/items` | `UC-Token → TCL-Token` | [AkAccountController.java:131](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:131) |
| GET | `/user/ak-accounts/skins` | `UC-Token → TCL-Token` | [AkAccountController.java:148](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:148) |
| POST | `/user/ak-accounts/operators/save` | `UC-Token → TCL-Token` | [AkAccountController.java:163](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:163) |
| POST | `/user/ak-accounts/manual-operators/save` | `UC-Token → TCL-Token` | [AkAccountController.java:175](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AkAccountController.java:175) |
| POST | `/auth/logout` | `UC-Token → TCL-Token` | [AuthController.java:130](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:130) |
| GET | `/user/ef-accounts` | `UC-Token → TCL-Token` | [EfPlayerDataController.java:57](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/EfPlayerDataController.java:57) |
| GET | `/user/ef-accounts/player-data` | `UC-Token → TCL-Token` | [EfPlayerDataController.java:70](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/EfPlayerDataController.java:70) |
| POST | `/user/ef-accounts/player-data/save` | `UC-Token → TCL-Token` | [EfPlayerDataController.java:87](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/EfPlayerDataController.java:87) |
| POST | `/user/ef-pool-records/create-task` | `UC-Token → TCL-Token` | [EfPoolRecordUserController.java:55](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/EfPoolRecordUserController.java:55) |
| GET | `/user/ef-pool-records/check-task` | `UC-Token → TCL-Token` | [EfPoolRecordUserController.java:67](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/EfPoolRecordUserController.java:67) |
| GET | `/user/ef-pool-records/accounts` | `UC-Token → TCL-Token` | [EfPoolRecordUserController.java:78](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/EfPoolRecordUserController.java:78) |
| GET | `/user/ef-pool-records/record` | `UC-Token → TCL-Token` | [EfPoolRecordUserController.java:90](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/EfPoolRecordUserController.java:90) |
| GET | `/user/oauth/client/scopes` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:58](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:58) |
| POST | `/user/oauth/client/register` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:72](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:72) |
| GET | `/user/oauth/client/list` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:83](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:83) |
| GET | `/user/oauth/client/{clientId}` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:95](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:95) |
| POST | `/user/oauth/client/{clientId}/update` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:108](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:108) |
| POST | `/user/oauth/client/{clientId}/rotate-secret` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:122](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:122) |
| POST | `/user/oauth/client/{clientId}/disable` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:134](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:134) |
| POST | `/user/oauth/client/{clientId}/enable` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:147](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:147) |
| POST | `/user/oauth/client/{clientId}/delete` | `UC-Token → TCL-Token` | [OAuthClientAdminController.java:160](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:160) |
| POST | `/user/config/save` | `UC-Token → TCL-Token` | [UserConfigController.java:51](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserConfigController.java:51) |
| POST | `/user/config/save-if-match` | `UC-Token → TCL-Token` | [UserConfigController.java:63](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserConfigController.java:63) |
| GET | `/user/config/quota` | `UC-Token → TCL-Token` | [UserConfigController.java:74](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserConfigController.java:74) |
| GET | `/user/config/list` | `UC-Token → TCL-Token` | [UserConfigController.java:88](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserConfigController.java:88) |
| POST | `/user/config/delete` | `UC-Token → TCL-Token` | [UserConfigController.java:103](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserConfigController.java:103) |
| GET | `/user/profile` | `UC-Token → TCL-Token` | [UserController.java:64](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:64) |
| POST | `/user/profile/update` | `UC-Token → TCL-Token` | [UserController.java:76](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:76) |
| POST | `/user/password` | `UC-Token → TCL-Token` | [UserController.java:89](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:89) |
| POST | `/user/email/bind` | `UC-Token → TCL-Token` | [UserController.java:102](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:102) |
| POST | `/user/email/send-change-code` | `UC-Token → TCL-Token` | [UserController.java:115](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:115) |
| POST | `/user/email/change` | `UC-Token → TCL-Token` | [UserController.java:128](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:128) |
| GET | `/user/sessions` | `UC-Token → TCL-Token` | [UserController.java:140](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:140) |
| GET | `/user/oauth/grants` | `UC-Token → TCL-Token` | [UserController.java:151](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:151) |
| POST | `/user/oauth/grants/revoke` | `UC-Token → TCL-Token` | [UserController.java:163](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:163) |
| GET | `/user/oauth/grants/scopes` | `UC-Token → TCL-Token` | [UserController.java:178](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:178) |
| POST | `/user/oauth/grants/scopes/grant` | `UC-Token → TCL-Token` | [UserController.java:193](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:193) |
| POST | `/user/oauth/grants/scopes/revoke` | `UC-Token → TCL-Token` | [UserController.java:209](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:209) |
| POST | `/user/sessions/{token}/kick` | `UC-Token → TCL-Token` | [UserController.java:223](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserController.java:223) |
| POST | `/user/pat` | `UC-Token → TCL-Token` | [UserPatController.java:53](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserPatController.java:53) |
| GET | `/user/pat/list` | `UC-Token → TCL-Token` | [UserPatController.java:64](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserPatController.java:64) |
| POST | `/user/pat/revoke` | `UC-Token → TCL-Token` | [UserPatController.java:76](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserPatController.java:76) |
| POST | `/user/pat/revoke-all` | `UC-Token → TCL-Token` | [UserPatController.java:88](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserPatController.java:88) |
| POST | `/user/schedule/save` | `UC-Token → TCL-Token` | [UserScheduleController.java:34](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserScheduleController.java:34) |
| GET | `/user/schedule/list` | `UC-Token → TCL-Token` | [UserScheduleController.java:40](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserScheduleController.java:40) |
| POST | `/user/schedule/delete` | `UC-Token → TCL-Token` | [UserScheduleController.java:46](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/UserScheduleController.java:46) |

### B. OAuth access_token 资源接口（27 个）

| HTTP 方法 | API 路径 | 对应名称变化 | 涉及位置（路由声明） |
| --- | --- | --- | --- |
| GET | `/oauth2/ak-accounts` | `UC-Token → TCL-Token` | [OAuthAkAccountController.java:61](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthAkAccountController.java:61) |
| GET | `/oauth2/ak-accounts/operators` | `UC-Token → TCL-Token` | [OAuthAkAccountController.java:75](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthAkAccountController.java:75) |
| POST | `/oauth2/ak-accounts/default/update` | `UC-Token → TCL-Token` | [OAuthAkAccountController.java:93](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthAkAccountController.java:93) |
| DELETE | `/oauth2/ak-accounts` | `UC-Token → TCL-Token` | [OAuthAkAccountController.java:110](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthAkAccountController.java:110) |
| GET | `/oauth2/ak-accounts/items` | `UC-Token → TCL-Token` | [OAuthAkAccountController.java:124](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthAkAccountController.java:124) |
| POST | `/oauth2/ak-accounts/operators/save` | `UC-Token → TCL-Token` | [OAuthAkAccountController.java:140](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthAkAccountController.java:140) |
| POST | `/oauth2/ak-accounts/manual-operators/save` | `UC-Token → TCL-Token` | [OAuthAkAccountController.java:153](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthAkAccountController.java:153) |
| POST | `/oauth2/config/save` | `UC-Token → TCL-Token` | [OAuthConfigController.java:55](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthConfigController.java:55) |
| POST | `/oauth2/config/save-if-match` | `UC-Token → TCL-Token` | [OAuthConfigController.java:68](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthConfigController.java:68) |
| GET | `/oauth2/config/quota` | `UC-Token → TCL-Token` | [OAuthConfigController.java:80](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthConfigController.java:80) |
| GET | `/oauth2/config/list` | `UC-Token → TCL-Token` | [OAuthConfigController.java:95](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthConfigController.java:95) |
| POST | `/oauth2/config/delete` | `UC-Token → TCL-Token` | [OAuthConfigController.java:111](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthConfigController.java:111) |
| GET | `/oauth2/userinfo` | `UC-Token → TCL-Token` | [OAuthController.java:255](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:255) |
| GET | `/oauth2/ef-accounts` | `UC-Token → TCL-Token` | [OAuthEfPlayerDataController.java:60](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthEfPlayerDataController.java:60) |
| GET | `/oauth2/ef-accounts/player-data` | `UC-Token → TCL-Token` | [OAuthEfPlayerDataController.java:74](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthEfPlayerDataController.java:74) |
| POST | `/oauth2/ef-accounts/player-data/save` | `UC-Token → TCL-Token` | [OAuthEfPlayerDataController.java:92](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthEfPlayerDataController.java:92) |
| POST | `/oauth2/ef-pool-records/create-task` | `UC-Token → TCL-Token` | [OAuthEfPoolRecordController.java:59](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthEfPoolRecordController.java:59) |
| GET | `/oauth2/ef-pool-records/check-task` | `UC-Token → TCL-Token` | [OAuthEfPoolRecordController.java:72](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthEfPoolRecordController.java:72) |
| GET | `/oauth2/ef-pool-records/accounts` | `UC-Token → TCL-Token` | [OAuthEfPoolRecordController.java:84](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthEfPoolRecordController.java:84) |
| GET | `/oauth2/ef-pool-records/record` | `UC-Token → TCL-Token` | [OAuthEfPoolRecordController.java:97](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthEfPoolRecordController.java:97) |
| GET | `/oauth2/pat/list` | `UC-Token → TCL-Token` | [OAuthPatController.java:77](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthPatController.java:77) |
| POST | `/oauth2/pat/revoke` | `UC-Token → TCL-Token` | [OAuthPatController.java:92](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthPatController.java:92) |
| POST | `/oauth2/profile/nickname` | `UC-Token → TCL-Token` | [OAuthProfileController.java:53](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthProfileController.java:53) |
| POST | `/oauth2/profile/avatar` | `UC-Token → TCL-Token` | [OAuthProfileController.java:72](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthProfileController.java:72) |
| GET | `/oauth2/schedule/list` | `UC-Token → TCL-Token` | [OAuthScheduleController.java:46](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthScheduleController.java:46) |
| POST | `/oauth2/schedule/save` | `UC-Token → TCL-Token` | [OAuthScheduleController.java:59](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthScheduleController.java:59) |
| POST | `/oauth2/schedule/delete` | `UC-Token → TCL-Token` | [OAuthScheduleController.java:72](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthScheduleController.java:72) |

### C. OAuth 登录用户解析接口（5 个）

| HTTP 方法 | API 路径 | 对应名称变化 | 涉及位置（路由声明） |
| --- | --- | --- | --- |
| GET | `/oauth2/authorize` | `UC-Token → TCL-Token`；`uc_ticket → tcl_ticket` | [OAuthController.java:75](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:75) |
| POST | `/oauth2/authorize` | `UC-Token → TCL-Token`；`uc_ticket → tcl_ticket` | [OAuthController.java:94](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:94) |
| POST | `/oauth2/ticket` | `UC-Token → TCL-Token`；`uc_ticket → tcl_ticket` | [OAuthController.java:116](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:116) |
| GET | `/oauth2/consent/info` | `UC-Token → TCL-Token`；`uc_ticket → tcl_ticket` | [OAuthController.java:131](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:131) |
| POST | `/oauth2/consent` | `UC-Token → TCL-Token`；`uc_ticket → tcl_ticket` | [OAuthController.java:154](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:154) |

### D. PAT 消费接口（1 个）

| HTTP 方法 | API 路径 | 对应名称变化 | 涉及位置（路由声明） |
| --- | --- | --- | --- |
| GET | `/pat/ak-accounts/operators` | `UC-Token → TCL-Token` | [PatAkAccountController.java:68](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/pat/PatAkAccountController.java:68) |

### E. 登录、注册入口（2 个）

| HTTP 方法 | API 路径 | 对应名称变化 | 涉及位置（路由声明） |
| --- | --- | --- | --- |
| POST | `/auth/register` | `UC-Client-Id → TCL-Client-Id` | [AuthController.java:71](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:71) |
| POST | `/auth/login` | `UC-Client-Id → TCL-Client-Id` | [AuthController.java:88](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:88) |

### F. 短期 Redis 前缀替换额外涉及的接口（9 个）

这些接口不在 A—E 的 85 个请求头/参数接口内；它们是本次扩大 Redis 替换范围后新增的运行影响。与 A—E 重叠的验证码、邮箱操作、游戏保存及授权接口不重复计入本组。

| 现有名称 | 目标名称 | 涉及位置 | 受影响的 API 路径 |
| --- | --- | --- | --- |
| `uc:code:`、`uc:rate:` | `tcl:code:`、`tcl:rate:` | [AuthController.java:57](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:57)；EmailCodeServiceImpl 共用发送逻辑 | `POST /auth/send-code` |
| `uc:oauth:code:`、`uc:oauth:code:used:` | 对应 `tcl:` 前缀 | [OAuthController.java:212](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:212)；RedisOAuthTokenStore 46、51、57、58 行及 oauth-code-consume.lua | `POST /oauth2/token`（授权码兑换分支；refresh 分支长期前缀保留） |
| `uc:code:`、`uc:rate:` | `tcl:code:`、`tcl:rate:` | [AuthController.java:104](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:104)；AuthServiceImpl 250 行发送重设密码验证码 | `POST /auth/reset-code` |
| `uc:code:` | `tcl:code:` | [AuthController.java:117](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:117)；AuthServiceImpl 268 行校验验证码 | `POST /auth/reset-password` |
| `uc:oauth:direct:channel:`、`uc:rate:` | 对应 `tcl:` 前缀 | [OAuthLegacyLoginController.java:54](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:54)；AuthServiceImpl 394、506、509 行 | `POST /oauth2/direct-session` |
| `uc:oauth:direct:channel:`、`uc:oauth:direct:ticket:`、`uc:code:`、`uc:login:fail:`、`uc:lock:`、`uc:rate:` | 对应 `tcl:` 前缀 | [OAuthLegacyLoginController.java:78](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:78)；AuthServiceImpl 直连登录及共享验证码/登录失败处理 | `POST /oauth2/direct-login` |
| `uc:oauth:direct:channel:`、`uc:oauth:direct:ticket:`、`uc:code:`、`uc:rate:` | 对应 `tcl:` 前缀 | [OAuthLegacyLoginController.java:109](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:109)；AuthServiceImpl 直连注册及共享验证码/注册限流 | `POST /oauth2/direct-register` |
| `uc:oauth:direct:ticket:`、`uc:oauth:direct:used:` | 对应 `tcl:` 前缀 | [OAuthLegacyLoginController.java:140](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:140)；AuthServiceImpl 618、623、627 行 | `POST /oauth2/direct-user` |
| `uc:oauth:migrate:nonce:`、`uc:rate:` | 对应 `tcl:` 前缀 | [OAuthInternalController.java:55](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthInternalController.java:55)；OAuthMigrateServiceImpl 149、284、286、288 行 | `POST /oauth2/internal/migrate-token` |

### 不因请求头 / 参数改名而直接受影响的接口

以下接口不读取本轮变更的 UC 请求头或 uc_ticket 参数。其中 F 组已明确纳入短期 Redis 前缀改造，不能据此认为它们完全不受本轮工作影响：

| API 路径 | 当前认证 / 作用 | 本轮处理 |
| --- | --- | --- |
| `POST /oauth2/token` | 授权码或 refresh_token 交换 | 不改变请求参数；授权码及已用标记 Redis 前缀按第 4 节直接替换，refresh 分支的长期前缀保留 |
| `POST /oauth2/revoke` | 客户端身份及待撤销令牌 | 不改变调用契约 |
| `POST /oauth2/pat` | 请求体 refresh_token 签发 PAT | 不增加 TCL-Token 必填要求；与 B 组的 list/revoke 区分 |
| `POST /oauth2/internal/migrate-token` | Ed25519 验签及防重放 | UC 业务简称可改；nonce 与限流短期前缀按第 4 节直接替换，签名原文、kid、公钥映射、参数及长期映射保持不变 |
| `POST /oauth2/direct-session`、`POST /oauth2/direct-login`、`POST /oauth2/direct-register`、`POST /oauth2/direct-user` | 直连认证及短时凭证 | 业务说明可改；channel/ticket/used 及相关限流 Redis 前缀按第 4 节直接替换，客户端传递的 channel、ticket 值和认证字段保持不变 |
| `POST /auth/send-code`、`POST /auth/reset-code`、`POST /auth/reset-password` | 验证码及重设密码 | 不新增来源头要求；验证码、验证码发送限流等短期 Redis 前缀按第 4 节直接替换 |
| `/open/**`、`/skland/**`、`GET /` | 公开查询、森空岛相关操作及健康检查 | 当前未发现读取本轮 UC 请求头 / 参数的逻辑 |
| `GET /internal/pat-migration/run` | 现有独立历史迁移入口 | 不执行、不修改迁移流程；仅在清单中注明保留的 key 与测试关联 |

## 6. 实施核对项与部署约定

源码及文档已实施；以下包括已核对的实现要求，以及部署时仍需遵守的切换约定。

- 新旧令牌请求头、来源客户端头及票据参数名仍按第 2 节兼容；原有 clientId、会话/OAuth/PAT 认证边界不变。
- 14 个短期 Redis 常量和示例注释明确从 uc: 改为 tcl:；所有现有读写、删除、限流和 Lua 传参通过原构建方法取得新键，不保留旧短期键回退。
- 新签发验证码、票据、授权码、确认单可以按原有流程使用；旧短期数据不可用及重新登录/重新授权属于已接受的影响。
- 在空的新命名空间中，失败计数和限流重新累计，旧锁与旧 nonce 不再生效；保留原时窗和 TTL，验证新产生的状态仍按现有逻辑工作。
- 9 个关联前缀、会话扫描表达式、长期 OAuth Lua 入参及断言保持原样；refresh:next 虽为 60 秒也不改名。
- 现有授权码消费 Lua 无需修改正文及 KEYS/ARGV 结构；不新增 Redis legacy 方法、兼容层、双读双写、迁移脚本或清理命令。
- 半夜统一停旧启动新版本，不采用新旧版本同时提供服务的滚动切换；接受回退时短期命名空间再次切换的影响。
- 前端 UC_ACCESS_TOKEN、uc.oauth.pkce、现有 tlc 命名、数据库结构和存量客户端标识不纳入此次变更。

本次已修改源码、业务注释、接口文档、AGENTS.md 和 application-test.yml 中一处注释；没有修改其他资源 yml、数据库结构、Lua 正文或线上 Redis，也没有部署。测试记录及真实数据库统计写入情况见文末。

### 本次验证记录（2026-11-19）

- Java 17 / Maven 3.9.12 离线编译成功。
- 定向测试：73 项通过，0 失败、0 错误、0 跳过。
- 完整测试：322 项中 315 项通过、7 项跳过，0 失败、0 错误；跳过的是未显式启用的 Redis 集成测试，因此没有验证真实 Redis 并发行为。
- 前缀差异核对：恰好 14 个常量由 uc: 改为 tcl:；另外 9 个 uc: 和 4 个 tlc: 常量与执行前完全一致。Lua 正文、TTL/消费协议及数据库结构没有修改。
- git diff --check 通过。没有提交、打包部署或执行 Redis 前缀迁移/清理命令。

**测试副作用说明：**运行完整测试前没有检查出仓库中两个默认参与执行的真实集成测试。AkOperatorStateStatisticsIntegrationTest 使用现有 dev 配置连接数据库，执行了干员数据扫描，并通过事务清空后重写 ak_operator_state_statistics 统计结果表；测试随后回读校验通过。TencentCiTextAuditIntegrationTest 调用了真实文本审核接口。这两项不是本次改名所需的验证，属于执行范围外的副作用；不能据本记录宣称本次完全没有数据库写入。当前没有执行额外回滚或数据恢复操作。

后续常规回归应显式排除上述两个真实集成测试及本地密钥生成工具测试，避免写入统计库、调用外部服务或在日志打印本地工具中的密钥。真实 Redis 集成测试继续保持默认关闭。例如在本机配置好 Maven 后执行：

```powershell
mvn '-Dtest=*,!AkOperatorStateStatisticsIntegrationTest,!TencentCiTextAuditIntegrationTest,!OAuthClientSecretGeneratorTest' test
```
