# OAuth 游戏数据接口

本文档说明通过 OAuth access_token 访问排班表、游戏账号和干员数据的接口。接口复用用户侧既有服务逻辑；用户身份与 OAuth 客户端均由 access_token 决定，请求参数不能指定或覆盖它们。

## 1. 认证与授权范围

所有接口携带 OAuth access_token：

```http
Authorization: Bearer <access_token>
```

| scope | 可访问接口 |
| --- | --- |
| `gama-data.read` | 读取排班表、已绑定游戏账号和干员数据 |
| `gama-data.write` | 保存游戏账号角色信息和干员数据 |

缺少所需 scope 时响应 `code=80008`。OAuth Token 仍同时绑定用户与客户端，用于客户端认证和 scope 校验；游戏账号绑定关系只按 Token 中的用户 `uid` 校验，不按 `client_id` 拆分。

## 2. 排班表

```http
GET /oauth2/schedules/{id}
Authorization: Bearer <access_token>
```

需要 `gama-data.read`。返回结构与 `GET /schedules/{id}` 相同。

## 3. 游戏账号和干员

```http
GET /oauth2/ak-accounts
GET /oauth2/ak-accounts/operators?akUid={akUid}
Authorization: Bearer <access_token>
```

以上读取接口需要 `gama-data.read`，返回结构与用户侧对应接口一致。干员读取响应包含 `Cache-Control: private, no-cache`。

```http
POST /oauth2/ak-accounts/operators/save
Content-Type: application/json
Authorization: Bearer <access_token>
```

保存接口需要 `gama-data.write`，目标游戏账号取自请求体 `playerInfo.akUid`；请求体和响应结构与 `POST /user/ak-accounts/operators/save` 完全一致。
