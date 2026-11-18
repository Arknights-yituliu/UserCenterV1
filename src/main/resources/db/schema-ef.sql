-- =============================================================
-- 模块三：终末地（玩家数据、角色绑定、寻访记录）
--
-- 依赖：先执行 schema.sql 建库，再执行 schema-user.sql（绑定关系逻辑上指向 user_info.uid）。
-- 本文件可独立重跑：所有建表均为 CREATE TABLE IF NOT EXISTS，重复执行不会删除
-- 已有数据，也不会修改已有表结构；需要清空重建请显式执行同目录的 reset.sql。
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
CREATE TABLE IF NOT EXISTS `ef_player_data` (
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
-- server_type / server_name 为客户端保存玩家数据时上报的服务器展示信息，可选：
-- 未上报时落空串、后续导入沿用旧值，不参与唯一键与归属校验。
-- nick_name 由寻访记录导入写入，三者都只用于角色列表展示。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ef_account_binding` (
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id`              VARCHAR(32) NOT NULL COMMENT '终末地游戏内角色UID',
    `server_id`            VARCHAR(16) NOT NULL DEFAULT '' COMMENT '服务器ID；寻访记录导入不区分服务器，固定为空串',
    `server_type`          VARCHAR(64) NOT NULL DEFAULT '' COMMENT '服务器类型（客户端保存玩家数据时上报，可选）',
    `server_name`          VARCHAR(64) NOT NULL DEFAULT '' COMMENT '服务器名称（客户端保存玩家数据时上报，可选）',
    `owner_uid`            BIGINT      NOT NULL COMMENT '绑定该角色的用户中心UID；0 表示匿名导入档案，不对应任何用户',
    `hg_uid`               VARCHAR(64) NOT NULL DEFAULT '' COMMENT '鹰角通行证UID（原 endministrator_info.uid）',
    `nick_name`            VARCHAR(64) NOT NULL DEFAULT '' COMMENT '玩家昵称（原 endministrator_info.nick_name，寻访记录导入时写入）',
    `player_data_imported` TINYINT     NOT NULL DEFAULT 0 COMMENT '该角色是否已导入过玩家数据（角色详情）：0否 1是',
    `pool_record_imported` TINYINT     NOT NULL DEFAULT 0 COMMENT '该角色是否已导入过寻访记录：0否 1是',
    `create_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定创建时间：首次导入该角色数据时写入',
    `update_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '该角色数据最近一次导入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ef_owner` (`role_id`, `server_id`, `owner_uid`) COMMENT '同一角色、服务器与用户只保留一条绑定关系',
    KEY `idx_ef_owner_update` (`owner_uid`, `update_time`) COMMENT '按用户查询已绑定角色并按最近导入时间倒序'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '终末地角色与用户中心账号绑定关系、角色档案及服务器展示信息';

-- -------------------------------------------------------------
-- 3. 寻访记录导入任务
--
-- 创建任务只落库 hgToken 与归属，调度器每秒取一条 start_flag = 0 的任务执行导入；
-- token 为敏感凭证，创建 15 分钟后由定时任务覆盖为占位值，避免长期留存。
-- owner_uid 允许 NULL：匿名接口下线前创建、尚未执行的历史任务没有归属。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ef_player_pool_record_task` (
    `task_id`       VARCHAR(64)  NOT NULL COMMENT '任务ID（task + 雪花ID），创建接口返回给调用方',
    `token`         VARCHAR(512) NULL COMMENT '前端提交的 hgToken，完成后替换为占位值，过期后被覆盖',
    `complete_flag` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已完成导入：0否 1是',
    `role_id`       VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '终末地游戏内角色UID，导入完成后回填',
    `owner_uid`     BIGINT       NULL COMMENT '创建任务的用户中心UID；匿名接口遗留任务为NULL',
    `start_flag`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已被调度器取走开始处理：0否 1是',
    `create_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '任务创建时间',
    `update_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '任务最近更新时间',
    PRIMARY KEY (`task_id`),
    KEY `idx_ef_task_start` (`start_flag`, `create_time`) COMMENT '调度器取最早创建且未开始的任务',
    KEY `idx_ef_task_create_time` (`create_time`) COMMENT '按创建时间区间覆盖过期 token',
    KEY `idx_ef_task_owner` (`owner_uid`) COMMENT '按创建者校验任务归属'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '终末地寻访记录导入任务';

-- -------------------------------------------------------------
-- 4. 干员寻访记录
--
-- 一行一条干员寻访记录，唯一键 role_id + seq_id：重复导入时走
-- ON DUPLICATE KEY UPDATE「只补不毁」，新值为空不清空历史值。
-- 赠礼类记录（kind=gift_intel_book）没有 char_id / char_name，
-- 由服务端映射为 gift_ 前缀伪编码落库，保证记录条数不丢、也不产生空值行。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ef_character_pool_record` (
    `id`         BIGINT       NOT NULL COMMENT '记录ID，由雪花算法生成',
    `role_id`    VARCHAR(32)  NOT NULL COMMENT '终末地游戏内角色UID',
    `pool_id`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '卡池ID',
    `pool_name`  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '卡池名称',
    `char_id`    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '角色编码；赠礼类记录为 gift_ 前缀伪编码',
    `char_name`  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '角色名称；赠礼类记录为展示名',
    `rarity`     INT          NOT NULL DEFAULT 0 COMMENT '稀有度',
    `is_free`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否免费抽取：0否 1是',
    `is_new`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否新角色：0否 1是',
    `gacha_ts`   VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '寻访时间戳（毫秒字符串，上游原样保存）',
    `seq_id`     VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '记录序列ID，与 role_id 共同去重',
    `lang`       VARCHAR(16)  NOT NULL DEFAULT 'zh-cn' COMMENT '数据语言',
    `pool_type`  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '卡池类型（E_CharacterGachaPoolType_*）',
    `server_id`  VARCHAR(16)  NOT NULL DEFAULT '1' COMMENT '服务器ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ef_char_role_seq` (`role_id`, `seq_id`) COMMENT '同一角色的同一序列只保留一条记录',
    KEY `idx_ef_char_role_pool` (`role_id`, `pool_type`) COMMENT '按角色读取记录、统计新手卡池抽数'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '终末地干员寻访记录';

-- -------------------------------------------------------------
-- 5. 武器寻访记录
--
-- 与干员记录同构，写入走 INSERT IGNORE，由唯一键 role_id + seq_id 静默去重。
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ef_weapon_pool_record` (
    `id`          BIGINT       NOT NULL COMMENT '记录ID，由雪花算法生成',
    `role_id`     VARCHAR(32)  NOT NULL COMMENT '终末地游戏内角色UID',
    `pool_id`     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '卡池ID',
    `pool_name`   VARCHAR(128) NOT NULL DEFAULT '' COMMENT '卡池名称',
    `weapon_id`   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '武器ID',
    `weapon_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '武器名称',
    `weapon_type` VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '武器类型',
    `rarity`      INT          NOT NULL DEFAULT 0 COMMENT '稀有度',
    `is_new`      TINYINT      NOT NULL DEFAULT 0 COMMENT '是否新武器：0否 1是',
    `gacha_ts`    VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '寻访时间戳（毫秒字符串，上游原样保存）',
    `seq_id`      VARCHAR(32)  NOT NULL DEFAULT '' COMMENT '记录序列ID，与 role_id 共同去重',
    `lang`        VARCHAR(16)  NOT NULL DEFAULT 'zh-cn' COMMENT '数据语言',
    `server_id`   VARCHAR(16)  NOT NULL DEFAULT '1' COMMENT '服务器ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ef_weapon_role_seq` (`role_id`, `seq_id`) COMMENT '同一角色的同一序列只保留一条记录',
    KEY `idx_ef_weapon_role` (`role_id`) COMMENT '按角色读取武器寻访记录'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '终末地武器寻访记录';
