# UC / uc 替换为 TCL / tcl：代码替换明细

更新日期：2026-11-19

本文与 [详细总清单](/C:/dev_projects/UserCenterV1/docs/UC缩写替换为TCL清单.md) 配套。**已按本明细完成源码替换并同步接口文档；未部署；完整测试触发了真实数据库统计写入，详见文末。文中“当前代码”、删除片段和原行号为执行前基线，目标代码已应用；注释以最终源码表述为准。**

本轮范围：请求头和跨站票据参数新增 TCL 名称并兼容 UC；14 个短期 Redis 前缀直接改为 tcl，不做 Redis 旧键兼容；9 个会话/令牌关联前缀保留。用户已接受旧短期凭证失效、登录/授权流程中断、计数/锁/nonce 状态重置及可能需要重新登录等影响。API URL、凭证值、clientId、现有 tlc 拼写及浏览器存储键均不连带替换。

文中有两类代码：

- **明确的替换片段**：保留执行前代码和已应用的目标代码，便于核对差异；最终以当前源码为准。
- **Redis 调用核对位置**：现有调用和 Lua 协议保持原样，14 个常量改名后统一使用新键；只核对覆盖范围，不新增旧键兼容层。请求头、请求参数仍保留第 2 节的新旧名称解析。

## 1. 文件与改动总览

| 文件 | 需要修改或仅需核对的代码 | 关联 API |
| --- | --- | --- |
| [RequestUtil.java:45](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RequestUtil.java:45) | 统一解析 TCL/UC 令牌头；新增来源客户端头、票据参数的兼容解析方法 | 总清单 B/C/D 组及 `POST /auth/logout`；新增 helper 由登录/注册、OAuth 调用 |
| [UserAuthInterceptor.java:104](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/interceptor/UserAuthInterceptor.java:104) | 去除重复的令牌头解析，委托 RequestUtil；清理冗余常量 | 总清单 A 组 |
| [AuthController.java:33](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:33) | 两处来源客户端头读取改为统一 helper，删除旧单一头常量 | `POST /auth/register`、`POST /auth/login` |
| [OAuthController.java:186](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:186) | ucTicket 改名，使用统一票据参数解析 | `GET /oauth2/authorize`、`POST /oauth2/authorize` |
| [OAuthTokenServiceImpl.java:150](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:150) | 使用统一票据参数解析；短期 Redis 调用保持原样，键随常量切换 | 总清单 C 组及 `POST /oauth2/token` 授权码兑换分支 |
| [AuthServiceImpl.java:704](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:704) | 修改提示语；直连票据、失败计数、锁、限流方法体保持原样，允许短期状态重置 | 登录/注册、直连认证；失败和锁为主站/直连登录共用 |
| [RedisKeyUtil.java:16](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:16) | 直接替换 14 个前缀常量及注释；不新增 legacy 或旧键回退 | 总清单第 4 节逐项列出的接口 |
| [EmailCodeServiceImpl.java:88](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:88) | 现有验证码读写/删除和发送限流保持原样，统一使用新短期键 | 发码、登录、注册、重设密码、绑定/换绑邮箱及直连认证 |
| [RedisOAuthTokenStore.java:46](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java:46) | 读取、used 判断、Lua 传参语句保持原样，构建结果变为新前缀 | `POST /oauth2/token` |
| [oauth-code-consume.lua:8](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-code-consume.lua:8) | 正文及 KEYS/ARGV 结构保持原样，由 Java 传入新前缀的授权码和 used 键 | `POST /oauth2/token` |
| [RedisRateLimiter.java:28](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisRateLimiter.java:28) | 方法体和新计数器 TTL/额度规则保留，不继承旧计数 | 所有使用该通用限流器的接口 |
| [OAuthMigrateServiceImpl.java:149](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:149) | nonce/限流语句及验签时窗保持原样，使用新前缀，不继承旧 nonce/额度 | `POST /oauth2/internal/migrate-token` |
| [AkAccountServiceImpl.java:475](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AkAccountServiceImpl.java:475) | 2 秒 setIfAbsent 上传短锁语句不改，键随常量切换 | `POST /user/ak-accounts/operators/save`、`POST /oauth2/ak-accounts/operators/save` |
| [EfPlayerDataServiceImpl.java:272](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EfPlayerDataServiceImpl.java:272) | 2 秒 setIfAbsent 上传短锁语句不改，键随常量切换 | `POST /user/ef-accounts/player-data/save`、`POST /oauth2/ef-accounts/player-data/save` |
| [EfPoolRecordTaskService.java:107](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/EfPoolRecordTaskService.java:107) | 创建任务限流语句不改，键随常量切换 | `POST /user/ef-pool-records/create-task`、`POST /oauth2/ef-pool-records/create-task` |

上表为 15 个运行相关文件。需要执行代码替换的是第 2 节六个文件及 RedisKeyUtil；其余运行文件列为调用核对位置，按当前代码无需为 Redis 改名新增逻辑或修改 Lua。另有第 6 节逐文件列出的业务注释和测试配置注释。

## 2. 请求头、参数与局部变量的替换代码

### 2.1 RequestUtil.java：集中实现新旧名称解析

涉及位置：[RequestUtil.java:45](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RequestUtil.java:45)。

当前 resolveToken：

```java
    public static String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String ucToken = request.getHeader("UC-Token");
        if (ucToken != null && !ucToken.isBlank()) {
            return ucToken.trim();
        }
        return null;
    }
}
```

已增加的 import（现有 jakarta.servlet 导入及其余代码保留）：

```java
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
```

已替换/新增的方法如下。新旧非空值 trim 后不一致时返回参数错误 10001；有 Authorization: Bearer 时保持原来的优先返回行为。该冲突策略已写入源码并通过测试。

```java
public static String resolveToken(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
        return authorization.substring(BEARER_PREFIX.length()).trim();
    }
    return compatibleValue(
            request.getHeader("TCL-Token"),
            request.getHeader("UC-Token"),
            "TCL-Token / UC-Token");
}

public static String resolveClientId(HttpServletRequest request) {
    return compatibleValue(
            request.getHeader("TCL-Client-Id"),
            request.getHeader("UC-Client-Id"),
            "TCL-Client-Id / UC-Client-Id");
}

public static String resolveLoginTicket(HttpServletRequest request) {
    return compatibleValue(
            request.getParameter("tcl_ticket"),
            request.getParameter("uc_ticket"),
            "tcl_ticket / uc_ticket");
}

private static String compatibleValue(String current, String legacy, String field) {
    String currentValue = normalizeCompatibleValue(current);
    String legacyValue = normalizeCompatibleValue(legacy);
    if (currentValue != null && legacyValue != null
            && !currentValue.equals(legacyValue)) {
        throw new BusinessException(ResultCode.PARAM_ERROR,
                "新旧参数值不一致（" + field + "）");
    }
    return currentValue != null ? currentValue : legacyValue;
}

private static String normalizeCompatibleValue(String value) {
    return value == null || value.isBlank() ? null : value.trim();
}
```

