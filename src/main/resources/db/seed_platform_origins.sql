-- 单独补录平台自有前端 Origin 时执行；可重复执行。
-- 安全提示：http://localhost:3000 仅建议保留在开发或测试数据库。

INSERT INTO `oauth_client_origin`
    (`client_id`, `client_name`, `origin`, `enabled`, `admin_approved`)
VALUES
    ('platform_ark_web', 'ARK 前端', 'https://ark.yituliu.cn', 1, 1),
    ('platform_ef_web', 'EF 前端', 'https://ef.yituliu.cn', 1, 1),
    ('platform_orange_web', 'Orange 前端', 'https://orange.yituliu.cn', 1, 1),
    ('platform_local_web', '本地开发前端', 'http://localhost:3000', 1, 1)
ON DUPLICATE KEY UPDATE
    `client_name` = VALUES(`client_name`),
    `origin` = VALUES(`origin`),
    `enabled` = 1,
    `admin_approved` = 1;
