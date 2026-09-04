-- OAuth 客户端状态字段正向语义迁移（一次性人工执行）
--
-- 执行要求：
-- 1. 先备份 oauth_client 表；
-- 2. 在应用停机或维护窗口执行；
-- 3. 旧版应用使用 status/admin_banned，新版应用使用 owner_enabled/admin_approved，
--    因此不支持新旧版本同时运行。

ALTER TABLE `oauth_client`
    ADD COLUMN `owner_enabled` TINYINT NULL COMMENT '所有者是否启用：1=启用 0=停用' AFTER `refresh_token_ttl`,
    ADD COLUMN `admin_approved` TINYINT NULL COMMENT '管理员是否审批通过：1=通过 0=待审批或封禁' AFTER `owner_enabled`;

-- owner_enabled 直接继承旧 status；admin_approved 需要反转旧 admin_banned。
UPDATE `oauth_client`
SET `owner_enabled` = CASE WHEN `status` = 1 THEN 1 ELSE 0 END,
    `admin_approved` = CASE WHEN `admin_banned` = 0 THEN 1 ELSE 0 END;

ALTER TABLE `oauth_client`
    MODIFY COLUMN `owner_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '所有者是否启用：1=启用 0=停用',
    MODIFY COLUMN `admin_approved` TINYINT NOT NULL DEFAULT 0 COMMENT '管理员是否审批通过：1=通过 0=待审批或封禁',
    DROP INDEX `idx_status`,
    ADD INDEX `idx_owner_enabled` (`owner_enabled`),
    DROP COLUMN `status`,
    DROP COLUMN `admin_banned`;