原有 getIp、BEARER_PREFIX 保留；这里的 ucToken 局部变量随实现消失，无需另保留一个 tclToken 变量。旧 UC 名称的字面量在兼容代码中必须继续存在，不能要求全仓库 UC 搜索结果归零。

### 2.2 UserAuthInterceptor.java：统一取头规则

涉及位置：[UserAuthInterceptor.java:31](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/interceptor/UserAuthInterceptor.java:31)、104 行。新增 import：

```java
import com.orange.common.util.RequestUtil;
```

当前方法：

```java
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        String ucToken = request.getHeader("UC-Token");
        return (ucToken == null || ucToken.isBlank()) ? null : ucToken.trim();
    }
```

目标方法：

```java
private String resolveToken(HttpServletRequest request) {
    return RequestUtil.resolveToken(request);
}
```

该类原来的 Bearer 常量随重复逻辑删除；RequestUtil 中同名常量继续保留：

```diff
-    /** Authorization 请求头前缀 */
-    private static final String BEARER_PREFIX = "Bearer ";
```

现有调用 `String token = resolveToken(request);`、会话 Redis key 和用户上下文注入逻辑不变。

### 2.3 AuthController.java：替换两处客户端头读取

涉及位置：[AuthController.java:33](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:33)、73、90 行。该文件已有 RequestUtil 导入，不重复新增。

删除只支持旧名称的常量及其紧邻的说明注释：

```diff
-    /** 来源客户端标识请求头（登录 / 注册必填，由服务层校验，缺失返回 10001） */
-    private static final String HEADER_CLIENT_ID = "UC-Client-Id";
```

register 和 login 方法中的以下语句各替换一次，共两处：

```diff
-String clientId = httpRequest.getHeader(HEADER_CLIENT_ID);
+String clientId = RequestUtil.resolveClientId(httpRequest);
```

不要改动 clientId 的值、服务入参、会话字段、数据库列名和必填/长度校验；不是把每个客户端的既有 ID 从 uc 改成 tcl。

### 2.4 OAuthController.java：参数与局部变量

涉及位置：[OAuthController.java:186](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:186)、190 行。新增 import：

```java
import com.orange.common.util.RequestUtil;
```

替换片段：

```diff
-String ucTicket = request.getParameter("uc_ticket");
+String tclTicket = RequestUtil.resolveLoginTicket(request);
```

```diff
-if (StringUtils.hasText(ucTicket)) {
+if (StringUtils.hasText(tclTicket)) {
```

这里只用于日志中的“是否带票据”判断；身份解析仍由服务层执行。不能在 Controller 中额外消费一次票据。

### 2.5 OAuthTokenServiceImpl.java：实际身份解析入口

涉及位置：[OAuthTokenServiceImpl.java:150](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:150)；该文件已有 RequestUtil 导入。

```diff
-Long uid = resolveUidByTicket(request.getParameter("uc_ticket"));
+Long uid = resolveUidByTicket(RequestUtil.resolveLoginTicket(request));
```

该一处代码被以下 4 个服务方法共用，覆盖 5 个 HTTP 方法与路径组合：

| 调用点 | 受影响 API |
| --- | --- |
| createLoginTicket，211 行 | `POST /oauth2/ticket` |
| buildAuthorizeRedirectUrl，258 行 | `GET /oauth2/authorize`、`POST /oauth2/authorize` |
| getConsentInfo，409 行 | `GET /oauth2/consent/info` |
| confirmAuthorization，460 行 | `POST /oauth2/consent` |

这里仍只选出一枚票据后调用 resolveUidByTicket 一次。JSON 请求体不新增 tcl_ticket 字段，getParameter 读取 query / 表单参数的方式不变。

### 2.6 AuthServiceImpl.java：提示语

涉及位置：[AuthServiceImpl.java:704](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:704)。

```diff
-throw new BusinessException(ResultCode.PARAM_ERROR, "缺少来源客户端标识（UC-Client-Id）");
+throw new BusinessException(ResultCode.PARAM_ERROR,
+        "缺少来源客户端标识（TCL-Client-Id，兼容 UC-Client-Id）");
```

长度限制和错误码保持不变。

## 3. RedisKeyUtil.java：14 个短期前缀的目标代码

以下 diff 即本次确认的**直接替换目标**：修改 14 个常量，不读取、复制、迁移或回退旧短期键，不新增 legacy 构建入口。用户接受旧短期凭证失效、计数重新累计、旧锁失效及旧 nonce 记录不继承；新键仍沿用原有 TTL 和处理逻辑。

前缀常量名及现有公开构建方法名不改，业务名与后缀不改。位置：[RedisKeyUtil.java:16](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:16)。

位置：[RedisKeyUtil.java:16](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:16)。

```diff
-    private static final String PREFIX_CODE = "uc:code:";
+    private static final String PREFIX_CODE = "tcl:code:";
```

位置：[RedisKeyUtil.java:19](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:19)。

```diff
-    private static final String PREFIX_LOGIN_FAIL = "uc:login:fail:";
+    private static final String PREFIX_LOGIN_FAIL = "tcl:login:fail:";
```

位置：[RedisKeyUtil.java:22](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:22)。

```diff
-    private static final String PREFIX_LOGIN_LOCK = "uc:lock:";
+    private static final String PREFIX_LOGIN_LOCK = "tcl:lock:";
```

位置：[RedisKeyUtil.java:25](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:25)。

```diff
-    private static final String PREFIX_RATE = "uc:rate:";
+    private static final String PREFIX_RATE = "tcl:rate:";
```

位置：[RedisKeyUtil.java:28](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:28)。

```diff
-    private static final String PREFIX_OAUTH_CODE = "uc:oauth:code:";
+    private static final String PREFIX_OAUTH_CODE = "tcl:oauth:code:";
```

位置：[RedisKeyUtil.java:31](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:31)。

```diff
-    private static final String PREFIX_OAUTH_CODE_USED = "uc:oauth:code:used:";
+    private static final String PREFIX_OAUTH_CODE_USED = "tcl:oauth:code:used:";
```

位置：[RedisKeyUtil.java:49](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:49)。

```diff
-    private static final String PREFIX_OAUTH_TICKET = "uc:oauth:ticket:";
+    private static final String PREFIX_OAUTH_TICKET = "tcl:oauth:ticket:";
```

