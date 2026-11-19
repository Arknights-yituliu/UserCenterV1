# Orange Auth

> 名称兼容：示例统一使用 `TCL-Token`、`TCL-Client-Id`、`tcl_ticket`；对应入口仍接受旧名称 `UC-Token`、`UC-Client-Id`、`uc_ticket`。同一项新旧名称同时提供且非空值不一致时返回 `10001`；令牌解析仍优先使用 `Authorization: Bearer`。此说明不新增端点的认证要求，也不改变归档接口的历史状态。

Orange Auth 是一个面向多站点的统一用户认证与授权服务。它集中管理用户账号、会话和安全配置，并向接入系统提供 OAuth2 授权能力，使多个站点能够复用同一套身份体系。

## 主要能力

- 用户注册、密码登录、邮箱验证码登录和密码重置。
- 用户资料、邮箱和登录会话管理，支持主动下线指定会话。
- OAuth2 授权码流程、访问令牌刷新、令牌撤销和用户信息查询。
- OAuth 客户端自助注册及客户端配置管理。
- 旧系统直连认证：由前端向 Orange Auth 提交登录或注册凭证，再通过一次性票据供旧系统后端兑换用户信息；仅限管理员显式开通的加密客户端使用。
- Redis 会话、验证码、限流和 OAuth 临时凭证管理。

## 接口范围

| 路径前缀 | 用途 |
| --- | --- |
| `/auth/**` | 注册、登录、验证码、登出及密码重置 |
| `/user/**` | 用户资料、邮箱和会话自助管理 |
| `/oauth2/**` | OAuth2 授权、令牌、用户信息与直连登录 |
| `/oauth/config/**` | 通过 OAuth 令牌管理用户配置 |

用户会话支持标准 `Authorization: Bearer <token>` 请求头，也兼容 `TCL-Token` 请求头。Redis 的 14 类短期键使用 `tcl:` 前缀；会话、OAuth 令牌及关联索引继续使用历史 `uc:` 前缀，现有 PAT 的 `tlc:` 命名保留。

## 技术栈

- Java 17 与 Spring Boot 3
- MyBatis-Plus 与 MySQL
- Redis
- Springdoc OpenAPI
- Spring Mail 与腾讯云 SES

## 构建与运行

运行前请准备 MySQL、Redis 和邮件服务，并按目标环境补齐应用配置。

```bash
mvn clean package
java -jar target/user-center-backend.jar
```

构建完成后的后端产物名称为 `user-center-backend.jar`。
