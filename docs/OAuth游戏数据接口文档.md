# OAuth 游戏数据接口

本文档面向 OAuth 接入方，说明通过 OAuth access_token 访问排班表、游戏账号和干员数据的接口。接口复用用户侧既有服务逻辑；用户身份与 OAuth 客户端均由 access_token 决定，请求参数不能指定或覆盖它们。

## 1. 通用约定

### 1.1 认证

所有接口都需要携带有效的 OAuth access_token：

```http
Authorization: Bearer <access_token>
```

服务端从令牌解析出用户 `uid`、OAuth 客户端 `client_id` 与已授权的 `scope` 并注入请求上下文，接入方不能通过请求参数指定这两个值。

令牌缺失、无效或已过期时响应 `code=80001`。

### 1.2 授权范围

| scope | 可访问接口 |
| --- | --- |
| `gama-data.read` | 读取排班表、已绑定游戏账号和干员数据 |
| `gama-data.write` | 保存游戏账号角色信息和干员数据 |

缺少所需 scope 时响应 `code=80008`，`msg` 形如 `缺少授权范围: gama-data.read`。客户端必须在注册时登记相应 scope，并在授权请求中申请它。

OAuth Token 仍同时绑定用户与客户端，用于客户端认证和 scope 校验；游戏账号绑定关系只按 Token 中的用户 `uid` 校验，不按 `client_id` 拆分。

### 1.3 令牌来源

本文档的接口只要求一个有效 access_token，来源不限：

| 场景 | 获取方式 |
| --- | --- |
| 后端服务 / BFF（能安全保存 client_secret） | 授权码 + PKCE，由业务后端换码，见《[加密客户端 OAuth2 接入文档](./加密客户端OAuth2接入文档.md)》 |
| 无后端 Web 应用（公共客户端） | 授权码 + PKCE，浏览器直接换码，见《[无后端 Web 应用 OAuth2 接入文档](./无后端Web应用OAuth2接入文档.md)》 |
| 存量 BackEndV3 会话 | 由前端在本地无 `UC_ACCESS_TOKEN` 时主动调 BackEndV3 兑换接口取得，凭据从响应体返回，见《[BackEndV3 与 UC 令牌统一方案](./BackEndV3与UC令牌统一方案.md)》 |
| 直连登录链路 | `POST /oauth2/direct-login`、`POST /oauth2/direct-user` 等直连接口 |

### 1.4 令牌过期与刷新

access_token 默认有效期 7200 秒。过期后再调用本文档接口会响应 `code=80001`。

- 业务后端持有 refresh_token 的接入方：调用 `POST /oauth2/token`（`grant_type=refresh_token`，须提交 `client_secret`）换取新的 access_token，见《加密客户端 OAuth2 接入文档》第 7 节。refresh_token 是固定凭证，有效期内可反复刷新，本地无需替换。
- 刷新返回 `90009` 表示 refresh_token 已过期或被吊销，需清除本地令牌并重新发起授权。
- 由 BackEndV3 代持 refresh_token 的前端：带自签 token 调用 BackEndV3 的 `/auth/token/refresh`，由服务端代刷并回带新的 access_token。

### 1.5 响应格式

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
| `data` | object / array / null | 响应数据 |

`/oauth2/ak-accounts/**` 的应用内失败一律返回 HTTP `200` + 业务码（含参数校验失败、限流与未绑定）；其他接口按全局约定，业务错误返回 HTTP `200`，请求参数校验失败可能返回 HTTP `400`。调用方应始终检查响应体中的 `code`，不能只判断 HTTP 状态码。

## 2. 排班表

```http
GET /oauth2/schedules/{id}
Authorization: Bearer <access_token>
```

需要 `gama-data.read`。

### 2.1 路径参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer | 是 | 排班表 ID |

该接口复用公开查询逻辑，与 `GET /schedules/{id}` 返回结构相同：按 ID 返回完整排班表，不校验排班表归属。

