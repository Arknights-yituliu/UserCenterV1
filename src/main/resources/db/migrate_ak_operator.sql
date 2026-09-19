-- =============================================================
-- 增量迁移：游戏账号绑定、游戏角色信息、干员数据三张新表
-- 说明：本脚本只新增表，不 DROP 任何既有表，可直接在已有库上执行。
-- 对应设计：.yama/干员数据保存与读取API设计.md
-- =============================================================

-- -------------------------------------------------------------
-- 1. 游戏账号与用户中心账号的多对多绑定关系
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ak_account_binding` (
    `ak_uid`    VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '游戏账号UID，一套干员数据的归属键',
    `owner_uid` BIGINT      NOT NULL COMMENT '绑定此游戏账号的用户中心UID，同一游戏账号可有多个用户',
    `client_id` VARCHAR(64) NOT NULL COMMENT '绑定时的接入客户端ID，供鉴权隔离',
    PRIMARY KEY (`ak_uid`, `owner_uid`, `client_id`),
    KEY `idx_owner_accounts` (`owner_uid`, `client_id`, `ak_uid`) COMMENT '按当前用户和客户端查询已绑定游戏账号'
) ENGINE = InnoDB COMMENT = '游戏账号与用户中心账号多对多绑定关系，按三字段去重';

-- -------------------------------------------------------------
-- 2. 按游戏账号 UID 去重的共享角色信息
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ak_player_info` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '游戏角色数据库行ID',
    `ak_uid`      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '游戏角色UID，与绑定表及干员表采用相同类型和排序规则',
    `create_time` BIGINT      NOT NULL COMMENT '记录创建时间的Unix毫秒时间戳，服务端生成',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_ak_uid` (`ak_uid`) COMMENT '每个游戏角色UID只保存一份角色信息'
) ENGINE = InnoDB COMMENT = '按游戏角色UID去重的共享游戏角色信息';

-- -------------------------------------------------------------
-- 3. 游戏账号干员数据（一行一个干员，按 ak_uid 归属）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operator_progression_data` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '数据库自增行ID，响应中称recordId',
    `ak_uid`           VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '游戏账号UID，按此账号读取与更新',
    `operator_id`      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '稳定干员编码，对应JSON中的id',
    `rarity`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '干员星级，0表示未提供；业务代码筛选',
    `level`            SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '干员等级',
    `evolve_phase`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '精英化阶段',
    `main_skill_level` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '基础技能等级',
    `skill1`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '技能1等级或状态，具体业务含义待确认',
    `skill2`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '技能2等级或状态，具体业务含义待确认',
    `skill3`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '技能3等级或状态，具体业务含义待确认',
    `equip_x`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'X模组数值，具体业务含义待确认',
    `equip_y`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Y模组数值，具体业务含义待确认',
    `equip_d`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'D模组数值，具体业务含义待确认',
    `equip_a`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'A模组数值，具体业务含义待确认',
    `equip_b`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'B模组数值，具体业务含义待确认',
    `potential_rank`   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '潜能等级，不是星级',
    `updated_at`       DATETIME(3) NOT NULL COMMENT '该干员最后一次实际变更时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ak_operator` (`ak_uid`, `operator_id`) COMMENT '一个游戏账号下每个干员编码仅一条记录，支持全量读取'
) ENGINE = InnoDB COMMENT = '游戏账号干员数据';
