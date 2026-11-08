-- =============================================================
-- 模块三：终末地（玩家数据、角色绑定）
--
-- 依赖：先执行 schema.sql 建库，再执行 schema-user.sql（绑定关系逻辑上指向 user_info.uid）。
-- 本文件可独立重跑：所有建表均为 DROP TABLE IF EXISTS + CREATE。
-- 排序规则：全部 utf8mb4_bin。role_id 为游戏内角色 UID，区分大小写，
-- 用 general_ci 会把仅大小写不同的角色误判为同一角色。
-- =============================================================

USE `user_center`;

-- -------------------------------------------------------------
-- 1. 终末地玩家数据（一行一个角色，按 role_id + server_id 去重，全部绑定用户共享）
--
-- 与 ak_operator_state「一行一个干员、按业务字段拆列」不同，终末地角色卡片是
-- 结构多变的大块 JSON（base/chars/domain/...），拆列成本高且随游戏版本频繁变化，
-- 因此按需求直接把整份 JSON 文本存为一列，由写入方维护结构，服务端只做大小与归属校验。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ef_player_data`;
CREATE TABLE `ef_player_data` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '数据库自增行ID',
    `role_id`     VARCHAR(32) NOT NULL COMMENT '终末地游戏内角色UID（card/detail 的 roleId），数据归属键',
    `server_id`   VARCHAR(16) NOT NULL DEFAULT '' COMMENT '服务器ID（国服常见1），与 role_id 共同定位角色',
    `player_data` LONGTEXT    NOT NULL COMMENT '玩家数据原始JSON文本，结构由写入方维护，服务端不拆分',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近一次保存时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ef_role` (`role_id`, `server_id`) COMMENT '同一服务器下的同一角色只保存一份数据'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '终末地玩家数据，一行一个角色，整份JSON文本存储';

-- -------------------------------------------------------------
-- 2. 终末地角色与用户中心账号的绑定关系
--
-- 与 ak_account_binding 同构：一个 uid 可绑定多个角色，一个角色也可被多个 uid 绑定；
-- 玩家数据按角色存一份，绑定表只存授权关系，供后续读取接口做归属校验。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ef_account_binding`;
CREATE TABLE `ef_account_binding` (
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`              VARCHAR(32) NOT NULL COMMENT '终末地游戏内角色UID',
    `server_id`            VARCHAR(16) NOT NULL DEFAULT '' COMMENT '服务器ID；寻访记录导入不区分服务器，固定为空串',
    `owner_uid`            BIGINT      NOT NULL COMMENT '绑定该角色的用户中心UID；0 表示匿名导入档案，不对应任何用户',
    `hg_uid`               VARCHAR(64) NOT NULL DEFAULT '' COMMENT '鹰角通行证UID（原 endministrator_info.uid）',
    `nick_name`            VARCHAR(64) NOT NULL DEFAULT '' COMMENT '玩家昵称（原 endministrator_info.nick_name）',
    `player_data_imported` TINYINT     NOT NULL DEFAULT 0 COMMENT '该角色是否已导入过玩家数据（角色详情）：0否 1是',
    `pool_record_imported` TINYINT     NOT NULL DEFAULT 0 COMMENT '该角色是否已导入过寻访记录：0否 1是',
    `create_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定创建时间：首次导入该角色数据时写入',
    `update_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '该角色数据最近一次导入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ef_owner` (`role_id`, `server_id`, `owner_uid`) COMMENT '同一角色、服务器与用户只保留一条绑定关系',
    KEY `idx_ef_owner_update` (`owner_uid`, `update_time`) COMMENT '按用户查询已绑定角色并按最近导入时间倒序'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '终末地角色与用户中心账号绑定关系及角色档案';
