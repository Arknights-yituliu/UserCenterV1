-- =============================================================
-- 多站点统一用户服务（User Center）建表脚本
-- 数据库：MySQL 8.x，字符集 utf8mb4
-- =============================================================

CREATE DATABASE IF NOT EXISTS `user_center`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `user_center`;

-- -------------------------------------------------------------
-- 1. 全局用户主表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user_info`;
CREATE TABLE `user_info` (
    `uid`             BIGINT       NOT NULL COMMENT '对外业务编号（雪花ID，全局唯一，主键）',
    `email`           VARCHAR(128) DEFAULT NULL COMMENT '邮箱（登录账号）',
    `user_name`       VARCHAR(64)  DEFAULT NULL COMMENT '用户名（登录账号，兼容旧系统迁移用户）',
    `password`        VARCHAR(128) DEFAULT NULL COMMENT '密码（BCrypt 哈希）',
    `avatar`          VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    `nickname`        VARCHAR(64)  DEFAULT NULL COMMENT '全局默认昵称',
    `status`          INT          NOT NULL DEFAULT 1 COMMENT '状态：1=正常 <0=封禁',
    `ip`              VARCHAR(64)  DEFAULT NULL COMMENT '注册 IP',
    `register_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `last_login_time` DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    PRIMARY KEY (`uid`),
    UNIQUE KEY `uk_email` (`email`),
    UNIQUE KEY `uk_user_name` (`user_name`)
) ENGINE = InnoDB COMMENT = '全局用户主表';

-- -------------------------------------------------------------
-- 2. 登录日志表
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
-- 3. 操作审计日志表
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
-- 4. OAuth2 客户端注册表（第三方 Web 网站接入登记）
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
-- 5. SMTP 邮件渠道配置表（多渠道降级发送，配置存数据库可动态调整）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `smtp_config`;
CREATE TABLE `smtp_config` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `account_key`      VARCHAR(64)  NOT NULL COMMENT '渠道标识，如 mail-163-1 / mail-163-2',
    `host`             VARCHAR(128) NOT NULL COMMENT 'SMTP 服务器地址',
    `port`             INT          NOT NULL COMMENT 'SMTP 端口',
    `username`         VARCHAR(128) NOT NULL COMMENT '登录账号（发件人邮箱）',
    `password`         VARCHAR(256) NOT NULL COMMENT 'SMTP 授权码',
    `protocol`         VARCHAR(16)  NOT NULL DEFAULT 'smtp' COMMENT '协议，默认 smtp',
    `default_encoding` VARCHAR(16)  NOT NULL DEFAULT 'UTF-8' COMMENT '默认编码',
    `ssl_enable`       TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 SSL：1=启用 0=关闭',
    `enabled`          TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用该渠道：1=启用 0=停用',
    `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_key` (`account_key`)
) ENGINE = InnoDB COMMENT = 'SMTP 邮件渠道配置表';