位置：[RedisKeyUtil.java:52](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:52)。

```diff
-    private static final String PREFIX_OAUTH_TICKET_USED = "uc:oauth:ticket:used:";
+    private static final String PREFIX_OAUTH_TICKET_USED = "tcl:oauth:ticket:used:";
```

位置：[RedisKeyUtil.java:55](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:55)。

```diff
-    private static final String PREFIX_OAUTH_CONSENT = "uc:oauth:consent:";
+    private static final String PREFIX_OAUTH_CONSENT = "tcl:oauth:consent:";
```

位置：[RedisKeyUtil.java:58](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:58)。

```diff
-    private static final String PREFIX_OAUTH_CONSENT_USED = "uc:oauth:consent:used:";
+    private static final String PREFIX_OAUTH_CONSENT_USED = "tcl:oauth:consent:used:";
```

位置：[RedisKeyUtil.java:67](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:67)。

```diff
-    private static final String PREFIX_DIRECT_CHANNEL = "uc:oauth:direct:channel:";
+    private static final String PREFIX_DIRECT_CHANNEL = "tcl:oauth:direct:channel:";
```

位置：[RedisKeyUtil.java:70](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:70)。

```diff
-    private static final String PREFIX_DIRECT_TICKET = "uc:oauth:direct:ticket:";
+    private static final String PREFIX_DIRECT_TICKET = "tcl:oauth:direct:ticket:";
```

位置：[RedisKeyUtil.java:73](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:73)。

```diff
-    private static final String PREFIX_DIRECT_USED = "uc:oauth:direct:used:";
+    private static final String PREFIX_DIRECT_USED = "tcl:oauth:direct:used:";
```

位置：[RedisKeyUtil.java:76](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:76)。

```diff
-    private static final String PREFIX_OAUTH_MIGRATE_NONCE = "uc:oauth:migrate:nonce:";
+    private static final String PREFIX_OAUTH_MIGRATE_NONCE = "tcl:oauth:migrate:nonce:";
```

### 3.1 对应的前缀注释

以上常量前一行的 key 示例，随最终切换从 uc: 改成 tcl:。不添加 Redis 旧前缀兼容说明；第 3.3 节保留的九类前缀注释不改。

```diff
-    /** 邮箱验证码 key 前缀：uc:code:{email} */
+    /** 邮箱验证码 key 前缀：tcl:code:{email} */
```

```diff
-    /** 登录失败计数 key 前缀：uc:login:fail:{account} */
+    /** 登录失败计数 key 前缀：tcl:login:fail:{account} */
```

```diff
-    /** 登录锁定 key 前缀：uc:lock:{account} */
+    /** 登录锁定 key 前缀：tcl:lock:{account} */
```

```diff
-    /** 通用限流 key 前缀：uc:rate:{biz}:{target} */
+    /** 通用限流 key 前缀：tcl:rate:{biz}:{target} */
```

```diff
-    /** OAuth 授权码 key 前缀：uc:oauth:code:{code} */
+    /** OAuth 授权码 key 前缀：tcl:oauth:code:{code} */
```

```diff
-    /** OAuth 授权码已使用标记 key 前缀：uc:oauth:code:used:{code} */
+    /** OAuth 授权码已使用标记 key 前缀：tcl:oauth:code:used:{code} */
```

```diff
-    /** OAuth 跨站登录票据 key 前缀：uc:oauth:ticket:{ticket} */
+    /** OAuth 跨站登录票据 key 前缀：tcl:oauth:ticket:{ticket} */
```

```diff
-    /** OAuth 跨站登录票据已使用标记 key 前缀：uc:oauth:ticket:used:{ticket} */
+    /** OAuth 跨站登录票据已使用标记 key 前缀：tcl:oauth:ticket:used:{ticket} */
```

```diff
-    /** OAuth 授权确认单 key 前缀：uc:oauth:consent:{pendingId} */
+    /** OAuth 授权确认单 key 前缀：tcl:oauth:consent:{pendingId} */
```

```diff
-    /** OAuth 授权确认单已使用标记 key 前缀：uc:oauth:consent:used:{pendingId} */
+    /** OAuth 授权确认单已使用标记 key 前缀：tcl:oauth:consent:used:{pendingId} */
```

```diff
-    /** 直连登录发起会话 key 前缀：uc:oauth:direct:channel:{channel}（前端登录的前置凭证） */
+    /** 直连登录发起会话 key 前缀：tcl:oauth:direct:channel:{channel}（前端登录的前置凭证） */
```

```diff
-    /** 直连登录一次性票据 key 前缀：uc:oauth:direct:ticket:{ticket}（前端登录成功后的凭证） */
+    /** 直连登录一次性票据 key 前缀：tcl:oauth:direct:ticket:{ticket}（前端登录成功后的凭证） */
```

```diff
-    /** 直连登录票据已使用标记 key 前缀：uc:oauth:direct:used:{ticket} */
+    /** 直连登录票据已使用标记 key 前缀：tcl:oauth:direct:used:{ticket} */
```

```diff
-    /** 迁移兑换请求防重放 key 前缀：uc:oauth:migrate:nonce:{nonce} */
+    /** 迁移兑换请求防重放 key 前缀：tcl:oauth:migrate:nonce:{nonce} */
```

### 3.2 不新增旧键构建方法或兼容逻辑

先前草案中的 14 个 legacy* 旧键构建方法已从本方案删除，不需要新增这些方法。也不新增兼容存储层、双读双写、旧键回退、旧值复制、迁移 Lua 或额外部署开关。

现有 code、loginFail、loginLock、rate、oauthCode、oauthCodeUsed、oauthTicket、oauthTicketUsed、oauthConsent、oauthConsentUsed、directChannel、directTicket、directUsed、oauthMigrateNonce 方法名和签名保持原样，只由对应常量返回新的 tcl: 键。旧键自然过期，无需等待后才部署。

### 3.3 不替换的九个常量：保留原代码

```java
    private static final String PREFIX_TOKEN = "uc:token:";
    private static final String PREFIX_OAUTH_ACCESS = "uc:oauth:access:";
    private static final String PREFIX_OAUTH_REFRESH = "uc:oauth:refresh:";
    private static final String PREFIX_OAUTH_REFRESH_USED = "uc:oauth:refresh:used:";
    private static final String PREFIX_OAUTH_REFRESH_NEXT = "uc:oauth:refresh:next:";
    private static final String PREFIX_OAUTH_FAMILY = "uc:oauth:family:";
    private static final String PREFIX_UID_SESSION = "uc:uid:session:";
    private static final String PREFIX_UID_OAUTH = "uc:uid:oauth:";
    private static final String PREFIX_OAUTH_MIGRATE_CURRENT = "uc:oauth:migrate:current:";
```

