-- =============================================================
-- 多站点统一用户服务（User Center）建表入口
-- 数据库：MySQL 8.x，字符集 utf8mb4
--
-- 建表语句已按业务模块拆分，本文件只负责建库与排序规则对齐：
--   1. schema-user.sql  用户服务：账号、登录/审计日志、邮件、配置、排班、OAuth
--   2. schema-ak.sql    明日方舟：账号绑定、干员、物品、皮肤、player/info 镜像、统计结果
--   3. schema-ef.sql    终末地：玩家数据、角色绑定
--
-- 全新环境按上述顺序执行；三个模块文件各自带 USE `user_center`，
-- 需要重建某一个模块时单独重跑对应文件即可，不影响其它模块。
-- =============================================================

CREATE DATABASE IF NOT EXISTS `user_center`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

-- 已有库可能沿用了历史默认排序规则（如 utf8mb4_german2_ci）。若不显式对齐，
-- 后续新建表或新增列会继承错误的排序规则，与既有列字符串比较时报 1267 非法混用。
-- CREATE DATABASE IF NOT EXISTS 对已存在的库不生效，故此处再强制对齐一次。
ALTER DATABASE `user_center`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;

USE `user_center`;
