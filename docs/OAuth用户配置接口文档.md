# OAuth 用户配置接口

本文档面向 OAuth 接入方，说明用户配置的保存、读取、删除和配额查询接口。

## 1. 通用约定

### 1.1 认证

所有接口都需要携带有效的 OAuth `access_token`：

```http
Authorization: Bearer <access_token>
```

服务端从令牌中取得用户 `uid` 和 OAuth 客户端 `client_id`。接入方不能通过请求参数指定这两个值，也只能读写当前 OAuth 客户端名下的配置。

### 1.2 响应格式

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

除文档明确说明的 HTTP `400`、`409` 外，调用方还应始终检查响应体中的 `code`，不能只判断 HTTP 状态码。

## 2. 保存配置（无条件创建 / 覆盖更新）

```http
POST /oauth2/config/save
Content-Type: application/json
Authorization: Bearer <access_token>
```

该接口为普通保存，不做并发控制：

- `id` 为空（省略或传 `null`）→ 新建配置。
- `id` 非空 → 按 `id` 直接覆盖更新（last-write-wins）：不校验内容 hash，不要求携带 `expectedHash`（携带也会被忽略）。

需要防止并发覆盖的应用请改用 `POST /oauth2/config/save-if-match`（见下文第 3 节）。

### 2.1 请求字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer / null | 否 | 为空=新建；非空=按 id 覆盖更新，目标必须属于当前用户和当前 OAuth 客户端 |
| `category` | string | 是 | 配置分类，1-32 个字符 |
| `version` | string | 是 | 配置版本，1-32 个字符 |
| `name` | string | 是 | 配置名称，1-32 个字符 |
| `source` | string | 否 | 配置来源，最多 32 个字符 |
| `note` | string | 否 | 备注，最多 32 个字符 |
| `config` | object / string | 是 | 配置内容，不能为 `null` |
| `expectedHash` | string / null | 否 | save 接口不使用：创建时携带非空值返回 HTTP `400`；更新时携带一律忽略 |

save 接口不校验 `expectedHash`。防并发更新请使用 `POST /oauth2/config/save-if-match`。

### 2.2 创建示例

```json
{
  "id": null,
  "category": "editor",
  "version": "v1",
  "name": "default",
  "source": "web",
  "note": "首次同步",
  "config": {
    "theme": "dark",
    "fontSize": 14
  },
  "expectedHash": null
}
```

创建只会在当前用户、当前 OAuth 客户端下不存在相同 `category + version + name` 的记录时成功。记录已经存在时返回 HTTP `409`（`code=10005`），`data.currentHash` 为已存在记录的内容 hash。

### 2.3 覆盖更新示例

```json
{
  "id": 123,
  "category": "editor",
  "version": "v1",
  "name": "default",
  "source": "web",
  "note": "自动同步",
  "config": {
    "theme": "light",
    "fontSize": 16
  }
}
```

覆盖更新按 `id` 直接生效：`category`、`version`、`name` 必须与目标记录一致，否则返回 HTTP `400`。请求中即使携带 `expectedHash` 也会被忽略，服务端总是以本次内容覆盖。目标记录不存在或不属于当前用户、当前 OAuth 客户端时返回 HTTP `409`（`data.currentHash` 为 `null`）。

### 2.4 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 123,
    "hash": "5f70bf18a08660b84f3f4f4f25c4a32f5f728c01e98b767a7c8a153f67e85972"
  }
}
```

接入方应保存响应中的新 `hash`：save 接口本身不校验它，但后续使用 save-if-match 防并发或再次读取时仍需最新 hash。

### 2.5 失败响应（创建冲突 / 覆盖目标不存在）

HTTP `409 Conflict`：

```json
{
  "code": 10005,
  "msg": "配置已被更新",
  "data": {
    "currentHash": "c19d3f7b40d5d193e6dc1faa4f26b18d73f8a70bff94e59f3d47a50e64f7f031"
  }
}
```

创建冲突时，`data.currentHash` 为已存在记录的内容 hash；按 id 覆盖的目标不存在或不属于当前客户端时，`currentHash` 为 `null`。不要携带旧 hash 循环重试，应重新读取配置后决定新建或覆盖。

## 3. 条件更新配置（save-if-match，防并发覆盖）

```http
POST /oauth2/config/save-if-match
Content-Type: application/json
Authorization: Bearer <access_token>
```

该接口只承担按 `id` 的条件更新，采用 CAS（Compare-And-Set）语义，不承担创建：

- `id` 和 `expectedHash` 均必填，缺少任一字段返回 HTTP `400`。
- 仅当目标记录当前的 `content_hash` 等于请求携带的 `expectedHash` 时才会落库并返回新 `hash`。
- hash 不一致（被其他请求先更新）或目标记录已被删除时返回 HTTP `409`（`code=10005`），`data.currentHash` 为服务端最新 hash（已删除则为 `null`）。

### 3.1 请求字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer | 是 | 读取接口返回的配置 ID，必须属于当前用户和当前 OAuth 客户端 |
| `category` | string | 是 | 配置分类，1-32 个字符，须与原记录一致 |
| `version` | string | 是 | 配置版本，1-32 个字符，须与原记录一致 |
| `name` | string | 是 | 配置名称，1-32 个字符，须与原记录一致 |
| `source` | string | 否 | 配置来源，最多 32 个字符 |
| `note` | string | 否 | 备注，最多 32 个字符 |
| `config` | object / string | 是 | 配置内容，不能为 `null` |
| `expectedHash` | string | 是 | 目标记录的最新 64 位 SHA-256 hash，由读取接口或上次保存响应返回 |

### 3.2 更新示例

```json
{
  "id": 123,
  "category": "editor",
  "version": "v1",
  "name": "default",
  "source": "web",
  "note": "自动同步",
  "config": {
    "theme": "light",
    "fontSize": 16
  },
  "expectedHash": "2b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfe"
}
```

### 3.3 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": 123,
    "hash": "5f70bf18a08660b84f3f4f4f25c4a32f5f728c01e98b767a7c8a153f67e85972"
  }
}
```

