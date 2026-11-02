# 终末地寻访记录 API

> 来源：从 `endfield-yituliu-backend` 迁移至 UserCenterV1。
> 当前版本**不做用户鉴权**，调用方无需携带登录 token。

## 基础信息

| 项目 | 说明 |
|---|---|
| Base URL | `http://<host>:<port>` |
| 统一前缀 | `/ef/pool-record` |
| 返回格式 | `application/json`，统一封装为 `{ code, msg, data }` |
| 成功标识 | `code = 200` |
| 鉴权 | 无（`/ef/**` 不在登录拦截器范围内） |

统一响应结构：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

## 调用流程

```
创建任务 → 轮询任务状态（约 1~数十秒）→ 任务完成后读取寻访记录
```

1. 前端提交 `hgToken` 创建任务，立即拿到 `taskId`；
2. 后台调度器每秒取一条未开始任务执行导入（换取 u8Token → 拉取干员/武器寻访记录 → 落库）；
3. 前端轮询 `check-task`，返回成功即表示导入完成；
4. 完成后调用 `character/list` 读取该角色的全部寻访记录。

补充规则：

- 创建超过 10 分钟仍未开始的任务不再执行；
- 任务创建 15 分钟后，库中 `token` 字段会被定时覆盖为 `complete_114514`，避免 hgToken 长期留存；
- 同一 `hgToken` 每分钟只能创建 1 次任务。

## 接口列表

### 1. 创建寻访记录导入任务

`POST /ef/pool-record/create-task`

**请求参数**（Query 或 `application/x-www-form-urlencoded`）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| hgToken | string | 是 | 鹰角通行证 token |

**响应 data**：任务 ID（string）

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": "task1234567890123"
}
```

**业务错误**

| code | msg | 场景 |
|---|---|---|
| 10001 | hgToken不能为空 | hgToken 为空或空白 |
| 30005 | 创建任务过于频繁，请稍后再试 | 同一 hgToken 1 分钟内重复创建 |
| 40003 | 限流服务不可用，请稍后再试 | Redis 限流设施异常 |

示例：

```bash
curl -X POST "http://localhost:8080/ef/pool-record/create-task?hgToken=xxx"
```

### 2. 查询任务状态

`GET /ef/pool-record/check-task`

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| taskId | string | 是 | 创建任务时返回的任务 ID |

**响应 data**：任务完成时返回玩家角色信息，未完成时返回业务错误。

| 字段 | 类型 | 说明 |
|---|---|---|
| roleId | string | 终末地游戏内角色 UID |
| uid | string | 鹰角账号 uid |
| nickName | string | 玩家昵称 |

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

**业务错误**

| code | msg | 场景 |
|---|---|---|
| 10001 | taskId不能为空 | 未传 taskId |
| 10001 | 任务不存在或已过期 | taskId 查不到记录 |
| 10003 | 任务尚未完成 | 任务仍在处理中或执行失败 |

示例：

```bash
curl "http://localhost:8080/ef/pool-record/check-task?taskId=task1234567890123"
```

### 3. 读取干员与武器寻访记录

`GET /ef/pool-record/character/list`

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| taskId | string | 是 | 已完成任务的任务 ID |

**响应 data**：`characterPoolRecord` 与 `weaponPoolRecord` 两个数组。

干员记录字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 记录主键 |
| roleId | string | 终末地游戏内角色 UID |
| poolId | string | 卡池 ID |
| poolName | string | 卡池名称 |
| charId | string | 角色 ID |
| charName | string | 角色名称 |
| rarity | number | 稀有度 |
| isFree | boolean | 是否免费抽取 |
| isNew | boolean | 是否新角色 |
| gachaTs | string | 寻访时间戳（毫秒） |
| seqId | string | 记录序列 ID |
| lang | string | 数据语言，固定 `zh-cn` |
| poolType | string | 卡池类型，见下方枚举 |
| serverId | string | 服务器 ID，固定 `1` |

干员卡池类型 `poolType`：

| 值 | 含义 |
|---|---|
| `E_CharacterGachaPoolType_Special` | 特许寻访 |
| `E_CharacterGachaPoolType_Rerun` | 重构寻访 |
| `E_CharacterGachaPoolType_Joint` | 辉光庆典（联合寻访） |
| `E_CharacterGachaPoolType_Standard` | 基础寻访（常驻） |
| `E_CharacterGachaPoolType_Beginner` | 启程寻访（新手，满 40 条后不再重复拉取） |

武器记录字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | number | 记录主键 |
| roleId | string | 终末地游戏内角色 UID |
| poolId | string | 卡池 ID |
| poolName | string | 卡池名称 |
| weaponId | string | 武器 ID |
| weaponName | string | 武器名称 |
| weaponType | string | 武器类型 |
| rarity | number | 稀有度 |
| isNew | boolean | 是否新武器 |
| gachaTs | string | 寻访时间戳（毫秒） |
| seqId | string | 记录序列 ID |
| lang | string | 数据语言，固定 `zh-cn` |
| serverId | string | 服务器 ID，固定 `1` |

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

**业务错误**

| code | msg | 场景 |
|---|---|---|
| 10001 | taskId不能为空 | 未传 taskId |
| 10001 | 任务不存在或已过期 | taskId 查不到记录 |

示例：

```bash
curl "http://localhost:8080/ef/pool-record/character/list?taskId=task1234567890123"
```

## 数据来源与落库

| 表 | 说明 |
|---|---|
| `player_pool_record_task` | 导入任务，保存 hgToken、任务状态与 roleId |
| `character_pool_record` | 干员寻访记录，`INSERT IGNORE` 去重 |
| `weapon_pool_record` | 武器寻访记录，`INSERT IGNORE` 去重 |
| `endministrator_info` | 玩家角色信息（roleId、uid、nickName） |

- 干员数据：`https://ef-webview.hypergryph.com/api/record/char`
- 武器数据：`https://ef-webview.hypergryph.com/api/record/weapon`
- token 换取：`http://127.0.0.1:10086/token`（可用 `user-center.ef.pool-record.token-service-url` 覆盖），入参 `hgToken`，返回 `u8Token / roleId / uid / nickName`
