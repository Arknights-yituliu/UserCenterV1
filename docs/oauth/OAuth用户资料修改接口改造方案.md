# OAuth 用户资料修改接口改造方案

> 状态：待评审　|　编写日期：2026-11-06　|　范围：OAuth 侧新增用户名 / 昵称 / 头像 / 邮箱修改能力

## 1. 背景与目标

当前 OAuth 接入方只能通过 `GET /oauth2/userinfo` 读取用户基础资料（`uid`、`userName`、`nickname`、`avatar`），无法在授权范围内修改资料。本方案为 OAuth 侧补齐四类修改能力：

1. 修改昵称
2. 修改用户名
3. 修改头像
4. 修改邮箱（含未绑定用户的绑定流程与已绑定用户的换绑流程）

设计原则：**能复用用户侧既有逻辑的一律复用**，OAuth 侧只做「令牌身份注入 + scope 校验 + 参数校验」，不重复实现业务规则。

## 2. 现状调研

### 2.1 用户侧可复用能力

| 能力 | 用户侧接口 | 服务方法 | 能否直接复用 |
| --- | --- | --- | --- |
| 修改昵称 | `POST /user/profile/update` | `UserService.updateProfile` | 能 |
| 修改头像 | `POST /user/profile/update` | `UserService.updateProfile` | 能 |
| 绑定邮箱 | `POST /user/email/bind` | `UserService.bindEmail` | 能 |
| 发送换绑验证码 | `POST /user/email/send-change-code` | `UserService.sendChangeEmailCode` | 能（仅依赖 `uid` 与 IP） |
| 换绑邮箱 | `POST /user/email/change` | `UserService.changeEmail` | 能 |
| 修改用户名 | **不存在** | **不存在** | 不能，需新增 |

关键点：

- `updateProfile` 的入参为 `UpdateProfileRequest`（`nickname` ≤64、`avatar` ≤512，均可空）；`nickname` 为空或空白时跳过，`avatar` 非 null 时写入（空串等价于清除）。
- 邮箱相关方法均只依赖 `uid`，不依赖用户会话，因此可直接被 OAuth 侧调用；验证码发送仍由 `EmailCodeService` 做 IP 与邮箱双维度限流。
- 用户名的创建规则见 `RegisterRequest`：`^[A-Za-z0-9_]{3,20}$`，唯一性由 `user_info.user_name` 保证；登录时用户名与邮箱都是可选账号。
- `UserInfoMapper` 已提供按 `user_name` 查询的方法，新增唯一性校验无需改 Mapper。

### 2.2 OAuth 侧现状

- `OAuthAuthInterceptor` 负责解析 access_token 并写入 `UserContext`（`uid`、`clientId`、`scope`）。
- 现有受保护路径在 `WebConfig` 中逐个枚举，新增端点必须同步注册，否则拦截器不生效、`UserContext` 为空。
- `OAuthScope.USER_PROFILE`（`user.profile`）已定义且为敏感 scope，但**当前没有任何接口使用它**；其文案「读取并修改你的个人资料」与本次新增能力方向一致。
- 元范围 `all` 会被 `UserContext.requireScope` 视为已获得全部 scope，无需额外处理。

## 3. 接口设计

### 3.1 接口清单

| 方法 | 路径 | 所需 scope | 说明 |
| --- | --- | --- | --- |
| POST | `/oauth2/profile/username` | `user.profile` | 修改用户名 |
| POST | `/oauth2/profile/nickname` | `user.profile` | 修改昵称 |
| POST | `/oauth2/profile/avatar` | `user.profile` | 修改头像 |
| POST | `/oauth2/email/bind` | `user.profile` | 未绑定邮箱时绑定新邮箱 |
| POST | `/oauth2/email/send-change-code` | `user.profile` | 向当前绑定邮箱发送换绑验证码 |
| POST | `/oauth2/email/change` | `user.profile` | 换绑邮箱（旧邮箱 + 新邮箱双验证码） |

拆分理由：四项能力分别对应独立的业务动作与错误语义，接入方按需申请；邮箱绑定/换绑是不同前置条件的两条路径，保留用户侧原有的两条入口，避免把「未绑定」和「已绑定」的判断揉进一个接口。

### 3.2 通用约定

- 认证：`Authorization: Bearer <access_token>`；缺失或无效返回 `80001`。
- 授权：缺少 `user.profile` 返回 `80008`，`msg` 形如 `缺少授权范围: user.profile`。
- 身份：`uid` 一律取自令牌，请求参数不可指定。
- 响应：统一 `{"code":200,"msg":"操作成功","data":null}`。
- 参数校验失败（Bean Validation）按项目现有全局异常处理返回 `10002`。

### 3.3 接口详情

#### 3.3.1 修改用户名

```http
POST /oauth2/profile/username
Content-Type: application/json
Authorization: Bearer <access_token>

{"userName": "new_name"}
```

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `userName` | string | 是 | 非空；仅字母、数字、下划线；3-20 位（与注册规则一致） |

处理规则：