### 2.2 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 1001,
    "schedule": [
      {"date": "2026-09-21", "time": "09:00-18:00", "status": "on_duty"}
    ],
    "createTime": "2026-09-20T10:00:00",
    "updateTime": "2026-09-21T09:30:00"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | 排班表 ID |
| `schedule` | array | 客户端提交的排班记录数组，原样返回 |
| `createTime` | string | 创建时间 |
| `updateTime` | string | 最后更新时间 |

排班记录内部字段不固定（见《[排班表接口文档](./排班表接口文档.md)》），单个排班表最大 30KB，每个用户最多保存 5 个。

### 2.3 失败响应

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `gama-data.read` |
| `200` | `10001` | 排班表不存在（`msg=排班表不存在`） |

## 3. 游戏账号

```http
GET /oauth2/ak-accounts
Authorization: Bearer <access_token>
```

需要 `gama-data.read`。返回当前 OAuth 用户已绑定的游戏账号列表，不含干员正文。

### 3.1 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {"akUid": "123456789"}
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `akUid` | string | 游戏账号 UID |

没有绑定任何游戏账号时 `data` 为 `[]`。绑定关系存在但角色信息缺失的账号不会出现在结果中（绑定关系本身仍保留）。

### 3.2 失败响应

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `gama-data.read` |

## 4. 干员数据

```http
GET /oauth2/ak-accounts/operators?akUid={akUid}
Authorization: Bearer <access_token>
```

需要 `gama-data.read`。全量返回该游戏账号的干员数据，星级筛选由调用方完成。

### 4.1 查询参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `akUid` | string | 是 | 游戏账号 UID，不超过 32 位可见 ASCII 字符 |

### 4.2 成功响应

HTTP `200`，并携带响应头 `Cache-Control: private, no-cache`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "akUid": "123456789",
    "items": [
      {
        "id": "char_002_amiya",
        "recordId": 90001,
        "rarity": 5,
        "level": 80,
        "evolvePhase": 2,
        "mainSkillLevel": 7,
        "skill1": 7,
        "skill2": 10,
        "skill3": 0,
        "equipX": 3,
        "equipY": 0,
        "equipD": 0,
        "equipA": 0,
        "equipB": 0,
        "potentialRank": 2
      }
    ]
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `akUid` | string | 游戏账号 UID |
| `items` | array | 干员记录列表，无数据时为空数组 |
| `items[].id` | string | 稳定干员编码，业务关联请使用该字段 |
| `items[].recordId` | integer | 数据库自增行 ID，仅供排查 |
| `items[].rarity` | integer | 干员星级，`0` 表示未提供 |
| `items[].level` | integer | 干员等级 |
| `items[].evolvePhase` | integer | 精英化阶段 |
| `items[].mainSkillLevel` | integer | 基础技能等级 |
| `items[].skill1` / `skill2` / `skill3` | integer | 技能等级或状态 |
| `items[].equipX` / `equipY` / `equipD` / `equipA` / `equipB` | integer | 各模组数值 |
| `items[].potentialRank` | integer | 潜能等级（不是星级） |

全部数值字段始终返回数字，不会返回 `null`。

### 4.3 失败响应

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `gama-data.read`，或该游戏账号不在当前用户名下 |
| `200` | `10002` | `akUid` 未携带或格式非法 |

查询他人账号与查询不存在的账号返回同一个 `80008`，服务端不区分、不暴露账号是否存在。

## 5. 保存角色信息与干员数据

```http
POST /oauth2/ak-accounts/operators/save
Content-Type: application/json
Authorization: Bearer <access_token>
```

需要 `gama-data.write`。目标游戏账号取自请求体 `playerInfo.akUid`；请求体和响应结构与 `POST /user/ak-accounts/operators/save` 完全一致。

