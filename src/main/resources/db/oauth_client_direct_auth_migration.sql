-- OAuth 客户端直连认证能力字段迁移（一次性人工执行）
--
-- 执行要求：
-- 1. 先备份 oauth_client 表；
-- 2. 在发布包含直连权限校验的新版应用前执行；
-- 3. 新字段默认关闭。上线前必须确认现有直连接入方，并仅为可信客户端显式开启。

ALTER TABLE `oauth_client`
    ADD COLUMN `direct_auth_enabled` TINYINT NOT NULL DEFAULT 0
        COMMENT '是否允许直连认证（登录和注册）：1=允许 0=禁止'
        AFTER `admin_approved`;

-- 按审核后的白名单逐个开启，不要对全部客户端批量开放。
-- UPDATE `oauth_client`
-- SET `direct_auth_enabled` = 1
-- WHERE `id` IN ('<trusted_client_id>')
--   AND `admin_approved` = 1;
