# PAT 独立凭证类改造方案（最终版）

> 把「用户自签的长期令牌」从 OAuth access_token 域里拆出来，成为独立凭证类 PAT：独立表、独立命名空间、独立生命周期。
>
> 本文是唯一实施依据。历史版本（v1–v5、整合版）已移入 `docs/oauth/archive/`，仅作论证底稿。

## 1. 结论

**PAT 是「被管理的对象」，不是「请求的凭证」。** 申请、查看、撤销 PAT 时，请求头传的仍是原有两种凭证（`uc-token` 会话 / access_token）。PAT 的消费端是一个**将来新增的独立接口**，目前不存在。

因此改造分两阶段，中间没有必须赶的时间点：

| 阶段 | 内容 | 前置 |
| --- | --- | --- |
| 一 | 建表 + 签发 / 列表 / 撤销 / 轮换。**不动认证链路、不迁移、不下线旧接口** | 无，随时可上 |
| 二 | 认证解析 + 迁移旧数据 + 下线旧接口 + 旧表导出删除 | 消费端接口定稿 |

**阶段一与阶段二必须分开上。** 迁移会对旧令牌执行 `DEL uc:oauth:access:{token}`；如果此时还没有任何代码能校验 PAT，旧 key 一删，这些令牌既不被旧链路认、也不被新链路认，当场全部失效。

## 2. 数据模型

### 2.1 表 `tlc_pat`

```sql
CREATE TABLE `tlc_pat` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`          BIGINT       NOT NULL COMMENT '所属用户',
    `client_id`    VARCHAR(128) NOT NULL COMMENT '个人令牌固定 this_land_cloud',
    `name`         VARCHAR(50)  NOT NULL COMMENT '用户命名的用途',
    `token_hash`   CHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT 'SHA-256 摘要',
    `token_prefix` VARCHAR(16)  NOT NULL COMMENT '前 12 位，列表展示',
    `scope`        VARCHAR(256) NOT NULL COMMENT '权限快照，英文逗号分隔',
    `expires_in`   BIGINT       NOT NULL COMMENT '签发时指定的秒数',
    `expire_time`  DATETIME     NOT NULL COMMENT '到期时间',
    `source`       VARCHAR(16)  NOT NULL COMMENT 'session / legacy',
    `payload`      TEXT             NULL COMMENT '调用方自定义 KV 对象（JSON object）',
    `last_used_at` DATETIME         NULL COMMENT '最近使用（阶段二启用）',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tlc_pat_hash` (`token_hash`),
    KEY `idx_tlc_pat_uid` (`uid`, `create_time`),
    KEY `idx_tlc_pat_client` (`client_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '个人访问令牌';