### 5.1 请求字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `playerInfo.akUid` | string | 是 | 目标游戏账号 UID，不超过 32 位可见 ASCII 字符 |
| `operators` | array | 是 | 干员数组，不能为空 |
| `operators[].id` | string | 是 | 稳定干员编码，1-64 位可见 ASCII 字符 |
| `operators[].rarity` | integer | 否 | 干员星级，0~6 |
| `operators[].level` | integer | 否 | 干员等级，0~65535 |
| `operators[].evolvePhase` | integer | 否 | 精英化阶段，0~255 |
| `operators[].mainSkillLevel` | integer | 否 | 基础技能等级，0~255 |
| `operators[].skill1` / `skill2` / `skill3` | integer | 否 | 技能等级或状态，0~255 |
| `operators[].equipX` / `equipY` / `equipD` / `equipA` / `equipB` | integer | 否 | 各模组数值，0~255 |
| `operators[].potentialRank` | integer | 否 | 潜能等级，0~255 |

数值字段可以省略、传 `null` 或空字符串，服务端统一按 `0` 落库（与显式传 `0` 等价，**不表示沿用旧值**）。负数或超出上述范围会返回 `code=10002`。

### 5.2 请求示例

```json
{
  "playerInfo": {
    "akUid": "123456789"
  },
  "operators": [
    {
      "id": "char_002_amiya",
      "rarity": 5,
      "level": 80,
      "evolvePhase": 2,
      "mainSkillLevel": 7,
      "skill1": 7,
      "skill2": 10,
      "skill3": 0,
      "equipX": 3,
      "equipY": 0,
      "equipD": 0,
      "equipA": 0,
      "equipB": 0,
      "potentialRank": 2
    }
  ]
}
```

### 5.3 保存语义

- 传入的干员 `id` 已存在则按属性比较后更新，不存在则新增；**请求中未出现的干员不会被删除**，需要删除请走用户侧接口。
- 同一个请求中的干员 `id` 不能重复，重复返回 `code=10002`。
- 当前用户首次上传某个游戏账号时直接建立绑定关系，无需预先绑定。
- 同一游戏账号 2 秒内只接受一次保存请求，命中限流返回 `code=30006`，建议等待秒数在 `data.retryAfterSeconds` 中。

### 5.4 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "createdCount": 1,
    "updatedCount": 2,
    "unchangedCount": 3
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `createdCount` | integer | 新增记录数 |
| `updatedCount` | integer | 属性实际变化并更新的记录数 |
| `unchangedCount` | integer | 与已存数据完全相同、未写库的记录数 |

三项之和等于本次请求传入的干员记录数。

### 5.5 失败响应

| HTTP 状态 | `code` | 含义 | 处理建议 |
| --- | --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 | 刷新或重新获取 access token |
| `200` | `80008` | 缺少 `gama-data.write` | 在授权请求中申请该 scope |
| `200` | `10002` | 字段缺失、格式错误、数值越界或干员 `id` 重复 | 修正请求后重试 |
| `200` | `30006` | 该游戏账号上传过于频繁 | 按 `data.retryAfterSeconds` 等待后重试 |
| `200` | `40003` | 限流服务不可用 | 稍后重试 |
| `200` | `40004` | 干员数据保存冲突 | 重试整个请求 |
| `200` | `40001` | 服务端内部错误 | 稍后重试并保留请求信息 |

限流响应示例：

```json
{
  "code": 30006,
  "msg": "该游戏账号上传过于频繁，请稍后再试",
  "data": {
    "retryAfterSeconds": 2
  }
}
```

## 6. 常见错误总览

| `code` | 含义 | 出现场景 |
| --- | --- | --- |
| `80001` | 未登录或登录已失效 | access token 缺失、无效或已过期 |
| `80008` | 无权限操作 | 缺少所需 scope，或游戏账号不在当前用户名下 |
| `10001` | 参数错误 | 排班表不存在 |
| `10002` | 参数校验失败 | 请求字段缺失、格式错误或数值越界 |
| `30006` | 该游戏账号上传过于频繁 | 仅保存接口，等待秒数见 `data.retryAfterSeconds` |
| `40003` | 限流服务不可用 | 稍后重试 |
| `40004` | 干员数据保存冲突，请重试 | 重试整个请求 |
| `40001` | 系统繁忙，请稍后再试 | 稍后重试并保留请求信息 |

完整错误码见《[错误码参考](./错误码参考.md)》。