其中 PREFIX_OAUTH_REFRESH_NEXT 虽然只有 60 秒，也作为刷新令牌关联状态保留。会话扫描表达式 `"uc:token:*"`、现有 tlc PAT 前缀、令牌值和索引成员的 `access:/refresh:` 标识均不改。

## 4. 短期 Redis 的全部构建调用位置（44 行）

以下逐条列出现有 Java 中这 14 类方法的调用语句及位置，同一行可能包含多个构建调用。它们是**调用核对位置，不要求逐行修改**；现有方法名、签名和调用语句保持原样，前缀变化由 RedisKeyUtil 常量统一生效，不接入兼容存储层。多行语句按命中行展示，省略的后续参数仍沿用源码。

| 涉及位置 | 当前代码（命中行） | 目标处理 |
| --- | --- | --- |
| [EfPoolRecordTaskService.java:107](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/EfPoolRecordTaskService.java:107) | `String limitKey = RedisKeyUtil.rate(RATE_LIMIT_BIZ, SignUtil.sha256(hgToken));` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AkAccountServiceImpl.java:475](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AkAccountServiceImpl.java:475) | `String key = RedisKeyUtil.rate(RATE_LIMIT_BIZ, SignUtil.sha256(akUid));` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AuthServiceImpl.java:394](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:394) | `writeJson(RedisKeyUtil.directChannel(channel), record, oauthDirectProperties.getChannelTtlSeconds());` | 调用语句保持；只使用新 channel，旧 channel 无效后重新发起直连流程。 |
| [AuthServiceImpl.java:433](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:433) | `stringRedisTemplate.delete(RedisKeyUtil.directChannel(channel));` | 调用语句保持；只使用新 channel，旧 channel 无效后重新发起直连流程。 |
| [AuthServiceImpl.java:462](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:462) | `stringRedisTemplate.delete(RedisKeyUtil.directChannel(channel));` | 调用语句保持；只使用新 channel，旧 channel 无效后重新发起直连流程。 |
| [AuthServiceImpl.java:493](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:493) | `Map<String, Object> channelRecord = readJsonMap(RedisKeyUtil.directChannel(channel));` | 调用语句保持；只使用新 channel，旧 channel 无效后重新发起直连流程。 |
| [AuthServiceImpl.java:506](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:506) | `if (!RedisRateLimiter.tryAcquire(stringRedisTemplate, RedisKeyUtil.rate("direct-session-ip", ip),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AuthServiceImpl.java:509](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:509) | `RedisKeyUtil.rate("direct-session-client-ip", clientId + ":" + ip),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AuthServiceImpl.java:517](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:517) | `if (!RedisRateLimiter.tryAcquire(stringRedisTemplate, RedisKeyUtil.rate("direct-login-ip", ip),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AuthServiceImpl.java:539](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:539) | `RedisKeyUtil.rate("login-ip", ip), userAccentProperties.getLoginIpLimit(),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AuthServiceImpl.java:558](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:558) | `if (!RedisRateLimiter.tryAcquire(stringRedisTemplate, RedisKeyUtil.rate(biz + "-ip", ip),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AuthServiceImpl.java:564](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:564) | `RedisKeyUtil.rate(biz + "-email", normalizedEmail),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [AuthServiceImpl.java:586](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:586) | `writeJson(RedisKeyUtil.directTicket(ticket), record, oauthDirectProperties.getTicketTtlSeconds());` | 调用语句保持；只使用新直连票据，旧票据需重新获取。 |
| [AuthServiceImpl.java:618](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:618) | `RedisKeyUtil.directUsed(ticket), "1", oauthDirectProperties.getTicketTtlSeconds(), TimeUnit.SECONDS);` | 调用语句保持；只写新直连票据的已用标记。 |
| [AuthServiceImpl.java:623](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:623) | `Map<String, Object> record = readJsonMap(RedisKeyUtil.directTicket(ticket));` | 调用语句保持；只使用新直连票据，旧票据需重新获取。 |
| [AuthServiceImpl.java:627](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:627) | `stringRedisTemplate.delete(RedisKeyUtil.directTicket(ticket));` | 调用语句保持；只使用新直连票据，旧票据需重新获取。 |
| [AuthServiceImpl.java:757](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:757) | `if (!account.isEmpty() && Boolean.TRUE.equals(stringRedisTemplate.hasKey(RedisKeyUtil.loginLock(account)))) {` | 调用语句保持；只检查/写入/清理新锁，旧锁不再生效。 |
| [AuthServiceImpl.java:833](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:833) | `if (applyLoginFailureLock(RedisKeyUtil.loginFail(account), RedisKeyUtil.loginLock(account))) {` | 调用语句保持；新前缀从零累计失败次数，不复制旧计数。 |
| [AuthServiceImpl.java:859](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:859) | `stringRedisTemplate.delete(RedisKeyUtil.loginFail(account));` | 调用语句保持；新前缀从零累计失败次数，不复制旧计数。 |
| [AuthServiceImpl.java:860](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:860) | `stringRedisTemplate.delete(RedisKeyUtil.loginLock(account));` | 调用语句保持；只检查/写入/清理新锁，旧锁不再生效。 |
| [EfPlayerDataServiceImpl.java:272](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EfPlayerDataServiceImpl.java:272) | `String key = RedisKeyUtil.rate(RATE_LIMIT_BIZ, SignUtil.sha256(roleId + ":" + serverId));` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [EmailCodeServiceImpl.java:88](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:88) | `RedisKeyUtil.rate("send-code", ip), userAccentProperties.getCodeSendIpLimit(),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [EmailCodeServiceImpl.java:94](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:94) | `RedisKeyUtil.rate("send-code-hour", ip), userAccentProperties.getCodeSendIpHourlyLimit(),` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [EmailCodeServiceImpl.java:105](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:105) | `RedisKeyUtil.rate("send-code-email", email), 1, emailIntervalSeconds)) {` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [EmailCodeServiceImpl.java:112](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:112) | `stringRedisTemplate.opsForValue().set(RedisKeyUtil.code(email), code, codeTtlSeconds, TimeUnit.SECONDS);` | 调用语句保持；仅使用 tcl:code:，旧验证码不再读取，失败后重新发码。 |
| [EmailCodeServiceImpl.java:135](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:135) | `String key = RedisKeyUtil.code(email);` | 调用语句保持；仅使用 tcl:code:，旧验证码不再读取，失败后重新发码。 |
| [OAuthMigrateServiceImpl.java:149](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:149) | `RedisKeyUtil.oauthMigrateNonce(request.nonce()), "1", clockSkewSeconds * 2, TimeUnit.SECONDS);` | 调用语句保持；只检查/写入新 nonce，不继承旧已用记录，接受旧合法签名在允许时窗内可能再次被接受。 |
| [OAuthMigrateServiceImpl.java:284](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:284) | `RedisKeyUtil.rate("oauth-migrate-ip", rateIp), perIpPerMinute, RATE_WINDOW_SECONDS)` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [OAuthMigrateServiceImpl.java:286](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:286) | `RedisKeyUtil.rate("oauth-migrate-uid", String.valueOf(uid)), perUidPerMinute, RATE_WINDOW_SECONDS)` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [OAuthMigrateServiceImpl.java:288](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:288) | `RedisKeyUtil.rate("oauth-migrate-client", clientId), perClientPerMinute, RATE_WINDOW_SECONDS);` | 调用语句保持；新前缀重新计算额度或获取短锁，业务名、target、TTL 和阈值不变。 |
| [OAuthTokenServiceImpl.java:189](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:189) | `RedisKeyUtil.oauthTicketUsed(ticket), "1", oauthProperties.getLoginTicketTtlSeconds(), TimeUnit.SECONDS);` | 调用语句保持；只写新票据已用标记，不继承旧标记。 |
| [OAuthTokenServiceImpl.java:193](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:193) | `Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthTicket(ticket));` | 调用语句保持；只读写/删除新票据，旧票据失效后重新获取。 |
| [OAuthTokenServiceImpl.java:198](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:198) | `stringRedisTemplate.delete(RedisKeyUtil.oauthTicket(ticket));` | 调用语句保持；只读写/删除新票据，旧票据失效后重新获取。 |
| [OAuthTokenServiceImpl.java:218](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:218) | `writeJson(RedisKeyUtil.oauthTicket(ticket), record, oauthProperties.getLoginTicketTtlSeconds());` | 调用语句保持；只读写/删除新票据，旧票据失效后重新获取。 |
| [OAuthTokenServiceImpl.java:339](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:339) | `writeJson(RedisKeyUtil.oauthCode(code), record, oauthProperties.getAuthorizationCodeTtlSeconds());` | 调用语句保持；读写新授权码，并由现有 Lua 消费；旧码无法兑换。 |
| [OAuthTokenServiceImpl.java:385](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:385) | `writeJson(RedisKeyUtil.oauthConsent(pendingId), record, oauthProperties.getConsentTtlSeconds());` | 调用语句保持；只读写/删除新确认单，旧确认页需要重新发起授权。 |
| [OAuthTokenServiceImpl.java:404](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:404) | `Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthConsent(pendingId));` | 调用语句保持；只读写/删除新确认单，旧确认页需要重新发起授权。 |
| [OAuthTokenServiceImpl.java:451](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:451) | `RedisKeyUtil.oauthConsentUsed(pendingId), "1", oauthProperties.getConsentTtlSeconds(), TimeUnit.SECONDS);` | 调用语句保持；只写新确认单的已用标记。 |
| [OAuthTokenServiceImpl.java:455](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:455) | `Map<String, Object> record = readJsonMap(RedisKeyUtil.oauthConsent(pendingId));` | 调用语句保持；只读写/删除新确认单，旧确认页需要重新发起授权。 |
| [OAuthTokenServiceImpl.java:465](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:465) | `stringRedisTemplate.delete(RedisKeyUtil.oauthConsent(pendingId));` | 调用语句保持；只读写/删除新确认单，旧确认页需要重新发起授权。 |
| [RedisOAuthTokenStore.java:46](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java:46) | `return redisTemplate.opsForValue().get(RedisKeyUtil.oauthCode(code));` | 调用语句保持；读写新授权码，并由现有 Lua 消费；旧码无法兑换。 |
| [RedisOAuthTokenStore.java:51](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java:51) | `return Boolean.TRUE.equals(redisTemplate.hasKey(RedisKeyUtil.oauthCodeUsed(code)));` | 调用语句保持；只读写新 used 标记，不继承旧标记。 |
| [RedisOAuthTokenStore.java:57](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java:57) | `RedisKeyUtil.oauthCode(code),` | 调用语句保持；读写新授权码，并由现有 Lua 消费；旧码无法兑换。 |
| [RedisOAuthTokenStore.java:58](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java:58) | `RedisKeyUtil.oauthCodeUsed(code));` | 调用语句保持；只读写新 used 标记，不继承旧标记。 |

### 4.1 间接操作：保留现有方法体，核对新键生效

下列语句不需要增加 Redis 兼容逻辑；核对 key 由第 3 节改名后的构建方法传入，TTL、业务判断和调用签名保持原样。

| 文件 / 当前代码位置 | 当前操作 | 本轮处理 |
| --- | --- | --- |
| [EmailCodeServiceImpl.java:136](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EmailCodeServiceImpl.java:136)、143 行 | get(key)、delete(key) | 保持方法体；只校验/删除新前缀下的验证码，不回退旧码 |
| [AuthServiceImpl.java:840](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:840)、842、845、847 行 | increment、expire、set、delete | 保持方法体；失败计数和锁在新前缀下重新开始 |
| [RedisRateLimiter.java:28](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisRateLimiter.java:28) | increment 后首次 expire | 保持方法体；新桶使用原额度和窗口，不继承旧计数 |
| [AkAccountServiceImpl.java:479](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AkAccountServiceImpl.java:479) | setIfAbsent，2 秒锁 | 保持语句，新前缀短锁重新开始 |
| [EfPlayerDataServiceImpl.java:276](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/EfPlayerDataServiceImpl.java:276) | setIfAbsent，2 秒锁 | 同上 |
| [AuthServiceImpl.java:665](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:665)、[OAuthTokenServiceImpl.java:2174](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:2174) | writeJson(key, value, ttl) | 保持辅助方法；不能把全部 uc: 入参无差别改成 tcl:，因为长期键保留 |
| [RedisOAuthTokenStore.java:55](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/RedisOAuthTokenStore.java:55) | 组织授权码消费 Lua 的 KEYS | 保持方法体；原构建方法会将新授权码键和新 used 键传给脚本 |

### 4.2 oauth-code-consume.lua：保留原脚本，不做双命名空间改造

当前脚本完整代码保持原样：

```lua
-- KEYS[1]: authorization code record
-- KEYS[2]: short-lived used marker
-- ARGV[1]: exact JSON value already validated by Java
-- ARGV[2]: used marker TTL in seconds
--
-- The comparison is essential: validation happens before this script runs. If another request
-- consumes or replaces the record in between, this request must not delete that newer state.
local current = redis.call('GET', KEYS[1])
if not current or current ~= ARGV[1] then
    return 0
