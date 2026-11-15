# OAuth 终末地数据接口

本文档面向 OAuth 接入方，说明通过 OAuth access_token 访问《明日方舟：终末地》玩家数据与寻访记录的接口。接口复用用户侧既有服务逻辑；用户身份与 OAuth 客户端均由 access_token 决定，请求参数不能指定或覆盖它们。

| 模块 | 前缀 | 说明 |
| --- | --- | --- |
| 玩家数据 | `/oauth2/ef-accounts` | 已绑定角色列表、角色详情（玩家数据）读取与上传 |
| 寻访记录 | `/oauth2/ef-pool-records` | 导入任务创建与轮询、已导入账户列表、寻访记录读取 |

用户侧同名接口为 `/user/ef-accounts/**`、`/user/ef-pool-records/**`，请求与响应结构完全一致，仅鉴权方式不同。

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
| `ef-data.read` | 查询已绑定角色、读取玩家数据、查询任务状态、查询已导入账户、读取寻访记录 |
| `ef-data.write` | 保存玩家数据、创建寻访记录导入任务 |
| `gama-data.read` | 上级范围：覆盖全部游戏的读取能力，等价于持有一切 `*-data.read` |
| `gama-data.write` | 上级范围：覆盖全部游戏的写入能力，等价于持有一切 `*-data.write` |

缺少所需 scope 时响应 `code=80008`，`msg` 形如 `缺少授权范围: ef-data.read`。客户端必须在注册时登记相应 scope，并在授权请求中申请它。

> 玩家数据是一整份 JSON（角色、干员与仓库都在这份数据内），因此读取 `player-data` 只需 `ef-data.read` 一个范围，不再按干员 / 仓库细分。范围存在上下级关系：登记并申请 `gama-data.read` 的客户端可访问本组全部读接口（兼容存量客户端）；只登记 `ef-data.read` 的客户端**不能**访问明日方舟接口。写范围不隐含读范围。

OAuth Token 同时绑定用户与客户端，用于客户端认证和 scope 校验；角色绑定关系与寻访记录归属只按 Token 中的用户 `uid` 校验，不按 `client_id` 拆分。

### 1.3 令牌来源

本文档的接口只要求一个有效 access_token，来源不限：

| 场景 | 获取方式 |
| --- | --- |
| 后端服务 / BFF（能安全保存 client_secret） | 授权码 + PKCE，由业务后端换码，见《加密客户端 OAuth2 接入文档》 |
| 无后端 Web 应用（公共客户端） | 授权码 + PKCE，浏览器直接换码，见《无后端 Web 应用 OAuth2 接入文档》 |
| 直连登录链路 | `POST /oauth2/direct-login`、`POST /oauth2/direct-user` 等直连接口 |

### 1.4 令牌过期与刷新

access_token 默认有效期 7200 秒。过期后再调用本文档接口会响应 `code=80001`。

- 业务后端持有 refresh_token 的接入方：调用 `POST /oauth2/token`（`grant_type=refresh_token`，须提交 `client_secret`）换取新的 access_token，见《加密客户端 OAuth2 接入文档》第 7 节。
- 刷新返回 `90009` 表示 refresh_token 已过期或被吊销，需清除本地令牌并重新发起授权。

### 1.5 响应格式

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

`/oauth2/ef-accounts/**` 与 `/oauth2/ef-pool-records/**` 的应用内失败一律返回 HTTP `200` + 业务码（含参数校验失败、限流与未绑定）。调用方应始终检查响应体中的 `code`，不能只判断 HTTP 状态码。

日期时间字段为 ISO-8601 无时区格式，例如 `2026-10-30T20:00:00`。

## 2. 玩家数据

玩家数据以整份 JSON 对象上传，服务端**不拆分字段**，序列化后直接以文本形式落库；同一角色（`roleId` + `serverId`）只保存一份，由全部绑定该角色的用户共享。首次上传某个角色时直接建立绑定，无需预先绑定。

### 2.1 查询已绑定的角色列表

```http
GET /oauth2/ef-accounts
Authorization: Bearer <access_token>
```

需要 `ef-data.read`。返回当前令牌用户已绑定的全部终末地角色，不含玩家数据正文，按最近导入时间倒序，调用方取首条即可默认展示最新数据。

