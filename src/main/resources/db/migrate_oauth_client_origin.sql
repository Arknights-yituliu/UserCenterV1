-- 已有数据库的一次性迁移脚本：把 OAuth 客户端 Origin 拆到独立审核表。
-- 迁移后的 Origin 全部进入待审核状态，不继承客户端审批结果。

CREATE TABLE `oauth_client_origin` (
    `client_id`       VARCHAR(128) NOT NULL COMMENT 'OAuth客户端ID，一个客户端当前登记一个Origin',
    `client_name`     VARCHAR(128) NOT NULL COMMENT '客户端名称（冗余保存，供管理员审核展示）',
    `origin`          VARCHAR(255) NOT NULL COMMENT '规范化Origin（scheme、host和可选非默认端口）',
    `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '所有者是否启用：1=启用 0=停用',
    `admin_approved`  TINYINT      NOT NULL DEFAULT 0 COMMENT '管理员是否审批通过：1=通过 0=待审批或拒绝',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`client_id`),
    KEY `idx_cors_status` (`enabled`, `admin_approved`, `origin`)
) ENGINE = InnoDB COMMENT = 'OAuth2客户端CORS Origin白名单';

INSERT INTO `oauth_client_origin` (`client_id`, `client_name`, `origin`, `enabled`, `admin_approved`)
SELECT `id`, `client_name`, `website_origin`, `owner_enabled`, 0
FROM `oauth_client`
WHERE `website_origin` IS NOT NULL AND `website_origin` <> '';

ALTER TABLE `oauth_client` DROP COLUMN `website_origin`;