end

redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
return 1
```

RedisOAuthTokenStore 的现有构建方法将把新前缀的授权码键、used 键传入 KEYS[1] / KEYS[2]。脚本不硬编码 uc:，因此本轮不需要修改 Lua 正文、KEYS/ARGV 数量或 compare-and-delete 行为，也不新增迁移脚本。

旧授权码因不再读取旧前缀而无法兑换；新授权码继续按原脚本消费。客户端需要重新走授权流程，这是已接受的直接切换影响。

以下三个长期 OAuth Lua 同样保持原样：[oauth-refresh-issue.lua:7](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-refresh-issue.lua:7)、[oauth-refresh-rotate.lua:10](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-refresh-rotate.lua:10)、[oauth-migrate-issue.lua:11](/C:/dev_projects/UserCenterV1/src/main/resources/scripts/oauth-migrate-issue.lua:11)。它们操作的是本轮保留的 access、refresh、uid 索引、family 和 migrate-current；nonce 改名不代表 migrate-current 改名。

## 5. 各类短期键的有效期与半夜直接部署约定

| 前缀组 | 新键继续采用的有效期 | 直接切换时接受的影响 |
| --- | --- | --- |
| code（邮箱验证码） | 5 分钟 | 旧验证码查不到，重新获取 |
| login:fail / lock | 15 分钟 / 3 分钟 | 旧失败次数不继承，旧锁不再生效 |
| rate | 2 秒～1 小时 | 限流额度、计数和短锁在新前缀下重新开始 |
| oauth:code / code:used | 记录 5 分钟；used 自写入起 5 分钟 | 旧授权码不可兑换，旧标记不继承，重新授权 |
| oauth:ticket / ticket:used | 记录 5 分钟；used 自写入起 5 分钟 | 旧跨站票据失效，重新获取 |
| oauth:consent / consent:used | 记录 5 分钟；used 自写入起 5 分钟 | 旧确认单无效，重新打开授权流程 |
| oauth:direct:channel | 5 分钟 | 旧直连前置凭证无效，重新发起登录/注册 |
| oauth:direct:ticket / direct:used | 记录 1 分钟；used 自写入起 1 分钟 | 旧票据无法兑换，重新登录获取票据 |
| oauth:migrate:nonce | 2 × 时间偏移配置，测试配置为 4 分钟 | 不继承旧 nonce；旧合法签名在原允许时窗内可能再次被接受 |

用户计划半夜部署，并已接受上述短期流程失败、状态重置及可能重新登录的影响。部署按“停止全部旧实例，再启动新版本”统一切换，不让新旧版本并行提供服务。

不等待旧 TTL，不新增旧键兼容或复制迁移，也不执行 Redis 清理；旧短期键自然过期。九类长期/关联前缀及会话扫描保留，因此不主动批量吊销现有长期会话或令牌。若回滚旧版本，接受旧短期命名空间再次被读取、在途流程再次中断的影响。

本次确认不取消 HTTP 请求头和票据参数的旧名称兼容；Redis 键与对外参数名是两个独立变更点。

## 6. 业务简称注释的逐文件 diff

下列 diff 来自当前源码中实际命中的业务注释。仅替换业务简称和已列入计划的头/参数说明；注释里的长期 Redis key 保持原样，@author UserCenter 等完整名称不连带修改。

### RequestUtil.java

位置：[RequestUtil.java:40](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RequestUtil.java:40)。

```diff
-     * 从请求解析用户 token：优先 Authorization: Bearer xxx，其次 UC-Token
+     * 从请求解析用户 token：优先 Authorization: Bearer xxx，其次 TCL-Token（兼容旧请求头）
```

### OAuthController.java

位置：[OAuthController.java:40](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:40)、[OAuthController.java:90](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:90)、[OAuthController.java:112](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:112)、[OAuthController.java:127](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:127)、[OAuthController.java:150](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:150)、[OAuthController.java:179](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:179)、[OAuthController.java:250](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthController.java:250)。

```diff
- * （Authorization: Bearer {token} 或 UC-Token），由网站后端在跳转前引导用户完成登录。</p>
+ * （Authorization: Bearer {token} 或 TCL-Token（兼容旧请求头）），由网站后端在跳转前引导用户完成登录。</p>
-     * OAuth 授权码签发（POST 变体）：跨站登录页可用表单 POST 携带 uc_ticket 于请求体，
+     * OAuth 授权码签发（POST 变体）：跨站登录页可用表单 POST 携带 tcl_ticket（兼容旧参数） 于请求体，
-     * @param request HTTP 请求（Authorization: Bearer {token} 或 UC-Token）
+     * @param request HTTP 请求（Authorization: Bearer {token} 或 TCL-Token（兼容旧请求头））
-     * @param request   HTTP 请求（Authorization: Bearer {token} 或 UC-Token）
+     * @param request   HTTP 请求（Authorization: Bearer {token} 或 TCL-Token（兼容旧请求头））
-     * @param request HTTP 请求（解析登录会话，Authorization: Bearer {token} 或 UC-Token）
+     * @param request HTTP 请求（解析登录会话，Authorization: Bearer {token} 或 TCL-Token（兼容旧请求头））
-     * @param request             HTTP 请求（解析登录会话/uc_ticket）
+     * @param request             HTTP 请求（解析登录会话/tcl_ticket（兼容旧参数））
-     * 查库与按 scope 组装由 Service 完成（便于无自有账户体系的接入方直接以 UC 用户作为登录账号）
+     * 查库与按 scope 组装由 Service 完成（便于无自有账户体系的接入方直接以 TCL 用户作为登录账号）
```

### OAuthInternalController.java

位置：[OAuthInternalController.java:41](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthInternalController.java:41)、[OAuthInternalController.java:47](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthInternalController.java:47)。

```diff
-     * 迁移兑换：凭旧自签 token 对应的 uid 换取一对全新的 UC 令牌
+     * 迁移兑换：凭旧自签 token 对应的 uid 换取一对全新的 TCL 令牌
-     * @param kid             公钥标识（对应 UC 侧 kid → 公钥映射表）
+     * @param kid             公钥标识（对应 TCL 侧 kid → 公钥映射表）
```

### OAuthLegacyLoginController.java

位置：[OAuthLegacyLoginController.java:24](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:24)、[OAuthLegacyLoginController.java:67](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:67)、[OAuthLegacyLoginController.java:93](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/oauth/OAuthLegacyLoginController.java:93)。

```diff
- *       登录/注册凭证由浏览器直接提交 UC，不经过旧系统后端</li>
+ *       登录/注册凭证由浏览器直接提交 TCL，不经过旧系统后端</li>
-     * 由浏览器直接提交到 UC，校验通过后返回一次性登录票据（凭证不经过旧系统后端），
+     * 由浏览器直接提交到 TCL，校验通过后返回一次性登录票据（凭证不经过旧系统后端），
-     * 直接提交到 UC，创建用户后返回一次性登录票据（注册信息不经过旧系统后端），
+     * 直接提交到 TCL，创建用户后返回一次性登录票据（注册信息不经过旧系统后端），
```

### AuthController.java

位置：[AuthController.java:78](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:78)、[AuthController.java:80](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/AuthController.java:80)。

```diff
-     * 登录（密码 / 邮箱验证码），成功后返回会话 token（由前端自行携带：Authorization / UC-Token）
+     * 登录（密码 / 邮箱验证码），成功后返回会话 token（由前端自行携带：Authorization / TCL-Token（兼容旧请求头））
-     * <p>必须携带 {@code UC-Client-Id} 请求头：会话按来源客户端隔离用户配置，登录时缺失
+     * <p>必须携带 {@code TCL-Client-Id（兼容旧请求头）} 请求头：会话按来源客户端隔离用户配置，登录时缺失
```

### OAuthClientAdminController.java

位置：[OAuthClientAdminController.java:26](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/controller/user/OAuthClientAdminController.java:26)。

```diff
- * OAuth 客户端自助管理接口（需 UC 会话 token，/user/** 由 UserAuthInterceptor 统一校验）
+ * OAuth 客户端自助管理接口（需 TCL 会话 token，/user/** 由 UserAuthInterceptor 统一校验）
```

### MigrateTokenRequest.java

位置：[MigrateTokenRequest.java:4](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/dto/oauth/MigrateTokenRequest.java:4)、[MigrateTokenRequest.java:11](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/dto/oauth/MigrateTokenRequest.java:11)。

```diff
- * 迁移兑换请求参数（BackEndV3 → UC 的服务端间调用）
+ * 迁移兑换请求参数（BackEndV3 → TCL 的服务端间调用）
- * @param kid             公钥标识（对应 UC 侧 kid → 公钥映射表，用于轮换）
+ * @param kid             公钥标识（对应 TCL 侧 kid → 公钥映射表，用于轮换）
```

### ServerLoginVO.java

位置：[ServerLoginVO.java:14](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/auth/ServerLoginVO.java:14)。

```diff
- * 不是 <b>UC 会话 token</b>：会话 token 仅由 /auth/login 签发且只作用于 /user/**。</p>
+ * 不是 <b>TCL 会话 token</b>：会话 token 仅由 /auth/login 签发且只作用于 /user/**。</p>
```

### DirectLoginTicketVO.java

位置：[DirectLoginTicketVO.java:8](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/oauth/DirectLoginTicketVO.java:8)。

```diff
- * <p>前端直接向 UC 提交账号密码，校验通过后返回一次性登录票据（ticket），
+ * <p>前端直接向 TCL 提交账号密码，校验通过后返回一次性登录票据（ticket），
```

### MigrateTokenVO.java

位置：[MigrateTokenVO.java:7](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/oauth/MigrateTokenVO.java:7)。

```diff
- * 迁移兑换端点响应视图对象（UC → BackEndV3 的服务端间响应）
+ * 迁移兑换端点响应视图对象（TCL → BackEndV3 的服务端间响应）
```

### UserInfoVO.java

位置：[UserInfoVO.java:9](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/vo/oauth/UserInfoVO.java:9)。

```diff
- * <p>接入方多为无自有账户体系的网站，直接以 UC 用户作为登录账号，因此返回用户基础资料
+ * <p>接入方多为无自有账户体系的网站，直接以 TCL 用户作为登录账号，因此返回用户基础资料
```

### UserAuthInterceptor.java

位置：[UserAuthInterceptor.java:21](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/interceptor/UserAuthInterceptor.java:21)、[UserAuthInterceptor.java:99](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/interceptor/UserAuthInterceptor.java:99)。

```diff
- * <p>校验请求携带的用户会话凭证（Authorization: Bearer {token} 或 UC-Token），
+ * <p>校验请求携带的用户会话凭证（Authorization: Bearer {token} 或 TCL-Token（兼容旧请求头）），
-     * 从请求头解析 token：优先 Authorization: Bearer xxx，其次 UC-Token
+     * 从请求头解析 token：优先 Authorization: Bearer xxx，其次 TCL-Token（兼容旧请求头）
```

### AuthServiceImpl.java

位置：[AuthServiceImpl.java:692](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/AuthServiceImpl.java:692)。

```diff
-     * 校验并规范化来源客户端标识（登录 / 注册必填请求头 {@code UC-Client-Id}）
+     * 校验并规范化来源客户端标识（登录 / 注册必填请求头 {@code TCL-Client-Id（兼容旧请求头）}）
```

### OAuthMigrateServiceImpl.java

位置：[OAuthMigrateServiceImpl.java:32](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthMigrateServiceImpl.java:32)。

```diff
- *   <li>认证层是唯一准入屏障：Ed25519 非对称验签，UC 侧只存公钥，配置泄漏不足以伪造</li>
+ *   <li>认证层是唯一准入屏障：Ed25519 非对称验签，TCL 侧只存公钥，配置泄漏不足以伪造</li>
```

### OAuthTokenServiceImpl.java

位置：[OAuthTokenServiceImpl.java:141](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:141)、[OAuthTokenServiceImpl.java:154](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:154)、[OAuthTokenServiceImpl.java:206](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:206)、[OAuthTokenServiceImpl.java:242](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:242)、[OAuthTokenServiceImpl.java:264](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:264)。

```diff
-     * 解析当前登录用户：优先跨站登录票据（uc_ticket），其次请求头携带的本系统会话 token
+     * 解析当前登录用户：优先跨站登录票据（tcl_ticket（兼容旧参数）），其次请求头携带的本系统会话 token
-        // 2. 其次解析请求头携带的本系统会话 token（Authorization: Bearer / UC-Token）
+        // 2. 其次解析请求头携带的本系统会话 token（Authorization: Bearer / TCL-Token（兼容旧请求头））
-     * @param request HTTP 请求（Authorization / UC-Token 携带会话 token）
+     * @param request HTTP 请求（Authorization / TCL-Token（兼容旧请求头） 携带会话 token）
-     * @return 需要 302 跳转的地址：UC 登录页 / 授权确认页 / 客户端回调地址（携带 code）
+     * @return 需要 302 跳转的地址：TCL 登录页 / 授权确认页 / 客户端回调地址（携带 code）
-            // 未登录：302 到 UC 登录页，登录成功后携带 uc_ticket 回跳当前 authorize 地址（走跨站登录票据）
+            // 未登录：302 到 TCL 登录页，登录成功后携带 tcl_ticket（兼容旧参数） 回跳当前 authorize 地址（走跨站登录票据）
```

### OAuthMigrateService.java

位置：[OAuthMigrateService.java:10](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/OAuthMigrateService.java:10)、[OAuthMigrateService.java:19](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/OAuthMigrateService.java:19)。

```diff
- * UC 令牌。该端点跨公网可达，认证层（Ed25519 验签 + 时间窗 + nonce 防重放）是唯一
+ * TCL 令牌。该端点跨公网可达，认证层（Ed25519 验签 + 时间窗 + nonce 防重放）是唯一
-     * 校验，通过后为一个 uid 现场签发一对全新 UC 令牌。
+     * 校验，通过后为一个 uid 现场签发一对全新 TCL 令牌。
```

### OAuthTokenService.java

位置：[OAuthTokenService.java:41](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/OAuthTokenService.java:41)、[OAuthTokenService.java:185](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/OAuthTokenService.java:185)。

```diff
-     * @param request HTTP 请求（须携带本系统会话 token：Authorization / UC-Token）
+     * @param request HTTP 请求（须携带本系统会话 token：Authorization / TCL-Token（兼容旧请求头））
-     * 迁移兑换签发：为一个 uid 现场签发一对全新的 UC 令牌，并替换上一轮迁移凭证。
+     * 迁移兑换签发：为一个 uid 现场签发一对全新的 TCL 令牌，并替换上一轮迁移凭证。
```

### application-test.yml

位置：[application-test.yml:92](/C:/dev_projects/UserCenterV1/src/main/resources/application-test.yml:92)。

```diff
-  # UC 侧只存公钥，从环境变量注入，绝不写进仓库；轮换期可同时配置新旧多把公钥
+  # TCL 侧只存公钥，从环境变量注入，绝不写进仓库；轮换期可同时配置新旧多把公钥
```

## 7. 明确保留、无需执行替换的代码

| 代码 / 位置 | 保留原因 |
| --- | --- |
| [UserServiceImpl.java:42](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/UserServiceImpl.java:42)：`TOKEN_PATTERN = "uc:token:*"` | 会话前缀保留，扫描表达式必须匹配原会话 |
| [SessionInfo.java:6](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/dto/SessionInfo.java:6) 中的 `uc:token:` 注释 | 与真实保留的存储一致 |
| [PatServiceImpl.java:36](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/PatServiceImpl.java:36)、[TlcPat.java:15](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/po/TlcPat.java:15) 中的 `uc:oauth:*` 注释 | 长期 OAuth 键保留，不改成不存在的名称 |
| [OAuthTokenServiceImpl.java:1546](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/service/impl/OAuthTokenServiceImpl.java:1546) 中的 `uc:uid:oauth:` | 索引保留 |
| [PatMigrationServiceImplTest.java:84](/C:/dev_projects/UserCenterV1/src/test/java/com/orange/service/impl/PatMigrationServiceImplTest.java:84) 中的 `uc:uid:oauth:100` 断言 | 索引命名不变 |
| [PatUtil.java:21](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/PatUtil.java:21) 的 `tlc_pat_`，[TlcPat.java:26](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/entity/po/TlcPat.java:26) 的 `tlc_pat`，现有 TlcPat/TlcPatMapper | 现有 tlc 拼写、表和已签发 PAT 不变 |
| [RedisKeyUtil.java:82](/C:/dev_projects/UserCenterV1/src/main/java/com/orange/common/util/RedisKeyUtil.java:82)、85、88、91 行的四类 `tlc:` 前缀 | 不属于本轮 uc 缩写替换 |
| [schema-user.sql:261](/C:/dev_projects/UserCenterV1/src/main/resources/db/schema-user.sql:261) 中的 Redis 注释、全部业务表、列、索引和存量数据 | 不做数据库迁移，不生成 SQL |
| 浏览器键 `UC_ACCESS_TOKEN`、`uc.oauth.pkce` | 前端实现未核实，本轮保留旧登录/PKCE 状态 |
| `SUCCESS`、`success`、`bucket`、`NoSuchAlgorithmException`、`getClientId` | 普通单词或第三方标识符，不是要替换的缩写 |
| 旧请求头/参数字面量 UC-Token、UC-Client-Id、uc_ticket | 仍用于 HTTP 接口旧名称兼容；Redis 短期键不新增 legacy 构建入口 |

## 8. 实施验证与部署边界

新增 RequestUtilTest、UserAuthInterceptorTest、AuthControllerTest，扩展 OAuthTokenServiceImplTest，覆盖新旧 HTTP 名称、空白处理、冲突拒绝、Bearer 优先、旧会话存储继续读取，以及短期票据只使用新 Redis 前缀且只消费一次。现有 OAuth、认证和 PAT 测试一并回归。真实 Redis 集成测试仍需显式提供独立测试环境。

- HTTP 名称：Authorization 优先；新旧令牌头、来源头、票据参数分别单独提供、同时同值、同时冲突及空白输入的行为与第 2 节一致。
- Redis 直接切换：14 个短期常量全部使用 tcl:，读写调用不回退旧短期键；旧验证码、授权码、票据和确认单失败，新流程正常按原 TTL 使用。
- 新命名空间：失败计数从零累计，旧锁和旧限流不继承；新锁、新限流与新 nonce 标记仍按原代码工作；不要求历史 nonce 在切换后继续被拒绝。
- 保留项：九类关联前缀、会话扫描、长期令牌索引、现有 PAT 格式及相关断言不变；新旧 HTTP 头仍能传递同一枚保留的长期凭证。
- 脚本：授权码消费 Lua 正文和 KEYS/ARGV 结构不改，新授权码沿用原消费逻辑；其余三个长期 OAuth Lua 不改。
- 部署：半夜停旧后启动新版本，不进行新旧版本并行处理，不新增 legacy/双读双写/迁移/清理；回滚接受短期状态再次切换。

请求头/参数目标片段、14 个 Redis 前缀及业务注释已应用；接口文档及 AGENTS.md 已同步。application-test.yml 仅更新业务简称注释；Lua 正文、数据库结构及 Redis 前缀迁移/清理逻辑未改动，尚未部署。完整测试触发了真实数据库统计写入，详见文末。

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