成功响应 HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "roleId": "123456789",
      "serverId": "1",
      "createTime": "2026-10-30T19:00:00",
      "updateTime": "2026-10-30T20:00:00"
    }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roleId` | string | 终末地游戏内角色 UID |
| `serverId` | string | 服务器 ID，未传时为 `""` |
| `createTime` | string / null | 绑定创建时间：首次导入该角色数据时写入 |
| `updateTime` | string | 最近一次导入该角色数据的时间 |

没有绑定任何角色时 `data` 为 `[]`。

失败响应：

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `ef-data.read` |
| `200` | `40001` | 服务端内部错误 |

### 2.2 读取玩家数据

```http
GET /oauth2/ef-accounts/player-data?roleId={roleId}&serverId={serverId}
Authorization: Bearer <access_token>
```

需要 `ef-data.read`。角色由 `roleId` + `serverId` 定位，**该角色必须已绑定到当前令牌用户**，否则返回 `code=80008`，不能读取他人角色数据。

| 参数 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | query | 是 | 终末地游戏内角色 UID，不超过 32 位可见 ASCII 字符 |
| `serverId` | query | 否 | 服务器 ID，不超过 16 位可见 ASCII 字符；省略时按空串定位 |

成功响应 HTTP `200`，并携带响应头 `Cache-Control: private, no-cache`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "roleId": "123456789",
    "serverId": "1",
    "playerData": {
      "base": {
        "roleId": "123456789",
        "name": "玩家昵称",
        "level": 60
      }
    },
    "updateTime": "2026-10-30T20:00:00"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roleId` | string | 角色 UID |
| `serverId` | string | 服务器 ID，未传时为 `""` |
| `playerData` | object | 保存时提交的 JSON 对象，服务端原样返回 |
| `updateTime` | string | 最近一次保存时间 |

绑定关系存在但数据行缺失（异常清理场景）时 `data` 为 `null`，调用方可按空数据处理。

失败响应：

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `ef-data.read`，或该角色未绑定到当前令牌用户 |
| `200` | `10002` | 缺少 `roleId` 或角色标识格式非法 |
| `200` | `40001` | 服务端内部错误或存储数据损坏 |
查询他人角色与查询不存在的角色返回同一个 `80008`，服务端不区分、不暴露角色是否存在。

### 2.3 保存玩家数据

```http
POST /oauth2/ef-accounts/player-data/save
Content-Type: application/json
Authorization: Bearer <access_token>
```

需要 `ef-data.write`。目标角色由请求体中的 `roleId` + `serverId` 定位，**当前令牌用户首次上传某个角色时直接建立绑定关系**。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | string | 是 | 终末地游戏内角色 UID（`card/detail` 的 `roleId`），不超过 32 位可见 ASCII 字符 |
| `serverId` | string | 否 | 服务器 ID（国服常见 `1`），不超过 16 位可见 ASCII 字符；省略时按空串保存 |
| `playerData` | object | 是 | 玩家数据 JSON 对象，整份原样保存，序列化后不超过 2MB |

请求示例：

```json
{
  "roleId": "123456789",
  "serverId": "1",
  "playerData": {
    "base": {
      "roleId": "123456789",
      "name": "玩家昵称",
      "level": 60
    },
    "chars": [
      {
        "charId": "char_001",
        "level": 80,
        "evolvePhase": 4
      }
    ]
  }
}
```

保存语义：

- 同一 `roleId` + `serverId` 已存在数据则**整份覆盖** `playerData` 并刷新保存时间，不存在则新增；不做字段级合并。
- `playerData` 必须是 JSON **对象**，传入数组、字符串或 `null` 返回 `code=10002`。
- 同一角色 2 秒内只接受一次保存请求，命中限流返回 `code=30006`，建议等待秒数在 `data.retryAfterSeconds` 中；该名额**所有绑定该角色的用户共享**。