1. 与当前用户名相同 → 直接成功（幂等，不重复写库）。
2. 被其他用户占用 → `20007`（`USERNAME_ALREADY_EXISTS`）。
3. 更新后**不联动昵称**（注册时「昵称缺省取用户名」只在创建时生效，避免覆盖用户已自定义的昵称）。
4. 旧用户名立即失去登录能力；当前会话只存 `uid`，不受影响，无需踢会话。

#### 3.3.2 修改昵称

```http
POST /oauth2/profile/nickname
Content-Type: application/json
Authorization: Bearer <access_token>

{"nickname": "新昵称"}
```

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `nickname` | string | 是 | 非空（`@NotBlank`）；支持中文；≤64 字符 |

说明：复用 `UpdateProfileRequest` 的既有校验口径（≤64，无字符集限制），**昵称支持中文**（中文、英文、数字及其他字符均可提交）。注册接口额外限制 24 位且仅中英文数字，本次**不收紧** OAuth 修改规则，保持与用户侧修改行为一致；如需统一收紧，应作为独立改造同时调整用户侧。

#### 3.3.3 修改头像

```http
POST /oauth2/profile/avatar
Content-Type: application/json
Authorization: Bearer <access_token>

{"avatar": "https://example.com/a.png"}
```

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `avatar` | string | 是 | 非 null；≤512 字符；传空串表示清除头像 |

#### 3.3.4 绑定邮箱（未绑定用户）

```http
POST /oauth2/email/bind
Content-Type: application/json
Authorization: Bearer <access_token>

{"email": "new@example.com", "code": "123456"}
```

- 请求体直接复用 `BindEmailRequest`。
- 已绑定邮箱时返回 `10003`（`ILLEGAL_OPERATION`，提示改用换绑）。
- 新邮箱已被占用返回 `20003`。
- 验证码错误/过期返回 `30001` / `30002`。
- 新邮箱验证码通过**公开接口** `POST /auth/send-code` 发送（`usage` 建议传 `change_email`，非 `register`，避免触发注册用途的邮箱占用前置校验）。

#### 3.3.5 发送换绑验证码

```http
POST /oauth2/email/send-change-code
Authorization: Bearer <access_token>
```

- 无请求体；服务端按令牌 `uid` 取当前绑定邮箱并发送验证码，**不向接入方返回邮箱明文**。
- 未绑定邮箱返回 `20006`（`EMAIL_NOT_BOUND`）。
- 发送过于频繁返回 `30003`。
- 新邮箱的验证码仍由接入方调用公开接口 `POST /auth/send-code` 获取。

#### 3.3.6 换绑邮箱（已绑定用户）

```http
POST /oauth2/email/change
Content-Type: application/json
Authorization: Bearer <access_token>

{"oldCode": "111111", "newEmail": "new@example.com", "newCode": "222222"}
```

- 请求体直接复用 `ChangeEmailRequest`。
- 未绑定邮箱返回 `20006`；新旧邮箱相同返回 `10001`；新邮箱被占用返回 `20003`；验证码错误/过期返回 `30001` / `30002`。
- 校验顺序沿用用户侧：先校验旧邮箱验证码 → 再查新邮箱占用 → 最后校验新邮箱验证码。
- 换绑成功后**不踢出其他会话**（与用户侧现有行为一致）。

## 4. 代码改动清单

### 4.1 新增 DTO（`com.orange.entity.dto.oauth`）

| 文件 | 字段与约束 |
| --- | --- |
| `UpdateUserNameRequest.java` | `@NotBlank` + `@Pattern("^[A-Za-z0-9_]{3,20}$")` |
| `UpdateNicknameRequest.java` | `@NotBlank` + `@Size(max=64)` |
| `UpdateAvatarRequest.java` | `@NotNull` + `@Size(max=512)` |

邮箱相关 DTO 不新建，直接复用 `com.orange.entity.dto.user.BindEmailRequest` 与 `ChangeEmailRequest`。

### 4.2 Service 改动

`UserService` 新增：

```java
/**
 * 修改用户名（唯一性校验，与注册规则一致；与当前同名视为成功）
 *
 * @param uid      用户 uid
 * @param userName 新用户名（格式由调用方 DTO 校验）
 */
void updateUserName(Long uid, String userName);
```

`UserServiceImpl` 实现要点：

1. `getByUid(uid)` 取用户，不存在抛 `USER_NOT_FOUND`。
2. 与当前用户名相同 → 直接返回。
3. `selectCount(eq(UserInfo::getUserName, userName)) > 0` → 抛 `USERNAME_ALREADY_EXISTS`。
4. 写入 `userName` 后 `updateById`。

其余修改全部复用现有方法：`updateProfile`、`bindEmail`、`sendChangeEmailCode`、`changeEmail`。

### 4.3 新增 Controller

| 文件 | 类级路径 | 方法 |
| --- | --- | --- |
| `controller/oauth/OAuthProfileController.java` | `/oauth2/profile` | `username` / `nickname` / `avatar` |
| `controller/oauth/OAuthEmailController.java` | `/oauth2/email` | `bind` / `sendChangeCode` / `change` |

统一模式（与 `OAuthAkAccountController` 一致）：

