# 森空岛（Skland）终末地用户数据接口文档

> 整理日期：2026-10-30
>
> 参考实现：[Ankairis/ak-endfield-mcp](https://github.com/Ankairis/ak-endfield-mcp)（最小实现）、[FrostN0v0/nonebot-plugin-skland](https://github.com/FrostN0v0/nonebot-plugin-skland)（签名与完整链路）、[AixLnyt/skport-api-docs](https://github.com/AixLnyt/skport-api-docs)（字段释义、国际服差异）
>
> 声明：本文档整理自社区逆向/抓包结果，**非官方文档**，接口随时可能变更；`cred`、`token` 具备账号操作权限，务必妥善保管，不要提交到仓库或写入日志。

---

## 1. 域名与环境

| 域名 | 用途 |
|---|---|
| `zonai.skland.com` | 国服（森空岛）游戏数据、绑定关系、签到、角色卡片 |
| `as.hypergryph.com` | 国服账号认证、OAuth 授权、扫码登录 |
| `web-api.skland.com` | 国服网页端账号信息（可从这里直接拿 `cred`） |
| `ef-webview.hypergryph.com` | 国服终末地**抽卡记录**、卡池元数据（无需签名） |
| `binding-api-account-prod.hypergryph.com` | 国服按 UID 换取 `role_token`（抽卡用） |
| `zonai.skport.com` / `as.gryphline.com` / `ef-webview.gryphline.com` | 国际服对应域名（见第 11 节） |

请求统一约定：

- 响应结构：`{ "code": 0, "message": "...", "data": {...} }`，`code == 0` 表示成功，业务数据在 `data` 内。
- 时间字段：`gachaTs`、`ownTs` 是**毫秒级**时间戳字符串；`createTime`、`lastLoginTime`、`saveTime`、`maxTs` 通常是**秒级**时间戳字符串（以实际返回为准）。
- 分页字段：抽卡记录用 `seqId` 作游标（`hasMore` 表示是否还有下一页）。

---

## 2. 凭证体系

| 凭证 | 说明 | 获取位置 |
|---|---|---|
| `cred` | 森空岛登录凭据，放在请求头 `cred` | 网页端 `account/info/hg` 的 `data.content`；或 `generate_cred_by_code` 返回 |
| `token`（salt） | 签名密钥，参与 `sign` 计算 | `generate_cred_by_code` 返回的 `data.token`；或 `auth/refresh` 换得 |
| `access_token` | 鹰角通行证 token，用于 OAuth 与换取 `role_token` | 扫码登录 / 账号密码登录返回 |
| `role_token` | 终末地角色级 token，抽卡接口用 | `binding/v1/u8_token_by_uid` |

> 注意：`cred` 不等同于签名用的 `token`。`ak-endfield-mcp` 把 `account/info/hg` 的 `data.content` 直接当作请求头 `Cred` 使用，属于简化用法；需要严格签名时还必须拿到 `token`。

### 2.1 方式 A：网页直接复制 `cred`（最快）

1. 浏览器登录 [森空岛](https://www.skland.com/)。
2. 打开 `https://web-api.skland.com/account/info/hg`。
3. 复制返回 JSON 中的 `data.content`，即 `cred`。
4. 用 `cred` 调 `GET https://zonai.skland.com/api/v1/auth/refresh`（请求头带 `cred`）换取 `data.token`，作为签名密钥。

### 2.2 方式 B：扫码/密码 → OAuth → cred（可自动化）

```
1) 获取二维码
   POST https://as.hypergryph.com/general/v1/gen_scan/login
   Body: { "appCode": "4ca99fa6b56cc2ba" }
   → data.scanId

2) 轮询扫码状态
   GET  https://as.hypergryph.com/general/v1/scan_status?scanId={scanId}
   → data.scanCode

3) 扫码结果换 token
   POST https://as.hypergryph.com/user/auth/v1/token_by_scan_code
   Body: { "scanCode": "..." }
   → data.token            # 即 access_token

4) OAuth 授权换 code
   POST https://as.hypergryph.com/user/oauth2/v2/grant
   Body: { "appCode": "4ca99fa6b56cc2ba", "token": "{access_token}", "type": 0 }
   → data.code

5) code 换 cred
   POST https://zonai.skland.com/api/v1/user/auth/generate_cred_by_code
   Body: { "code": "{code}", "kind": 1 }
   → data = { cred, token, userId }
```

- `appCode`：森空岛 = `4ca99fa6b56cc2ba`；官网通行证 = `be36d44aa36bfb5b`（`type=1` 时返回官网 token 而非 code）。
- 扫码链路中 `scanCode` 有时效，建议 2~3 秒轮询一次。

### 2.3 刷新

```
GET https://zonai.skland.com/api/v1/auth/refresh
Header: cred
→ data.token     # 新的签名密钥；cred 本身失效时返回 code 10000/10002
```

---

## 3. 签名（zonai 系列接口）

### 3.1 算法（国服 `zonai.skland.com`）

```
timestamp = 当前秒级时间戳 - 1
sign_fields = {"platform": "", "timestamp": timestamp, "dId": "", "vName": ""}
body_str = GET 时取 URL 的 query 原串（不含 ?）；POST 时取与请求体完全一致的 JSON 字符串
secret_str = path + body_str + timestamp + json.dumps(sign_fields, separators=(",", ":"))
hex_hmac  = HMAC-SHA256(key=token, msg=secret_str).hexdigest()
sign      = MD5(hex_hmac)
```

请求头：

```
cred: {cred}
sign: {sign}
platform: ""
timestamp: "{timestamp}"
dId: ""
vName: ""
User-Agent: Skland/1.32.1 (com.hypergryph.skland; build:103201004; Android 33; ) Okhttp/4.11.0
```

要点：

- `path` 必须是完整路径（如 `/web/v1/game/endfield/card/detail`），`query` 必须与 URL 中完全一致（含参数顺序）。
- POST 的 `body_str` 必须与发送内容逐字符一致，推荐 `json.dumps(body)` 后原样发送。
- `dId` 默认空串；部分风控场景需要真实设备号（通过 `POST https://fp-it.portal101.cn/deviceprofile/v4` 获取，返回值加前缀 `B`），此时 `dId` 同时参与签名与请求头。
- 时间戳与服务端偏差过大会导致 `sign` 校验失败。

### 3.2 Python 实现

```python
import hashlib
import hmac
import json
import time
from urllib.parse import urlparse

SKLAND_UA = "Skland/1.32.1 (com.hypergryph.skland; build:103201004; Android 33; ) Okhttp/4.11.0"


def build_signed_headers(cred: str, token: str, url: str, method: str = "GET", body: dict | None = None) -> dict:
    """构造森空岛签名请求头。

    Args:
        cred: 登录凭据。
        token: 签名密钥（auth/refresh 或 generate_cred_by_code 获取）。
        url: 完整请求 URL（含 query）。
        method: 请求方法，GET/POST。
        body: POST 请求体，需与发送内容一致。
    Returns:
        可直接用于 requests 的请求头字典。
    """
    timestamp = str(int(time.time()) - 1)
    sign_fields = {"platform": "", "timestamp": timestamp, "dId": "", "vName": ""}
    parsed = urlparse(url)
    payload = json.dumps(body) if method.upper() == "POST" and body is not None else parsed.query
    raw = f"{parsed.path}{payload}{timestamp}" + json.dumps(sign_fields, separators=(",", ":"))
    hex_hmac = hmac.new(token.encode(), raw.encode(), hashlib.sha256).hexdigest()
    sign = hashlib.md5(hex_hmac.encode()).hexdigest()
    return {
        "cred": cred,
        "sign": sign,
        "User-Agent": SKLAND_UA,
        "Accept-Encoding": "gzip",
        "Connection": "close",
        **sign_fields,
    }
```

---

## 4. 接口速查

| # | 方法 | URL | 签名 | 用途 |
|---|---|---|---|---|
| 1 | GET | `zonai.skland.com/api/v1/game/player/binding` | 是 | 获取已绑定的游戏与角色列表 |
| 2 | GET | `zonai.skland.com/api/v1/user/teenager` | 是 | 获取账号级 `userId` |
| 3 | GET | `zonai.skland.com/web/v1/game/endfield/card/detail` | 是 | 终末地角色卡片（角色/武器/理智/任务/建设等） |
| 4 | GET/POST | `zonai.skland.com/web/v1/game/endfield/attendance` | 是 | 签到日历 / 执行签到 |
| 5 | GET | `zonai.skland.com/web/v1/game/endfield/attendance/records` | 是 | 历史签到记录 |
| 6 | GET | `zonai.skland.com/api/v1/game/endfield/card/war-echoes` | 是 | 战争回响（对应赛季） |
| 7 | GET | `zonai.skland.com/api/v1/game/player/gameData?uid=` | 否（MCP 简化） | 游戏实时数据（理智/日常） |
| 8 | POST | `binding-api-account-prod.hypergryph.com/account/binding/v1/u8_token_by_uid` | 否 | 换取终末地 `role_token` |
| 9 | GET | `ef-webview.hypergryph.com/api/record/char` | 否 | 角色池抽卡记录 |
| 10 | GET | `ef-webview.hypergryph.com/api/record/weapon` | 否 | 武器池抽卡记录 |
| 11 | GET | `ef-webview.hypergryph.com/api/record/weapon/pool` | 否 | 武器池列表（取 `pool_id`） |
| 12 | GET | `ef-webview.hypergryph.com/api/content` | 否 | 卡池元数据（UP 角色、横幅图） |

---

## 5. 接口明细

### 5.1 获取绑定角色

```
GET https://zonai.skland.com/api/v1/game/player/binding
Headers: 见第 3 节签名头
```

响应（节选）：

```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "appCode": "endfield",
        "appName": "明日方舟：终末地",
        "defaultUid": "xxx",
        "bindingList": [
          {
            "uid": "xxx",
            "nickName": "玩家昵称",
            "isDefault": true,
            "isDelete": false,
            "gameId": 3,
            "gameName": "endfield",
            "channelMasterId": "",
            "channelName": "",
            "roles": [
              {
                "serverId": "1",
                "roleId": "123456789",
                "nickname": "玩家昵称",
                "level": 60,
                "isDefault": true,
                "isBanned": false,
                "serverType": "China",
                "serverName": "China"
              }
            ],
            "defaultRole": null
          }
        ]
      }
    ]
  }
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `appCode` | 游戏标识，终末地为 `endfield`，方舟为 `arknights` |
| `bindingList[].uid` | 森空岛绑定维度下的角色 ID（账号级） |
| `bindingList[].nickName` | 昵称 |
| `bindingList[].roles[]` | 终末地专用：同账号下各服务器角色 |
| `roles[].roleId` | 游戏内 UID（终末地后续接口都用它） |
| `roles[].serverId` | 服务器 ID |
| `roles[].serverType/serverName` | 服务器类型/名称（国服常见 `China`） |

> 方舟（`arknights`）的 `bindingList` 直接就是角色，没有 `roles` 数组；终末地必须再遍历 `roles`。
> `ak-endfield-mcp` 里的 `bindingList[].uid` 被当作 `gameData?uid=` 的入参，`nickName` / `channelName` 被直接读取。

### 5.2 获取账号 userId

```
GET https://zonai.skland.com/api/v1/user/teenager
Headers: 签名头
→ data.teenager.userId
```

角色卡片、战争回响的 `userId` 参数即此值。

### 5.3 终末地角色卡片（核心接口）

```
GET https://zonai.skland.com/web/v1/game/endfield/card/detail?roleId={roleId}&serverId={serverId}&userId={userId}
Headers: 签名头
```

响应顶层结构（`data.detail`）：

```
detail
├── base            # 玩家基础信息
├── chars[]         # 干员列表
├── achieve         # 光荣之路/成就
├── spaceShip       # 帝江号建设
├── domain[]        # 地区建设
├── dungeon         # 理智（体力）
├── bpSystem        # 通行证
├── dailyMission    # 每日活跃
├── weeklyMission   # 每周任务
├── config          # 展示配置
├── currentTs
└── quickaccess[]
```

常用字段：

| 路径 | 说明 |
|---|---|
| `base.roleId` | 游戏内 UID |
| `base.name` | 玩家昵称 |
| `base.level` / `base.worldLevel` | 权限等阶 / 探索等级 |
| `base.avatarUrl` | 头像 |
| `base.charNum` / `base.weaponNum` | 干员数 / 武器数 |
| `base.mainMission.description` | 使命纪事 |
| `base.createTime` / `base.lastLoginTime` / `base.saveTime` | 创建 / 最后登录 / 存档时间 |
| `dungeon.curStamina` / `maxStamina` | 当前 / 最大理智 |
| `dungeon.maxTs` | 理智回满时间（秒级时间戳） |
| `dailyMission.dailyActivation` / `maxDailyActivation` | 每日活跃进度 / 上限 |
| `weeklyMission.score` / `total` | 每周任务进度 / 总量 |
| `bpSystem.curLevel` / `maxLevel` | 通行证等级 |
| `chars[].charData.rarity.key` | `rarity_6` ~ `rarity_3` |
| `chars[].level` / `evolvePhase` / `potentialLevel` | 等级 / 精英化(0-4) / 潜能(0-5) |
| `chars[].userSkills` | `{ skillId: { level, maxLevel } }` |
| `chars[].weapon.weaponData` | 武器数据；`refineLevel`(0-5)、`breakthroughLevel`(0-4) |
| `domain[].name` / `level` / `settlements[]` / `collections` | 地区 / 据点 / 收集进度 |

> 国际服另有一种写法：`GET https://zonai.skport.com/api/v1/game/endfield/card/detail?roleId=&serverId=`，并额外带 `sk-game-role: 3_{roleId}_{serverId}` 头。

### 5.4 终末地签到

```
GET  https://zonai.skland.com/web/v1/game/endfield/attendance          # 本月签到日历
POST https://zonai.skland.com/web/v1/game/endfield/attendance          # 执行签到，body "{}"
GET  https://zonai.skland.com/web/v1/game/endfield/attendance/records  # 历史记录
Headers: 签名头 + sk-game-role: 3_{roleId}_{serverId}
```

签到响应：

```json
{
  "code": 0,
  "data": {
    "ts": "...",
    "awardIds": [{ "id": "item_x", "type": 1 }],
    "resourceInfoMap": { "item_x": { "id": "item_x", "name": "物品名", "count": 5, "icon": "url" } },
    "tomorrowAwardIds": [{ "id": "item_y", "type": 1 }]
  }
}
```

### 5.5 战争回响

```
GET https://zonai.skland.com/api/v1/game/endfield/card/war-echoes?roleId={roleId}&serverId={serverId}&userId={userId}[&seasonId={seasonId}]
Headers: 签名头
→ data.warEchoes
```

`seasonId` 不传则取当前赛季。

### 5.6 换取 role_token（抽卡用）

```
POST https://binding-api-account-prod.hypergryph.com/account/binding/v1/u8_token_by_uid
Body: { "uid": "{bindingList[].uid}", "token": "{官网通行证 token}" }
→ data.token            # role_token
```

> `token` 来自 `oauth2/v2/grant` 的 `type=1`（`appCode=be36d44aa36bfb5b`）返回的 `data.token`。

### 5.7 抽卡记录（无需签名）

```
GET https://ef-webview.hypergryph.com/api/record/char
Params: token={role_token}&server_id={serverId}&lang=zh-cn
        &pool_type={E_CharacterGachaPoolType_*}&seq_id={上一页最后一条 seqId，可选}
```

```
GET https://ef-webview.hypergryph.com/api/record/weapon
Params: token={role_token}&server_id={serverId}&lang=zh-cn&seq_id={可选}
```

```
GET https://ef-webview.hypergryph.com/api/record/weapon/pool
Params: token={role_token}&server_id={serverId}&lang=zh-cn
→ 武器池列表（取 pool_id）
```

```
GET https://ef-webview.hypergryph.com/api/content
Params: pool_id={pool_id}&server_id={serverId}&lang=zh-cn
→ data.pool { pool_name, pool_type, up6_name, up6_image, up5_name, up5_image, rotate_image, all[], rotate_list[] }
```

角色池响应：

```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "kind": "draw",
        "nameText": "角色名",
        "poolId": "special_xxx",
        "poolName": "卡池名",
        "poolVersion": 1,
        "charId": "char_x",
        "charName": "角色名",
        "rarity": 6,
        "isFree": false,
        "isNew": true,
        "gachaTs": "1730000000000",
        "seqId": "10001"
      }
    ],
    "hasMore": true
  }
}
```

`pool_type` 枚举：

| 值 | 含义 |
|---|---|
| `E_CharacterGachaPoolType_Standard` | 常驻池 |
| `E_CharacterGachaPoolType_Special` | 限定池 |
| `E_CharacterGachaPoolType_Beginner` | 新手池 |
| `E_CharacterGachaPoolType_Joint` | 联合池 |
| `E_CharacterGachaPoolType_Rerun` | 复刻池 |

> 翻页：`seq_id` 传上一页**最后一条**（含被过滤的非抽卡项）的 `seqId`；同一 `gachaTs` 的多条属于一次十连。
> 只保留 `kind == "draw"` 的记录（武器池旧响应可能无 `kind` 字段）。

### 5.8 `ak-endfield-mcp` 使用的两个接口

该仓库只实现了两个调用（`src/ak_endfield_mcp/skland.py`）：

```
GET https://zonai.skland.com/api/v1/game/player/binding
GET https://zonai.skland.com/api/v1/game/player/gameData?uid={bindingList[].uid}
Headers: User-Agent + Cred（不带 sign / timestamp / platform / dId / vName）
```

- 只带 `Cred` 头的简化调用适合快速验证 Token 是否有效；**不建议作为正式实现**，部分接口可能返回 `code=10000`（未授权）。
- 它对 `gameData` 的读数路径是 `data.routine.daily/weekly`、`data.status.ap`（`current`/`max`/`completeRecoveryTime`），并自行判断"该游戏的数据 API 可能尚未开放"。
- 若需要稳定获取理智、干员、建设进度，直接用 5.3 的 `card/detail`。

---

## 6. 错误码

| code | 含义 | 处理 |
|---|---|---|
| `0` | 成功 | — |
| `10000` | 未授权（签名错误 / cred 失效 / 缺少 header） | 检查签名 `path`、`query`、`timestamp`；必要时刷新 cred |
| `10002` | 登录失效（cred 过期或被踢） | 重新登录换取 cred |
| 其他非 0 | 业务错误 | 展示 `message`；`data` 一般为空 |

排查顺序：① `cred` 是否有效（先调 `auth/refresh`）；② 签名串是否与 URL 完全一致；③ 时间戳偏差；④ 是否缺少 `sk-game-role`（签到/角色卡片）。

---

## 7. 完整流程示例（Python + requests）

```python
import requests

# build_signed_headers 直接复制 3.2 节的定义到本文件即可

ZONAI = "https://zonai.skland.com"


def get_salt(cred: str) -> str:
    """用 cred 换取签名密钥 token。"""
    resp = requests.get(f"{ZONAI}/api/v1/auth/refresh", headers={"cred": cred}, timeout=30)
    data = resp.json()
    if data.get("code") != 0:
        raise RuntimeError(f"cred 无效: {data.get('message')}")
    return data["data"]["token"]


def get_bindings(cred: str, salt: str) -> list[dict]:
    """获取已绑定的游戏与角色列表。"""
    url = f"{ZONAI}/api/v1/game/player/binding"
    resp = requests.get(url, headers=build_signed_headers(cred, salt, url), timeout=30)
    return resp.json()["data"]["list"]


def get_endfield_card(cred: str, salt: str, user_id: str, role_id: str, server_id: str) -> dict:
    """获取终末地角色卡片详情。"""
    url = f"{ZONAI}/web/v1/game/endfield/card/detail?roleId={role_id}&serverId={server_id}&userId={user_id}"
    resp = requests.get(url, headers=build_signed_headers(cred, salt, url), timeout=30)
    return resp.json()["data"]["detail"]


def get_role_token(uid: str, web_token: str) -> str:
    """用绑定 uid 与官网通行证 token 换取终末地 role_token。"""
    url = "https://binding-api-account-prod.hypergryph.com/account/binding/v1/u8_token_by_uid"
    resp = requests.post(url, json={"uid": uid, "token": web_token}, timeout=30)
    return resp.json()["data"]["token"]


def get_endfield_gacha(role_token: str, server_id: str, pool_type: str | None = None, seq_id: str | None = None) -> dict:
    """获取终末地角色池抽卡记录，seq_id 用于翻页。"""
    params = {"token": role_token, "server_id": server_id, "lang": "zh-cn"}
    if pool_type:
        params["pool_type"] = pool_type
    if seq_id:
        params["seq_id"] = seq_id
    resp = requests.get("https://ef-webview.hypergryph.com/api/record/char", params=params, timeout=30)
    return resp.json()["data"]


def main(cred: str) -> None:
    """串起完整的数据获取流程。"""
    salt = get_salt(cred)
    games = get_bindings(cred, salt)
    endfield = next(g for g in games if g["appCode"] == "endfield")
    for binding in endfield["bindingList"]:
        for role in binding["roles"]:
            card = get_endfield_card(cred, salt, binding["uid"], role["roleId"], role["serverId"])
            print(role["nickname"], card["dungeon"])
```

---

## 8. 注意事项

1. **凭证安全**：`cred` / `token` / `access_token` 均等效于账号密码，禁止硬编码、禁止提交、禁止打印完整值。
2. **频率控制**：签到、查询均有风控，建议单账号每日签到 1 次，数据查询间隔 ≥ 5 秒。
3. **字段兼容**：缺失字段一律按可选处理；`roles[]` 为空时说明该账号只绑定了方舟。
4. **国服/国际服差异**：域名、`appCode`、`sk-language`/`x-language` 头不同，字段语义一致但部分数值（如 `serverId`）取值不同。
5. **合规**：仅用于个人数据查询，不要用于批量爬取或商业分发；接口变更导致失败属正常现象。

---

## 9. 国际服（Skport / Gryphline）差异速览

| 项目 | 国服 | 国际服 |
|---|---|---|
| 游戏数据 | `zonai.skland.com` | `zonai.skport.com` |
| 账号认证 | `as.hypergryph.com` | `as.gryphline.com` |
| 抽卡记录 | `ef-webview.hypergryph.com` | `ef-webview.gryphline.com` |
| OAuth appCode | `4ca99fa6b56cc2ba` | `6eb76d4e13aa36e6` |
| 生成 cred 路径 | `/api/v1/user/auth/generate_cred_by_code` | `/web/v1/user/auth/generate_cred_by_code` |
| 刷新 | `/api/v1/auth/refresh` | `/web/v1/auth/refresh` |
| 语言头 | — | `sk-language: zh-tw`（gryphline 域名用 `x-language`） |
| 服务器 ID | `1` / `China` | 国际服常见 `2` |

国际服签名头示例（来自 skport-api-docs）：

```
headerJson = { "platform": "3", "timestamp": "...", "dId": "", "vName": "1.0.0" }
sign = MD5( HEX( HMAC-SHA256( path + body + timestamp + headerJson, salt ) ) )
```

部分轻量端点使用简化版：`sign = MD5(path + query + timestamp + salt)`。