成功响应 HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "roleId": "123456789",
    "serverId": "1",
    "updateTime": "2026-10-30T20:00:00"
  }
}
```

失败响应：

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `ef-data.write` |
| `200` | `10002` | 字段缺失、角色标识格式非法、`playerData` 非对象或超过 2MB |
| `200` | `30006` | 该角色上传过于频繁，按 `data.retryAfterSeconds` 等待后重试 |
| `200` | `40003` | 限流服务不可用 |
| `200` | `40001` | 服务端内部错误 |

## 3. 寻访记录

调用流程：

```
创建任务 → 轮询任务状态（约 1~数十秒）→ 从账户列表取 roleId → 读取寻访记录
```

1. 调用方提交 `hgToken` 创建任务，立即拿到 `taskId`；
2. 后台调度器每秒取一条未开始任务执行导入（换取 u8Token → 拉取干员/武器寻访记录 → 落库）；
3. 轮询 `check-task`，返回成功即表示导入完成，同时该角色进入「导入过的账户」列表；
4. 调用 `record` 读取该角色的全部寻访记录。

补充规则：

- 创建超过 10 分钟仍未开始的任务不再执行；
- 任务创建 15 分钟后，库中 `token` 字段会被定时覆盖为 `complete_114514`，避免 hgToken 长期留存；
- 同一 `hgToken` 每分钟只能创建 1 次任务。

### 3.1 创建导入任务

```http
POST /oauth2/ef-pool-records/create-task?hgToken={hgToken}
Authorization: Bearer <access_token>
```

需要 `ef-data.write`（本接口会写入任务与角色绑定关系）。任务归属取令牌中的 `uid`，导入完成后服务端把角色档案与「用户-角色」关系写入绑定表，该角色才会出现在账户列表中。

| 参数 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| `hgToken` | query | 是 | 鹰角通行证 token |

成功响应 HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "task1234567890123"
}
```

失败响应：

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `ef-data.write` |
| `200` | `10001` | `hgToken` 未携带或为空白 |
| `200` | `30005` | 同一 hgToken 1 分钟内重复创建任务 |
| `200` | `40003` | 限流服务不可用 |

### 3.2 查询任务状态

```http
GET /oauth2/ef-pool-records/check-task?taskId={taskId}
Authorization: Bearer <access_token>
```

需要 `ef-data.read`。仅任务创建者可见，其他用户的任务一律返回 `code=80008`。

| 参数 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| `taskId` | query | 是 | 创建任务时返回的任务 ID |

任务完成时返回角色信息，HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "roleId": "1001234567",
    "uid": "123456789",
    "nickName": "管理员"
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roleId` | string | 终末地游戏内角色 UID，读取寻访记录时使用 |
| `uid` | string | 鹰角通行证 UID |
| `nickName` | string | 玩家昵称 |

失败响应：

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `ef-data.read`，或任务不属于当前令牌用户 |
| `200` | `10001` | `taskId` 未携带，或任务不存在 / 已过期 |
| `200` | `10003` | 任务尚未完成（仍在处理中或执行失败），可继续轮询 |

### 3.3 查询导入过的账户

```http
GET /oauth2/ef-pool-records/accounts
Authorization: Bearer <access_token>
```

需要 `ef-data.read`。返回当前令牌用户导入过寻访记录的终末地账户，按最近导入时间倒序，同角色只保留最新一条。

成功响应 HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "roleId": "1001234567",
      "nickName": "管理员",
      "updateTime": "2026-11-02T10:15:30",
      "playerDataImported": true
    }
  ]
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `roleId` | string | 终末地游戏内角色 UID，读取寻访记录时回传 |
| `nickName` | string | 玩家昵称 |
| `updateTime` | string | 最近一次导入时间 |
| `playerDataImported` | boolean | 该角色是否已导入过玩家数据，可据此提示补传角色详情 |

从未导入过时 `data` 为 `[]`。失败响应：

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `ef-data.read` |
| `200` | `40001` | 服务端内部错误 |

### 3.4 读取寻访记录

```http
GET /oauth2/ef-pool-records/record?roleId={roleId}
Authorization: Bearer <access_token>
```

需要 `ef-data.read`。角色必须已归属当前令牌用户，否则返回 `code=80008`。

| 参数 | 位置 | 必填 | 说明 |
| --- | --- | --- | --- |
| `roleId` | string | 是 | 取自「导入过的账户」列表 |

成功响应 HTTP `200`，并携带响应头 `Cache-Control: private, no-cache`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "characterPoolRecord": [
      {
        "id": 1950000000000000001,
        "roleId": "1001234567",
        "poolId": "pool_xxx",
        "poolName": "特许寻访",
        "charId": "char_xxx",
        "charName": "角色名",
        "rarity": 6,
        "isFree": false,
        "isNew": true,
        "gachaTs": "1760000000000",
        "seqId": "15",
        "lang": "zh-cn",
        "poolType": "E_CharacterGachaPoolType_Special",
        "serverId": "1"
      }
    ],
    "weaponPoolRecord": [
      {
        "id": 1950000000000000002,
        "roleId": "1001234567",
        "poolId": "pool_yyy",
        "poolName": "武器寻访",
        "weaponId": "weapon_xxx",
        "weaponName": "武器名",
        "weaponType": "Sword",
        "rarity": 6,
        "isNew": true,
        "gachaTs": "1760000000000",
        "seqId": "10",
        "lang": "zh-cn",
        "serverId": "1"
      }
    ]
  }
}
```