```

关键约束：

- **只存摘要**，明文仅在签发/轮换响应里出现一次。`token_hash` 用 `COLLATE utf8mb4_bin`，摘要比较不能大小写不敏感。
- **`client_id` NOT NULL，个人令牌固定 `this_land_cloud`**。它在系统里同时是配置数据的分区键（`UserConfigServiceImpl.requireClientId()`），置空会让 `/oauth2/config/**` 抛「缺少来源客户端标识」，且与存量分区不一致。
- **`payload` 是 KV 对象**，Java 侧 `Map<String,Object>`。只校验「是 object 且序列化后 ≤ 4KB」，不解读键含义、不做键名白名单、不据其分支。`{}` 与 `NULL` 等价。用 `TEXT` 而非 MySQL `JSON` 列：MyBatis 按 `String` 映射，`JSON` 列在部分版本取值存在格式差异，会让「原样取出」打折。
- 明文入库、明文返回，**不得放密钥或敏感信息**。

### 2.2 Redis（按需，默认不用）

| Key | 值 | TTL |
| --- | --- | --- |
| `tlc:pat:{sha256}` | `{"uid":..,"clientId":..,"scope":..,"payload":{...}}` | 至 `expire_time` |
| `tlc:pat:{sha256}`（撤销后） | `{"revoked":true}` | 24h 墓碑 |
| `tlc:uid:pat:{uid}` | Set，成员为 `{sha256}` | 无 |
| `tlc:pat:miss:{sha256}` | `1` | 60s 负缓存 |
| `tlc:pat:lock:{sha256}` | `1` | 5s 单飞锁 |

- `payload` 以**嵌套对象**存放，不二次转义：它是 KV 对象，嵌在 `payload` 键下与外层字段无冲突，读取时用 `ObjectMapper` 转 `Map<String,Object>`。
- 容量：单条记录约 130 字节 ~ 4KB。全打满 2 万条约 80MB，属最坏情况。
- **默认不建这些键。** 5.1 的校验是「按 hash 查一次库」，PAT 调用量远低于普通接口，直接读 DB 即可；缓存、负缓存、单飞、墓碑都是为高频校验准备的，等真出现性能压力再加。
- 若确实要加，`RedisKeyUtil` 的四个方法（`pat` / `uidPat` / `patMiss` / `patLock`）在阶段一先备着，不加也不影响任何功能。

存量 `uc:oauth:access:` / `uc:oauth:refresh:` / `uc:uid:oauth:` 一律不动。

## 3. API

### 3.1 新增

| 方法 | 路径 | 认证 | 说明 |
| --- | --- | --- | --- |
| POST | `/user/pat` | 会话 | 签发。`name`、`expires_in` 必填；`scope`、`client_id`、`payload` 可选。明文仅此一次 |
| GET | `/user/pat/list` | 会话 | 我的令牌，仅元数据（含 `payload`） |
| POST | `/user/pat/revoke` | 会话 | 按 `id` 撤销 |
| POST | `/user/pat/revoke-all` | 会话 | 作废本用户全部 PAT |
| POST | `/user/pat/rotate` | 会话 | 按 `id` 轮换，`revokeOld` 默认 false；`payload` 缺省继承 |
| GET | `/oauth2/pat/list` | access_token | 应用视角只读列表，准入 `direct_auth_enabled=1` |
| POST | `/internal/pat-migration/run` | 无（仅存在于一次性构建） | 迁移触发，跑完即删，阶段二才用 |

拦截器注册：

- `/user/**` 已被 `UserAuthInterceptor` 通配覆盖，无需额外配置。
- **`/oauth2/pat/**` 必须显式加进 `WebConfig` 的 `addPathPatterns`**。当前按固定路径注册，漏配的表现是「接口能访问但没校验过」——这是最危险的一类失误。验证时要特意测「不带 token」，只有这个场景会暴露。
- `/internal/**` 不在任何拦截器范围内（现状天然满足）。

列表用 `/list` 而非资源根，给以后的 `GET /user/pat/{id}` 让路——否则 `{id}` 会捕获 `list`。

### 3.2 下线（阶段二）

`GET/POST /user/oauth/custom-tokens`、`POST /user/oauth/custom-tokens/revoke`、`GET /oauth2/custom-tokens`、`POST /oauth2/custom-tokens/revoke`、`POST /oauth2/token/from-refresh`，共 6 个，统一返回 `410 Gone`。

服务端要长期凭证时的人工替代：用户在 `/user/pat` 签一把（可设 10 年），明文粘进该服务配置。

> **不要反过来让后端改用标准刷新 `/oauth2/token`**：refresh 轮转带重放检测，多实例并发刷新会互相踢死整族令牌。这正是 `from-refresh` 当初存在的理由，下线它不等于退回标准刷新。

## 4. 阶段一语义

- **不写 Redis、不加认证分派**：`OAuthTokenResolver` 一字不改，存量 access_token 零影响。
- **撤销 = 删行**：`DELETE FROM tlc_pat WHERE id = ? AND uid = ?`。不需要软删或墓碑——没有缓存会把记录写回来。
- **`scope` 是快照**：签发时的权限不随用户后续授权调整回溯；轮换时按当前授权重算（不直接复制历史 scope）。
- **`scope` 只能收敛**：沿用 `narrowScope`，只能减不能增；元范围 `all` 不允许自助签发。
- **`revoke-all`** 按 `uid` 删行。
- **`last_used_at` 阶段一不更新**：没有校验路径可挂。
- **`payload` 写入点只有三处**：签发、轮换、迁移（`null`）。当前无修改接口，故不存在「DB 改了缓存没改」的问题。

## 5. 阶段二

### 5.1 校验归属：在消费端接口内部完成

PAT 不进 `/oauth2/**` 的 access_token 解析链路。消费端是新增的独立接口，它收到 PAT 后自己校验：

```
hash = SignUtil.sha256(pat)
row  = SELECT uid, client_id, scope, expire_time FROM tlc_pat WHERE token_hash = hash AND expire_time > NOW()
row 为 null → 401；否则按 row.scope 判定权限
```

即 `OAuthTokenResolver`、`OAuthAuthInterceptor` 以及全部 `/oauth2/**` 资源端点**一行都不用改**。这也是阶段一与本方案其余部分的共同前提：PAT 从不作为请求凭证出现在既有链路上。

> 备选（仅在需求变化时考虑）：若将来要让 PAT 直接调用 `/oauth2/**` 资源接口，才需要加分派层，且顺序必须是「先原有 access_token，miss 后再查 PAT」——反过来会让每次 `/oauth2/**` 调用都白付一次 Redis miss + 一次 SHA-256。届时再引入 2.2 的缓存与墓碑。

### 5.2 迁移接口

```http
POST /internal/pat-migration/run
```

**无参数、无鉴权、同步执行**，跑完返回统计后就没用了：

```json
{ "total":20413, "migrated":20101, "skipped":312, "failed":0, "elapsedMs":8421 }
```

行为固定：按 5.3 全量执行一次。重复调用幂等，不会翻倍。

**这个接口只存在于一次性构建里，跑完立即回滚到不含它的版本。** 别把它留在正式版本中——留着就等于在生产上挂了一个「任何人访问即触发一次不可逆全表迁移」的按钮，哪怕只多存在一天。

**回滚不靠接口，靠旧表。** 旧表明文完整保留，需要退回旧版本代码时，用一段一次性脚本把未过期行重灌回旧桶（就是 5.3 的逆操作）：

```
FOR EACH row IN oauth_custom_token WHERE expire_time > NOW()
    SET  uc:oauth:access:{row.token} {"uid","clientId","scope"} EX (expire_time - now)
    SADD uc:uid:oauth:{uid} access:{row.token}
END
```

先跑这段、再回滚 jar，迁移过的令牌就全部恢复。直接回滚 jar 会让它们失效——旧版本只查 `uc:oauth:access:`，而那些 key 已被迁移删除。

### 5.3 迁移动作

```
FOR EACH row IN oauth_custom_token WHERE expire_time > NOW()   -- 游标：WHERE id > ? ORDER BY id LIMIT n
    raw = GET uc:oauth:access:{row.token}
    IF raw == null        → skipped++ ; CONTINUE              -- 已失效，不迁
    hash = SignUtil.sha256(row.token)
    INSERT IGNORE tlc_pat(uid, client_id, name, token_hash=hash, token_prefix=LEFT(token,12),
                          scope, expires_in, expire_time, source='legacy', payload=NULL)
    SREM  uc:uid:oauth:{uid} access:{row.token}               -- 摘出 OAuth 索引
    DEL   uc:oauth:access:{row.token}                         -- 删旧 key
    migrated++
END
```

1. **旧表只读**：不删行、不改名、不清列。迁移前后 `CHECKSUM TABLE` 应一致，可随时与备份逐行比对。
2. **`GET` 判空是唯一分支**：不加会把「已被吊销但记录还在」的行迁成可用 PAT——`revokeClientAuthorization` 一直只删 Redis 不删记录，存量里必有一批。
3. **`SREM` 与 `DEL` 都要**：`SREM` 让它脱离 OAuth 索引（否则批量吊销仍会删它、与 PAT 语义冲突）；`DEL` 消除双副本（否则按 id 撤销删不掉旧副本）。
4. **顺序固定** `INSERT → SREM → DEL`：先保证新表可查，再拆旧桶。默认不写 `tlc:` 缓存键（见 2.2）；将来启用缓存后，首次校验会按 hash 自动回填。
5. **幂等**：`INSERT IGNORE` + `SREM`/`DEL` 均幂等；重跑到已处理行时 `GET` 已空 → 跳过。失败行计入 `failed`，不阻断后续。

### 5.4 对账

```sql
SELECT COUNT(*) FROM tlc_pat WHERE source='legacy';                 -- == migrated
SELECT COUNT(*) FROM oauth_custom_token WHERE expire_time > NOW();  -- == total
CHECKSUM TABLE oauth_custom_token;                                   -- 迁移前后一致
```

再抽查 10 条（从旧表取明文）调 `GET /oauth2/userinfo`，应全部 200。

## 6. 安全事件矩阵

| 事件 | 会话 | OAuth 令牌 | PAT |
| --- | --- | --- | --- |
| `resetPassword`（邮箱验证码） | 全踢 | 全吊销 | **全吊销** |
| `updatePassword`（旧密码） | 全踢 | 不动 | 不动 |
| `deleteClient` | — | 按 clientId 清理 | **按 clientId 清理** |
| 用户撤销某应用授权 | — | 按 uid + clientId 清理 | **不动**（缺口，见第 7 节） |

- `AuthServiceImpl.resetPassword:259-268` 目前只调 `kickAllSessions`，需追加 `patService.revokeAllByUid(uid)`。只改未被调用的 `revokeUser` 编排无效。
- `updatePassword` 保持只踢会话，代码注释写明与 `resetPassword` 的差异，避免后人「统一」。
- `deleteClient` 靠 `revokeByPrefix` 扫旧键，碰不到 `tlc_pat`，必须补 `DELETE FROM tlc_pat WHERE client_id = ?`。

前三条在阶段一即可实现（都是删行），不必等消费端。

## 7. 已知缺口

| 缺口 | 后果 | 何时处理 |
| --- | --- | --- |
| PAT 暂无消费方 | 签发出的字符串暂时调不了任何接口 | 阶段二。若在此之前开放签发，页面须标注「暂不可用」 |
| 家族索引成员未改写 | 迁移来的 legacy 令牌在刷新重放时不参与整族吊销 | 阶段二，迁移时顺手加两行 `SREM`/`SADD` |
| 撤销应用授权不清 `tlc_pat` | 用户撤销某应用授权后，该应用名下旧长期令牌仍可用 | 阶段二，一句 `DELETE ... WHERE uid=? AND client_id=? AND source='legacy'` |
| 并发撤销无栅栏 | 迁移期间撤销，少数令牌可能「撤销后又被写回」 | 迁移时停服，或接受 |
| 孤儿令牌（Redis 有、旧表无） | 迁不了，留在旧桶自然过期 | 接受 |
| 旧表明文常驻 | 库里仍有 2 万条可用明文 | 阶段二导出并删除 |

## 8. 落地

不排日期，按批次推进，每批可独立上线、独立回滚。

| 批次 | 内容 | 完成判据 |
| --- | --- | --- |
| 1 | 建表 + 实体 `TlcPat` + `TlcPatMapper` + `PatUtil` + `RedisKeyUtil` 四方法 | 建表成功；`generate` 输出 72 字符；`hash` 与 `SignUtil.sha256` 一致 |
| 2 | 五个管理接口 + `/oauth2/pat/list` + `WebConfig` 注册 | 不带 token 访问 `/oauth2/pat/list` 返回 `90009` |
| 3 | `resetPassword` / `deleteClient` 接线 | 重置密码后 PAT 行被删；删客户端后其名下 PAT 行被删 |
| 4 | 迁移 Controller（不执行） | 先在测试库跑通：`migrated + skipped == total`，重跑不翻倍 |
| 5 | 阶段二：定校验形态，补解析或 introspection | 按第 5.1 节 |
| 6 | 执行迁移 + 对账 | 第 5.4 节三项全过 |
| 7 | 旧 6 端点 `410`、前端切换、导出删旧表 | 旧端点日志归零 |

批次 1–4 是阶段一，**不改变任何既有行为**，回滚只需回滚 jar + `DROP TABLE tlc_pat`。

## 9. 验证清单

**单测**

- `PatUtil`：`generate` 输出 `tlc_pat_` + 64 hex；`isPat` 对旧 64 位 hex 返回 `false`
- 签发：`name`/`expires_in` 缺失或超上限报 `10001`；`scope` 提权被拒
- 撤销：跨用户 `id` 幂等成功且不误删；撤销后查不到
- `revoke-all`：只删本人，不影响他人
- `payload`：非 object（数组/字符串）报错；超 4KB 报错；`{}` 落库为 `NULL`
- 轮换：`revokeOld=false` 新旧并存；`true` 旧行被删

**冒烟**

- 签发 → 列表出现（只有 `tokenPrefix`）→ 撤销 → 列表消失
- `payload` 传 `{"a":1,"b":{"c":true}}`，列表原样返回
- `/oauth2/pat/list` 无 token → `90009`；有 access_token → 只返回该用户记录

**零影响专项**

- 旧 5 个 `custom-tokens` 端点与 `/oauth2/token/from-refresh` 行为不变
- `CHECKSUM TABLE oauth_custom_token` 上线前后一致
- 存量 access_token 校验正常（旧令牌打 `/oauth2/userinfo` 仍 200）