```java
UserContext.requireScope(OAuthScope.USER_PROFILE);
userService.updateProfile(UserContext.requireUid(), request);
```

邮箱相关接口中的 IP 通过 `RequestUtil.getIp(httpRequest)` 传入 `sendChangeEmailCode`。日志使用 `LogUtil.debug`，邮箱不打印明文。

### 4.4 拦截器注册

`WebConfig.addInterceptors` 的 OAuth 路径列表中追加：

```java
"/oauth2/profile/**", "/oauth2/email/**"
```

说明：`/oauth2/email/send-change-code` 也在 `/**` 覆盖范围内；新增路径若遗漏注册，`UserContext` 为空会导致 `80001`。

### 4.5 scope 文案调整

`OAuthScope.USER_PROFILE` 的描述由「读取并修改你的个人资料」调整为「读取并修改你的个人资料、用户名与邮箱」，保证授权确认页展示与实际能力一致。该枚举是 scope 元数据唯一来源，调整后确认页与 scope 元数据接口自动同步；已授权用户的令牌 scope 不变，无需重新授权。

## 5. 邮箱修改完整流程

**未绑定邮箱用户**

1. 接入方调用公开接口 `POST /auth/send-code`（`email` = 新邮箱，`usage` = `change_email`）。
2. 用户收取验证码并回填给接入方。
3. 接入方调用 `POST /oauth2/email/bind`。

**已绑定邮箱用户**

1. 接入方调用 `POST /oauth2/email/send-change-code`，验证码发到当前绑定邮箱。
2. 接入方调用 `POST /auth/send-code`，验证码发到新邮箱。
3. 用户回填两个验证码。
4. 接入方调用 `POST /oauth2/email/change`。

> 接入方无需（也无法）获得当前邮箱明文：`/oauth2/userinfo` 不返回邮箱，换绑验证码由服务端按 `uid` 直接发送。

## 6. 错误码汇总

| code | 含义 | 触发接口 |
| --- | --- | --- |
| `10001` | 参数错误（如新旧邮箱相同） | change |
| `10002` | 参数校验失败 | 全部 |
| `10003` | 非法操作（已绑定邮箱仍调 bind） | bind |
| `20003` | 邮箱已被注册 | bind / change |
| `20006` | 账号未绑定邮箱 | send-change-code / change |
| `20007` | 用户名已被注册 | username |
| `30001` | 验证码错误 | bind / change |
| `30002` | 验证码已过期 | bind / change |
| `30003` | 验证码发送过于频繁 | send-change-code |
| `80001` | 未登录 / 令牌无效 | 全部 |
| `80008` | 缺少 `user.profile` | 全部 |

## 7. 风险与待确认项

1. **账号凭证变更权限过大**：`user.profile` 授权后，三方应用可修改用户的登录用户名与登录邮箱。当前方案未要求旧密码或二次验证码确认（用户名修改完全无二次校验），存在应用恶意接管账号登录标识的风险。如需收紧，建议：
   - 用户名修改要求邮箱验证码或旧密码；
   - 邮箱修改已含双验证码，风险相对可控。
   
   本方案默认按需求实现，不做二次确认，待评审确认是否需要加强。
2. **邮箱变更不踢会话**：沿用用户侧现有行为；如安全要求提升，应在用户侧统一调整，而不是只在 OAuth 侧加逻辑。
3. **昵称规则不一致**：OAuth 修改支持中文且允许 ≤64 字符任意内容，注册限制 24 位中英文数字。本方案不收紧，避免与用户侧修改行为分裂。
4. **无邮箱明文回显**：OAuth 侧无法展示当前邮箱，接入方 UI 只能提示「验证码已发送至当前绑定邮箱」。

## 8. 文档同步

| 文档 | 改动 |
| --- | --- |
| `docs/API.md` | 「OAuth access_token 鉴权接口」章节新增资料修改接口表 |
| `docs/oauth/OAuth2客户端管理文档.md` | `user.profile` 说明补充「修改用户名、昵称、头像、邮箱」 |
| 新增 `docs/oauth/OAuth用户资料接口文档.md` | 面向接入方，包含认证、scope、用户信息与资料修改接口、错误码；已上线部分先落文档，未上线接口在文末单列 |

## 9. 测试要点

1. 缺少 `user.profile` 时六个接口均返回 `80008`；`all` 令牌全部放行。
2. 未携带/无效 access_token 返回 `80001`；令牌 `uid` 不可被请求参数覆盖。
3. 用户名：格式非法 `10002`、重复 `20007`、与当前同名幂等成功。
4. 昵称/头像：超长 `10002`；头像空串可清除；昵称为空白 `10002`。
5. 邮箱：已绑定调 bind `10003`、未绑定调 change/send-change-code `20006`、新旧邮箱相同 `10001`、邮箱占用 `20003`、验证码错误/过期 `30001`/`30002`。
6. 换绑验证码只发往当前绑定邮箱，响应与日志均不含邮箱明文。
7. 修改后 `GET /oauth2/userinfo` 能读到新的 `userName` / `nickname` / `avatar`。
8. 用户名修改后旧用户名无法登录、新用户名可登录；现有会话仍有效。
