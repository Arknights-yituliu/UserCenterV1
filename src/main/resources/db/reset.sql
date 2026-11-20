-- =============================================================
-- 清空重建脚本（危险操作，仅限显式执行）
--
-- 【用途】把 user_center 库里的业务表全部删除，用于本地/测试环境重建，
--        或需要让建表脚本的列变更真正落地时使用。
--
-- 【严禁】在已有业务数据的环境（预发、生产）执行本文件。
--        本文件是全库范围的数据销毁入口，执行后数据不可恢复。
--
-- 【执行顺序】
--   1. mysql ... < db/reset.sql          -- 删除全部业务表（不含库本身）
--   2. mysql ... < db/schema.sql         -- 建库并强制对齐排序规则
--   3. mysql ... < db/schema-user.sql    -- 模块一：用户服务
--   4. mysql ... < db/schema-ak.sql      -- 模块二：明日方舟
--   5. mysql ... < db/schema-ef.sql      -- 模块三：终末地
--
-- 【为什么不放在建表脚本里】
--   schema-*.sql 用 CREATE TABLE IF NOT EXISTS，供全新环境初始化与幂等重跑，
--   重复执行既不会删数据也不会改已有表结构，因此不会误伤存量数据。
--   结构变更要么走增量 DDL，要么显式执行本文件后重建。
--
-- 表间无物理外键约束（关联关系由业务层维护），删除顺序按模块倒序排列，
-- 仅为可读性，不依赖强制顺序。
-- =============================================================

USE `user_center`;

-- -------------------------------------------------------------
-- 模块三：终末地
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ef_weapon_pool_record`;
DROP TABLE IF EXISTS `ef_character_pool_record`;
DROP TABLE IF EXISTS `ef_player_pool_record_task`;
DROP TABLE IF EXISTS `ef_account_binding`;
DROP TABLE IF EXISTS `ef_player_data`;

-- -------------------------------------------------------------
-- 模块二：明日方舟
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_skin_statistics`;
DROP TABLE IF EXISTS `ak_operator_state_statistics`;
DROP TABLE IF EXISTS `ak_player_info_snapshot`;
DROP TABLE IF EXISTS `ak_skin_state`;
DROP TABLE IF EXISTS `ak_item_data`;
DROP TABLE IF EXISTS `ak_operator_state_manual`;
DROP TABLE IF EXISTS `ak_operator_state`;
DROP TABLE IF EXISTS `ak_default_source`;
DROP TABLE IF EXISTS `ak_player_info`;
DROP TABLE IF EXISTS `ak_account_binding`;

-- -------------------------------------------------------------
-- 模块一：用户服务
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `tlc_pat`;
DROP TABLE IF EXISTS `oauth_custom_token`;
DROP TABLE IF EXISTS `oauth_user_scope`;
DROP TABLE IF EXISTS `oauth_grant`;
DROP TABLE IF EXISTS `oauth_client_origin`;
DROP TABLE IF EXISTS `oauth_client`;
DROP TABLE IF EXISTS `user_schedule`;
DROP TABLE IF EXISTS `user_config_quota`;
DROP TABLE IF EXISTS `user_config`;
DROP TABLE IF EXISTS `smtp_config`;
DROP TABLE IF EXISTS `audit_log`;
DROP TABLE IF EXISTS `login_log`;
DROP TABLE IF EXISTS `user_deletion_audit`;
DROP TABLE IF EXISTS `user_info`;