干员记录字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | 记录主键 |
| `roleId` | string | 终末地游戏内角色 UID |
| `poolId` | string | 卡池 ID |
| `poolName` | string | 卡池名称 |
| `charId` | string | 角色 ID |
| `charName` | string | 角色名称 |
| `rarity` | integer | 稀有度 |
| `isFree` | boolean | 是否免费抽取 |
| `isNew` | boolean | 是否新角色 |
| `gachaTs` | string | 寻访时间戳（毫秒） |
| `seqId` | string | 记录序列 ID |
| `lang` | string | 数据语言，固定 `zh-cn` |
| `poolType` | string | 卡池类型，见下表 |
| `serverId` | string | 服务器 ID，固定 `1` |

干员卡池类型 `poolType`：

| 值 | 含义 |
| --- | --- |
| `E_CharacterGachaPoolType_Special` | 特许寻访 |
| `E_CharacterGachaPoolType_Rerun` | 重构寻访 |
| `E_CharacterGachaPoolType_Joint` | 辉光庆典（联合寻访） |
| `E_CharacterGachaPoolType_Standard` | 基础寻访（常驻） |
| `E_CharacterGachaPoolType_Beginner` | 启程寻访（新手） |

武器记录字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | integer | 记录主键 |
| `roleId` | string | 终末地游戏内角色 UID |
| `poolId` | string | 卡池 ID |
| `poolName` | string | 卡池名称 |
| `weaponId` | string | 武器 ID |
| `weaponName` | string | 武器名称 |
| `weaponType` | string | 武器类型 |
| `rarity` | integer | 稀有度 |
| `isNew` | boolean | 是否新武器 |
| `gachaTs` | string | 寻访时间戳（毫秒） |
| `seqId` | string | 记录序列 ID |
| `lang` | string | 数据语言，固定 `zh-cn` |
| `serverId` | string | 服务器 ID，固定 `1` |

> **赠礼类记录**：上游会混入赠礼记录（当期特许寻访累计满 60 抽赠送寻访情报书），这类记录**没有角色信息**，服务端以 `charId = gift_intel_book`、`charName = 寻访情报书` 落库保留。调用方统计抽卡次数、出率、收集度时，必须排除 `charId` 以 `gift_` 开头的行。

失败响应：

| HTTP 状态 | `code` | 含义 |
| --- | --- | --- |
| `200` | `80001` | access token 缺失、无效或已过期 |
| `200` | `80008` | 缺少 `ef-data.read`，或该角色未归属当前令牌用户 |
| `200` | `10001` | `roleId` 未携带或为空 |

## 4. 常见错误总览

| `code` | 含义 | 出现场景 |
| --- | --- | --- |
| `80001` | 未登录或登录已失效 | access token 缺失、无效或已过期 |
| `80008` | 无权限操作 | 缺少所需 scope，或角色 / 任务不属于当前令牌用户 |
| `10001` | 参数错误 | 请求必填参数缺失或为空（如 `hgToken`、`taskId`、`roleId`），或任务不存在 |
| `10002` | 参数校验失败 | 请求字段缺失、格式错误或数值越界 |
| `10003` | 任务尚未完成 | 轮询导入任务时的中间状态，继续轮询即可 |
| `30005` | 创建任务过于频繁 | 同一 hgToken 1 分钟内重复创建 |
| `30006` | 该角色上传过于频繁 | 仅保存玩家数据接口，等待秒数见 `data.retryAfterSeconds` |
| `40003` | 限流服务不可用 | 稍后重试 |
| `40001` | 系统繁忙，请稍后再试 | 稍后重试并保留请求信息 |

完整错误码见《错误码参考》。
