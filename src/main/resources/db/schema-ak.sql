-- =============================================================
-- 模块二：明日方舟（账号绑定、干员、物品、皮肤、player/info 镜像、统计结果）
--
-- 依赖：先执行 schema.sql 建库，再执行 schema-user.sql（绑定关系逻辑上指向 user_info.uid）。
-- 本文件可独立重跑：所有建表均为 DROP TABLE IF EXISTS + CREATE。
-- 排序规则：全部 utf8mb4_bin。游戏账号/角色 UID 与皮肤编码都区分大小写，
-- 用 general_ci 会把仅大小写不同的账号或皮肤误判为同一条，导致数据被错误合并。
-- =============================================================

USE `user_center`;

-- -------------------------------------------------------------
-- 1. 游戏账号与用户中心账号的多对多绑定关系
--
-- 一个 uid 可绑定多个 ak_uid，一个 ak_uid 也可被多个 uid 绑定；
-- 干员数据只按 ak_uid 存一份，绑定表只存授权关系。
-- 主键为代理自增 id，(ak_uid, owner_uid) 由 uk_ak_owner 保证业务唯一，
-- 以便 MyBatis-Plus 实体可用单主键。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_account_binding`;
CREATE TABLE `ak_account_binding` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ak_uid`      VARCHAR(32) NOT NULL COMMENT '游戏账号UID，一套干员数据的归属键',
    `owner_uid`   BIGINT      NOT NULL COMMENT '绑定此游戏账号的用户中心UID，同一游戏账号可有多个用户',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '绑定关系创建时间：首次导入该账号数据时建立绑定并写入',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '该账号干员数据最近一次导入时间：账号列表据此倒序，前端默认展示最新导入的数据',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ak_owner` (`ak_uid`, `owner_uid`) COMMENT '同一游戏账号与用户只保留一条绑定关系',
    KEY `idx_owner_accounts` (`owner_uid`, `ak_uid`) COMMENT '按当前用户查询已绑定游戏账号',
    KEY `idx_owner_update_time` (`owner_uid`, `update_time`) COMMENT '按当前用户查询已绑定账号并按最近导入时间倒序'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '游戏账号与用户中心账号多对多绑定关系，按用户和游戏账号去重';

-- -------------------------------------------------------------
-- 2. 按游戏账号 UID 去重的共享角色信息
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_player_info`;
CREATE TABLE `ak_player_info` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '游戏角色数据库行ID',
    `ak_uid`      VARCHAR(32) NOT NULL COMMENT '游戏角色UID，与绑定表及干员表采用相同类型和排序规则',
    `create_time` BIGINT      NOT NULL COMMENT '记录创建时间的Unix毫秒时间戳，服务端生成',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_ak_uid` (`ak_uid`) COMMENT '每个游戏角色UID只保存一份角色信息'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '按游戏角色UID去重的共享游戏角色信息';

-- -------------------------------------------------------------
-- 3. 用户默认展示的明日方舟数据来源偏好
--
-- 与 ak_account_binding（授权关系，多对多、可共享）不同，本表是用户维度的展示偏好：
-- 每个用户最多一条，用于标记「读取干员数据不传 akUid 时返回哪一个来源」。
-- source 取值与接口一致：skland 森空岛自动导入、manual 用户手动上传；
-- ak_uid 为对应来源的归属键：skland 存游戏账号 UID，manual 存用户中心 UID。
-- 偏好只存指向、不复制数据；目标来源失效时读取侧回退到最近变更的条目，不阻断请求。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_default_source`;
CREATE TABLE `ak_default_source` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `uid`         BIGINT      NOT NULL COMMENT '用户中心UID',
    `source`      VARCHAR(16) NOT NULL COMMENT '来源：skland 森空岛自动导入 / manual 用户手动上传',
    `ak_uid`      VARCHAR(32) NOT NULL COMMENT '对应来源的归属键：skland 为游戏账号UID，manual 为用户中心UID',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次设置默认来源的时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近一次修改默认来源的时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_default_source_uid` (`uid`) COMMENT '每个用户只保留一条默认来源偏好'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '用户默认展示的明日方舟数据来源偏好，一行一个用户';

-- -------------------------------------------------------------
-- 4. 游戏账号干员养成状态（一行一个干员，按 ak_uid 归属）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_operator_state`;
CREATE TABLE `ak_operator_state` (
    `id`               BIGINT      NOT NULL AUTO_INCREMENT COMMENT '数据库自增行ID，响应中称recordId',
    `ak_uid`           VARCHAR(32) NOT NULL COMMENT '游戏账号UID，按此账号读取与更新',
    `operator_id`      VARCHAR(64) NOT NULL COMMENT '稳定干员编码，对应JSON中的id',
    `owned`            TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否持有：0否，1是；历史数据均为已持有，默认1',
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
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '游戏账号干员养成状态（含是否持有），一行一个干员';

-- -------------------------------------------------------------
-- 5. 用户手动修改版干员数据（一行一个干员，按 uid 归属，每个用户一份）
--
-- 与 ak_operator_state（游戏账号自动导入、被全部绑定用户共享）不同，本表按用户维度
-- 保存用户手工调整后的干员数据。一个 uid 只能有一份手动修改版，
-- 由唯一键 uk_uid_operator_state_manual 保证，不存在版本号或历史表。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_operator_state_manual`;
CREATE TABLE `ak_operator_state_manual` (
    `id`               BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键，响应中称recordId',
    `uid`              BIGINT          NOT NULL COMMENT '用户中心UID，手动修改版按用户归属',
    `operator_id`      VARCHAR(64)     NOT NULL COMMENT '稳定干员编码，对应JSON中的id',
    `owned`            TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否持有：0否，1是',
    `rarity`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '干员星级，0表示未提供；业务代码筛选',
    `level`            SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '干员等级',
    `evolve_phase`     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '精英化阶段',
    `main_skill_level` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '基础技能等级',
    `skill1`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '技能1等级或状态',
    `skill2`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '技能2等级或状态',
    `skill3`           TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '技能3等级或状态',
    `equip_x`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'X模组数值',
    `equip_y`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Y模组数值',
    `equip_d`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'D模组数值',
    `equip_a`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'A模组数值',
    `equip_b`          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'B模组数值',
    `potential_rank`   TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '潜能等级，不是星级',
    `updated_at`       DATETIME(3)     NOT NULL COMMENT '该干员手动版最后一次实际变更时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_uid_operator_state_manual` (`uid`, `operator_id`) COMMENT '一个用户下每个干员编码仅一条手动记录'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '用户手动修改版干员数据，一行一个干员，每个用户一份';

-- -------------------------------------------------------------
-- 6. 游戏账号物品存量（一行一个账号，整份 JSON 文本存储）
--
-- 与 ak_operator_state「一行一个干员、按业务字段拆列」不同，物品数量每次上报几乎
-- 全量变化，按行拆表需要先比对再逐行写入，成本高且没有行级读取需求，因此把
-- items 数组整体序列化为一个 JSON 文本列，每次携带 items 时整体覆盖。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_item_data`;
CREATE TABLE `ak_item_data` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '数据库自增行ID',
    `ak_uid`      VARCHAR(32) NOT NULL COMMENT '游戏账号UID，与绑定表及干员表采用相同类型和排序规则',
    `item_data`   LONGTEXT    NOT NULL COMMENT '物品存量JSON文本，items数组整体序列化，元素形如{"itemId":"3001","count":111}',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次保存时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近一次携带items的上传时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_item_ak_uid` (`ak_uid`) COMMENT '每个游戏账号只保存一份物品数据'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '游戏账号物品存量，整份JSON文本存储，一行一个账号';

-- -------------------------------------------------------------
-- 7. 游戏账号已拥有皮肤（一行一个皮肤，按 ak_uid 归属）
--
-- 与 ak_operator_state 同粒度：按 ak_uid 共享，多个绑定用户看到同一份数据。
-- skin_id 含 @ 与 #，且区分大小写，故整表使用 utf8mb4_bin；uk_ak_skin 的前缀即
-- ak_uid，全量读取与按账号清理都走这条唯一键。
-- gain_ts 为获得时间（Unix 秒），0 表示时间未知（活动补发或历史数据），不可当 1970 年处理。
-- updated_at 由服务端在真正发生变化时显式写入，不使用 ON UPDATE：本表按「比对后只写变化行」
-- 的方式维护，自动刷新会让该列不再表示「最后一次实际变更」。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_skin_state`;
CREATE TABLE `ak_skin_state` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `ak_uid`     VARCHAR(32)  NOT NULL COMMENT '游戏账号UID，按此账号归属皮肤数据',
    `skin_id`    VARCHAR(128) NOT NULL COMMENT '皮肤编码，形如 char_4064_mlynar@epoque#28，区分大小写',
    `gain_ts`    INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '获得时间Unix秒，0表示未知',
    `updated_at` DATETIME(3)  NOT NULL COMMENT '该皮肤最后一次实际变更时间，仅真正发生变化时刷新',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ak_skin` (`ak_uid`, `skin_id`) COMMENT '一个账号下同一皮肤仅一条，全量读取与覆盖写入都走这条键'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '游戏账号已拥有皮肤，一行一个皮肤，按 ak_uid 归属';

-- -------------------------------------------------------------
-- 8. 森空岛 player/info 数据镜像（一行一个 ak_uid，只保留最新一份）
--
-- 定位是「原样留存」：前端把 player/info 的数据传回来，服务端不做解析、不做结构校验、
-- 不抽取 data 节点，收到什么文本就存什么，用于后续离线解析、比对与回溯。
-- 存储关系为「原始数据 → 派生数据」：ak_skin_state、ak_operator_state 等表都可由本表重新解析得到，
-- 派生逻辑写错或上游加字段时，可直接用本表重跑，不必再向森空岛请求一次。
-- 与 ak_item_data 同构：一行一账号、文本整段覆盖、时间由数据库默认值维护，故写法保持一致。
-- 不做内容比对，每次上报直接整段覆盖；update_time 用 ON UPDATE 自动刷新，表示「最后一次覆盖写入」
-- 而非「内容发生变化」，下游不要拿它判断数据是否真的变了。
-- 单个账号的数据约 200KB，故用 LONGTEXT，且不对该列建索引、不做前缀索引。
-- 需要历史版本时另建归档表，不要在本表加版本维度。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_player_info_snapshot`;
CREATE TABLE `ak_player_info_snapshot` (
    `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '数据库自增行ID',
    `ak_uid`      VARCHAR(32) NOT NULL COMMENT '游戏账号UID，与绑定表及干员表采用相同类型和排序规则',
    `player_data` LONGTEXT    NOT NULL COMMENT '前端上报的 player/info 文本，原样落库，服务端不解析不校验结构',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次写入时间',
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '最近一次覆盖写入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_player_info_ak_uid` (`ak_uid`) COMMENT '每个游戏账号只保留一份最新镜像'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '森空岛 player/info 数据镜像，一行一个账号，原样留存前端上报文本';

-- -------------------------------------------------------------
-- 9. 干员养成数据统计结果表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_operator_state_statistics`;
CREATE TABLE `ak_operator_state_statistics` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `char_id`         VARCHAR(64)     NOT NULL COMMENT '干员编码，对应 ak_operator_state.operator_id',
    `own`             BIGINT UNSIGNED NOT NULL COMMENT '拥有该干员的记录总数（operatorOwnedCount）',
    `sample_size`     BIGINT UNSIGNED NOT NULL COMMENT '有效样本数：最近一次数据变更时间不早于该干员实装时间的游戏账号数',
    `elite`           VARCHAR(512)    NOT NULL COMMENT '精英化阶段分布（JSON 字符串），对应 evolve_phase',
    `skill1`          VARCHAR(512)    NOT NULL COMMENT '技能1等级或状态分布（JSON 字符串）',
    `skill2`          VARCHAR(512)    NOT NULL COMMENT '技能2等级或状态分布（JSON 字符串）',
    `skill3`          VARCHAR(512)    NOT NULL COMMENT '技能3等级或状态分布（JSON 字符串）',
    `mod_a`           VARCHAR(512)    NOT NULL COMMENT 'A模组数值分布（JSON 字符串），对应 equip_a',
    `mod_x`           VARCHAR(512)    NOT NULL COMMENT 'X模组数值分布（JSON 字符串），对应 equip_x',
    `mod_y`           VARCHAR(512)    NOT NULL COMMENT 'Y模组数值分布（JSON 字符串），对应 equip_y',
    `mod_d`           VARCHAR(512)    NOT NULL COMMENT 'D模组数值分布（JSON 字符串），对应 equip_d',
    `mod_b`           VARCHAR(512)    NOT NULL COMMENT 'B模组数值分布（JSON 字符串），对应 equip_b',
    `statistics_time` DATETIME(3)     NOT NULL COMMENT '本轮统计的完成时间',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_char_id` (`char_id`) COMMENT '一个干员只保留最新一份统计'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '干员养成数据统计结果，一行一个干员，只保留最新一轮';

-- -------------------------------------------------------------
-- 10. 皮肤持有统计结果表
--
-- 与 ak_operator_state_statistics 同构：一行一个皮肤，只保留最新一轮统计结果，
-- 由定时任务在同一事务内「清表 + 批量插入」整表替换，读方要么看到旧快照、要么看到新快照。
-- skin_id 形如 char_4064_mlynar@epoque#28，区分大小写，故表级排序规则用 utf8mb4_bin；
-- total_accounts 是同一轮扫描得到的账号总数（持有率分母），冗余在每行便于前端直接相除。
-- 不存获得时间：玩家数据只能算出样本中的最早/最晚，容易被误读成上线时间或「是否在售」，
-- 这两类信息由游戏静态数据与运营配置负责，不从玩家数据反推。
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ak_skin_statistics`;
CREATE TABLE `ak_skin_statistics` (
    `id`              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    `skin_id`         VARCHAR(128)    NOT NULL COMMENT '皮肤编码，对应 ak_skin_state.skin_id，区分大小写',
    `own`             BIGINT UNSIGNED NOT NULL COMMENT '拥有该皮肤的账号数',
    `total_accounts`  BIGINT UNSIGNED NOT NULL COMMENT '本轮参与统计的账号总数（持有率分母）：上报过皮肤数据的账号数',
    `statistics_time` DATETIME(3)     NOT NULL COMMENT '本轮统计的完成时间',
    `create_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `update_time`     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skin_id` (`skin_id`) COMMENT '一个皮肤只保留最新一份统计'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_bin COMMENT = '皮肤持有统计结果，一行一个皮肤，只保留最新一轮';
