# OAuth 用户资料接口

本文档面向 OAuth 接入方，说明通过 OAuth access_token 读取用户基础资料、修改昵称与头像的接口。用户身份由 access_token 决定，请求参数不能指定或覆盖它。

本文档与用户侧《用户资料接口》为同一份业务的两侧入口，差异见 [第 6 节](#6-与用户侧的差异)。

## 1. 通用约定

### 1.1 认证

所有接口都需要携带有效的 OAuth access_token，二选一：

```http
Authorization: Bearer <access_token>
```

```http
UC-Token: <access_token>
```

服务端从令牌解析出用户 `uid`、OAuth 客户端 `client_id` 与已授权的 `scope` 并注入请求上下文。令牌缺失、无效或已过期时响应 `code=80001`。

### 1.2 授权范围

| scope | 可访问接口 |
| --- | --- |
| `user.read` | 获取用户信息 |
| `user.profile` | 修改昵称、修改头像 |

缺少所需 scope 时响应 `code=80008`，`msg` 形如 `缺少授权范围: user.read`。客户端必须在注册时登记相应 scope，并在授权请求中申请它。

被授予元范围 `all` 的令牌（仅管理员手工写库授予的机密客户端）视为已获得全部范围。

### 1.3 响应格式与错误返回

成功和业务错误均使用统一 JSON 结构：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | integer | 业务状态码，`200` 表示成功 |
| `msg` | string | 结果说明 |
| `data` | object / null | 响应数据 |

两类错误的 HTTP 状态不同，调用方需同时判断 HTTP 状态与响应体 `code`：

| 错误类型 | HTTP 状态 | `code` |
| --- | --- | --- |
| 请求内容不合法（字段缺失、超长、格式错误） | `400` | `10002` |
| 业务失败（令牌无效、缺少 scope、用户不存在） | `200` | `80001` / `80008` / `20001` |

## 2. 获取用户信息

```http
GET /oauth2/userinfo
Authorization: Bearer <access_token>
```

所需 scope：`user.read`。

响应示例：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "uid": 1000000000000000001,
    "userName": "alice",
    "nickname": "爱丽丝",
    "avatar": "https://cdn.example.com/avatar/1.png"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `uid` | integer(int64) | 用户 uid。雪花 ID，可能超出 JavaScript 安全整数范围，前端请按字符串保存或使用 BigInt |
| `userName` | string | 用户名，兼容旧系统迁移用户，可能为 `null` |
| `nickname` | string | 全局昵称，可能为 `null` |
| `avatar` | string | 头像地址，可能为 `null` |

说明：

- 本接口只返回展示所需的基础资料，**不返回邮箱、账号状态、注册时间、最后登录时间**。
- 无自有账户体系的接入方可直接以 `uid` 作为登录账号标识，用 `nickname` / `avatar` 做展示。

## 3. 修改昵称

```http
POST /oauth2/profile/nickname
Content-Type: application/json
Authorization: Bearer <access_token>

{"nickname": "新昵称"}
```

所需 scope：`user.profile`。

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `nickname` | string | 是 | 非空；支持中文；最长 64 字符 |

成功响应：

```json
{"code": 200, "msg": "操作成功", "data": null}
```

说明：

- 昵称不限制字符集，中文、英文、数字及其他字符均可提交。
- 修改昵称**不会**同步修改用户名或登录账号。
- 写入后立即生效，再次调用 `GET /oauth2/userinfo` 可读到新昵称。

## 4. 修改头像

```http
POST /oauth2/profile/avatar
Content-Type: application/json
Authorization: Bearer <access_token>

{"avatar": "https://cdn.example.com/avatar/new.png"}
```

所需 scope：`user.profile`。

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `avatar` | string | 是 | 非 `null`；最长 512 字符；传空串表示清除头像 |

成功响应：

```json
{"code": 200, "msg": "操作成功", "data": null}
```

说明：

- 字段必须显式传值，传 `null` 会被参数校验拒绝（`10002`）。
- 传空串 `""` 表示清除头像，清除后 `userinfo` 中 `avatar` 为 `null`。

## 5. 错误码

| code | 含义 | 说明 |
| --- | --- | --- |
| `10002` | 参数校验失败 | 昵称为空/超长、`avatar` 为 `null`/超长，HTTP 400 |
| `20001` | 用户不存在 | 令牌对应用户已被删除 |
| `80001` | 未登录或令牌已失效 | 未携带 access_token、令牌无效或过期 |
| `80008` | 无权限操作 | 令牌缺少 `user.read` / `user.profile`，`msg` 会指明缺失的 scope |

## 6. 与用户侧的差异

| 维度 | 本文档（OAuth 侧） | 用户侧 |
| --- | --- | --- |
| 路径 | `/oauth2/userinfo`、`/oauth2/profile/**` | `/user/profile`、`/user/profile/update` |
| 凭证 | OAuth access_token | 用户会话 token |
| 授权范围 | `user.read` / `user.profile` | 不需要 scope |
| 身份来源 | 令牌中的 `uid` | 会话中的 `uid` |
| 可修改字段 | 昵称、头像（本次提供） | 昵称、头像 |
| 返回字段 | 资料聚合接口只返回 `uid`/`userName`/`nickname`/`avatar` | 返回脱敏邮箱、账号状态、注册与最后登录时间 |

用户侧的「修改个人资料」是一个合并接口（`POST /user/profile/update`，昵称与头像可同时提交），OAuth 侧按字段拆分为独立接口，便于接入方按需申请权限与定位错误；底层写库逻辑完全复用，不会出现两套规则。

## 7. 尚未提供的接口

以下能力已列入改造规划，**当前尚未上线**，请勿按本文档调用：

| 规划接口 | 说明 |
| --- | --- |
| `POST /oauth2/profile/username` | 修改用户名（登录账号之一），需要 `user.profile` |
| `POST /oauth2/email/bind` | 未绑定用户绑定邮箱 |
| `POST /oauth2/email/send-change-code` | 向当前绑定邮箱发送换绑验证码 |
| `POST /oauth2/email/change` | 换绑邮箱（旧邮箱 + 新邮箱双验证码） |

完整设计见《OAuth 用户资料修改接口改造方案》。

## 8. 调用示例

```bash
# 1. 获取用户信息
curl -H "Authorization: Bearer <access_token>" \
  "https://<uc-host>/oauth2/userinfo"

# 2. 修改昵称（支持中文）
curl -X POST "https://<uc-host>/oauth2/profile/nickname" \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"nickname":"新昵称"}'

# 3. 修改头像（空串表示清除）
curl -X POST "https://<uc-host>/oauth2/profile/avatar" \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{"avatar":"https://cdn.example.com/avatar/new.png"}'
```