接入方应保存响应中的新 `hash`，并在下一次条件更新时作为 `expectedHash` 传回。

### 3.4 冲突响应

HTTP `409 Conflict`：

```json
{
  "code": 10005,
  "msg": "配置已被更新",
  "data": {
    "currentHash": "c19d3f7b40d5d193e6dc1faa4f26b18d73f8a70bff94e59f3d47a50e64f7f031"
  }
}
```

出现冲突时不要直接重试覆盖。接入方应重新读取配置，根据新内容决定覆盖、合并或提示用户。目标记录已被删除时，`currentHash` 为 `null`。

## 4. 读取配置

```http
GET /oauth2/config/list?category=editor&version=v1&name=default
Authorization: Bearer <access_token>
```

### 4.1 查询参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `category` | string | 是 | 配置分类 |
| `version` | string | 否 | 精确匹配配置版本 |
| `name` | string | 否 | 精确匹配配置名称 |

查询结果只包含当前 OAuth 客户端名下的配置，并按更新时间倒序返回。

### 4.2 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": 123,
      "clientId": "example-client",
      "category": "editor",
      "version": "v1",
      "name": "default",
      "source": "web",
      "note": "自动同步",
      "config": {
        "theme": "light",
        "fontSize": 16
      },
      "hash": "5f70bf18a08660b84f3f4f4f25c4a32f5f728c01e98b767a7c8a153f67e85972",
      "createTime": "2026-09-03T10:00:00",
      "updateTime": "2026-09-03T10:05:00"
    }
  ]
}
```

没有匹配记录时 `data` 为 `[]`。无条件覆盖更新仅需目标记录的 `id`；需要防并发时，再以记录的 `hash` 作为 `expectedHash` 调用 save-if-match。

## 5. 删除配置

```http
POST /oauth2/config/delete
Content-Type: application/json
Authorization: Bearer <access_token>
```

### 5.1 请求字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | integer | 是 | 读取接口返回的配置 ID，不能为 `null` |

请求示例：

```json
{
  "id": 123
}
```

服务端只允许删除当前用户、当前 OAuth 客户端名下的配置。删除为物理删除且不可恢复；删除成功后，该配置占用的字节数会从用户已用配额中扣除。删除接口不使用 CAS，因此不需要提交 `expectedHash`。

### 5.2 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

配置不存在或不属于当前 OAuth 客户端时，响应体返回 `code=10001` 和 `msg=配置不存在`。调用方应将其视为目标配置当前不可操作，不要改用其他客户端的配置 ID 重试。

## 6. 查询配额

```http
GET /oauth2/config/quota
Authorization: Bearer <access_token>
```

该接口不需要查询参数。配额以用户为单位统计，包含该用户在全部 OAuth 客户端下保存的配置内容，而不是只统计当前客户端。

### 6.1 成功响应

HTTP `200`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "usedBytes": 102400,
    "limitBytes": 512000,
    "remainingBytes": 409600
  }
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `usedBytes` | integer | 当前已使用的配置内容字节数 |
| `limitBytes` | integer | 当前用户的配置总配额字节数 |
| `remainingBytes` | integer | 当前剩余字节数 |

所有数值单位均为 byte。接入方可仅在展示时自行换算为 KB 或 MB，保存前的容量判断仍以服务端结果为准。

从未保存过配置的用户默认返回：

```json
{
  "usedBytes": 0,
  "limitBytes": 512000,
  "remainingBytes": 512000
}
```

## 7. 常见错误

| HTTP 状态 | `code` | 含义 | 处理建议 |
| --- | --- | --- | --- |
| `400` | `10002` | 请求字段缺失、格式错误、更新时修改了配置身份字段，或 save-if-match 缺少 `id` / `expectedHash` | 修正请求后重试 |
| `409` | `10005` | save 创建目标已存在、按 id 覆盖的目标不存在，或 save-if-match 携带过期 hash | 重新读取配置并处理冲突 |
| `200` | `10001` | 删除的配置不存在或不属于当前 OAuth 客户端 | 停止操作并重新读取配置列表 |
| `200` | `10004` | 用户配置总量超过当前配额 | 删除不需要的配置或申请提额 |
| `200` | `80001` | access token 缺失、无效或登录状态失效 | 重新获取有效 access token |
| `200` | `40001` | 服务端内部错误 | 稍后重试并保留请求信息以便排查 |

## 8. 推荐同步流程

### 8.1 防并发条件更新（save-if-match）

1. 调用读取接口获得配置的 `id`、内容和 `hash`。
2. 在本地基于该版本编辑配置。
3. 调用 `POST /oauth2/config/save-if-match`，将读取到的 `id` 和 `hash` 分别作为 `id`、`expectedHash`。
4. 保存成功后，用响应中的新 `hash` 替换本地旧 hash。
5. 收到 HTTP `409` 时重新读取配置，不使用旧 hash 循环重试。

### 8.2 无条件保存（save）

- 新建：调用 `POST /oauth2/config/save`，`id` 为空（省略或传 `null`），可省略 `expectedHash` 或显式传 `null`。
- 覆盖更新：同样调用 `POST /oauth2/config/save`，`id` 传目标配置 ID，无需 `expectedHash`；适合可容忍"最后写入者胜出"的同步场景。
