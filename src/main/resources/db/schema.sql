-- =============================================================
-- 多站点统一用户服务（User Center）建表脚本
-- 数据库：MySQL 8.x，字符集 utf8mb4
-- =============================================================

CREATE DATABASE IF NOT EXISTS `user_center`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `user_center`;

-- -------------------------------------------------------------
-- 1. 接入方应用表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `app_info`;
CREATE TABLE `app_info` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `app_id`         VARCHAR(64)  NOT NULL COMMENT '接入方 AppId（全局唯一）',
    `app_secret`     VARCHAR(128) NOT NULL COMMENT '接入方 AppSecret（HMAC-SHA256 签名密钥）',
    `app_name`       VARCHAR(128) NOT NULL COMMENT '应用名称',
    `callback_domain` VARCHAR(255) DEFAULT NULL COMMENT '回调域名白名单，多个用逗号分隔',
    `status`         TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_app_id` (`app_id`)
) ENGINE = InnoDB COMMENT = '接入方应用表';

-- -------------------------------------------------------------
-- 2. 全局用户主表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '内部主键（不对外暴露）',
    `uid`             BIGINT       NOT NULL COMMENT '对外业务编号（雪花ID，全局唯一）',
    `email`           VARCHAR(128) DEFAULT NULL COMMENT '邮箱（登录账号）',
    `password`        VARCHAR(128) DEFAULT NULL COMMENT '密码（BCrypt 哈希）',
    `avatar`          VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    `nickname`        VARCHAR(64)  DEFAULT NULL COMMENT '全局默认昵称',
    `status`          INT          NOT NULL DEFAULT 1 COMMENT '状态：1=正常 <0=封禁',
    `ip`              VARCHAR(64)  DEFAULT NULL COMMENT '注册 IP',
    `register_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid` (`uid`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB COMMENT = '全局用户主表';

-- -------------------------------------------------------------
-- 3. 站点扩展资料表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user_profile`;
CREATE TABLE `user_profile` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`         BIGINT       NOT NULL COMMENT '用户 uid',
    `app_id`      VARCHAR(64)  NOT NULL COMMENT '接入方 AppId',
    `nickname`    VARCHAR(64)  DEFAULT NULL COMMENT '站点内昵称',
    `avatar`      VARCHAR(512) DEFAULT NULL COMMENT '站点内头像',
    `extension`   JSON         DEFAULT NULL COMMENT '扩展字段（JSON）',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid_app` (`uid`, `app_id`)
) ENGINE = InnoDB COMMENT = '站点扩展资料表';

-- -------------------------------------------------------------
-- 4. 第三方绑定表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user_external_binding`;
CREATE TABLE `user_external_binding` (
    `id`        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`       BIGINT      NOT NULL COMMENT '用户 uid',
    `provider`  VARCHAR(32) NOT NULL COMMENT '第三方类型：wechat/qq',
    `open_id`   VARCHAR(128) NOT NULL COMMENT '第三方 open_id',
    `union_id`  VARCHAR(128) DEFAULT NULL COMMENT '第三方 union_id',
    `bind_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_provider_openid` (`provider`, `open_id`),
    KEY `idx_uid` (`uid`)
) ENGINE = InnoDB COMMENT = '第三方绑定表';

-- -------------------------------------------------------------
-- 5. 登录日志表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `login_log`;
CREATE TABLE `login_log` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`        BIGINT       DEFAULT NULL COMMENT '用户 uid（失败时可能为空）',
    `app_id`     VARCHAR(64)  DEFAULT NULL COMMENT '来源应用',
    `login_type` VARCHAR(32)  NOT NULL COMMENT '登录方式：password/email_code/wechat/qq',
    `ip`         VARCHAR(64)  DEFAULT NULL COMMENT '登录 IP',
    `user_agent` VARCHAR(512) DEFAULT NULL COMMENT 'UA',
    `status`     TINYINT      NOT NULL COMMENT '结果：1=成功 0=失败',
    `login_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_uid` (`uid`),
    KEY `idx_login_time` (`login_time`)
) ENGINE = InnoDB COMMENT = '登录日志表';

-- -------------------------------------------------------------
-- 6. 操作审计日志表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `audit_log`;
CREATE TABLE `audit_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `operator_type` VARCHAR(16)  NOT NULL COMMENT '操作者类型：admin/user',
    `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作者 id',
    `action`        VARCHAR(64)  NOT NULL COMMENT '操作动作',
    `target`        VARCHAR(255) DEFAULT NULL COMMENT '操作对象描述',
    `detail`        JSON         DEFAULT NULL COMMENT '操作详情（JSON）',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_operator` (`operator_type`, `operator_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE = InnoDB COMMENT = '操作审计日志表';

-- -------------------------------------------------------------
-- 7. OAuth2 客户端注册表（第三方 Web 网站接入登记）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `oauth_client`;
CREATE TABLE `oauth_client` (
    `id`                VARCHAR(128) NOT NULL COMMENT '客户端ID（client_id）',
    `client_secret`     VARCHAR(256) DEFAULT NULL COMMENT '客户端密钥（BCrypt 哈希，公共客户端为空）',
    `client_name`       VARCHAR(128) NOT NULL COMMENT '客户端名称（第三方网站名）',
    `auth_methods`      VARCHAR(256) NOT NULL COMMENT '认证方式：client_secret_basic/client_secret_post',
    `grant_types`       VARCHAR(256) NOT NULL COMMENT '授权类型：authorization_code,refresh_token',
    `redirect_uris`     VARCHAR(2048) NOT NULL COMMENT '回调地址白名单（逗号分隔，精确匹配）',
    `scopes`            VARCHAR(256) NOT NULL COMMENT '可授权范围（逗号分隔）：user.read',
    `require_pkce`      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否强制 PKCE：1=强制 0=不强制',
    `require_auth_consent` TINYINT    NOT NULL DEFAULT 1 COMMENT '授权时是否展示确认页（自研实现暂未启用确认页）',
    `website_origin`    VARCHAR(255) DEFAULT NULL COMMENT '网站域名 origin（CORS 白名单来源）',
    `access_token_ttl`  BIGINT       DEFAULT NULL COMMENT 'access_token 有效期（秒），NULL 用全局默认',
    `refresh_token_ttl` BIGINT       DEFAULT NULL COMMENT 'refresh_token 有效期（秒）',
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`)
) ENGINE = InnoDB COMMENT = 'OAuth2 客户端注册表';

-- -------------------------------------------------------------
-- 初始数据：内置一个测试接入方应用（AppSecret 生产环境务必重置）
-- -------------------------------------------------------------
INSERT INTO `app_info` (`app_id`, `app_secret`, `app_name`, `callback_domain`, `status`)
VALUES ('100001', 'test-secret-please-reset', '测试站点', NULL, 1);
