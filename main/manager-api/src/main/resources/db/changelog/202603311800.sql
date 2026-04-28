-- 儿童对话风险预警（P0+P1）：规则表、事件表、outbox、家长通知；配置走 sys_params JSON
CREATE TABLE IF NOT EXISTS `child_risk_rule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(64) NOT NULL COMMENT '展示名',
  `rule_type` VARCHAR(16) NOT NULL COMMENT 'KEYWORD/REGEX',
  `pattern` VARCHAR(512) NOT NULL,
  `risk_level` INT NOT NULL COMMENT '1最严重 3最轻',
  `category` VARCHAR(64) NOT NULL DEFAULT 'other',
  `sort_order` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='儿童风险关键词/正则规则';

CREATE TABLE IF NOT EXISTS `child_risk_event` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `device_id` VARCHAR(64) NOT NULL,
  `child_id` BIGINT NOT NULL,
  `parent_user_id` BIGINT NULL COMMENT '冗余：当时绑定的任一家长写库前可能为空',
  `session_id` VARCHAR(128) NULL,
  `risk_level` INT NOT NULL,
  `category` VARCHAR(64) NOT NULL,
  `source` VARCHAR(32) NOT NULL COMMENT 'ZhibAN_JSON/RULE/MERGED',
  `reason_public` VARCHAR(512) NULL,
  `status` VARCHAR(32) NOT NULL COMMENT 'SUPPRESSED_* / CREATED / NOTIFIED',
  `suppressed_reason` VARCHAR(128) NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_child_cat_time` (`child_id`, `category`, `create_time`),
  KEY `idx_create` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='儿童风险事件';

CREATE TABLE IF NOT EXISTS `child_risk_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `event_id` BIGINT NOT NULL,
  `channel` VARCHAR(32) NOT NULL DEFAULT 'MINI_APP',
  `status` VARCHAR(24) NOT NULL COMMENT 'PENDING/SUCCESS/FAILED',
  `attempts` INT NOT NULL DEFAULT 0,
  `next_retry_time` DATETIME NULL,
  `fail_message` VARCHAR(512) NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`),
  KEY `idx_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='风险投递 outbox';

CREATE TABLE IF NOT EXISTS `parent_risk_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `parent_user_id` BIGINT NOT NULL,
  `child_id` BIGINT NOT NULL,
  `event_id` BIGINT NOT NULL,
  `title` VARCHAR(128) NOT NULL,
  `summary` VARCHAR(512) NULL,
  `risk_level` INT NOT NULL,
  `is_read` TINYINT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_parent_read` (`parent_user_id`, `is_read`, `create_time`),
  KEY `idx_child` (`child_id`),
  KEY `idx_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长小程序风险通知';


INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.child_risk_config',
       '{"enabled":false,"cooldownMinutes":30,"notifyIfRiskLevelLte":3,"evalEveryNRounds":3}',
       'string', 1,
       '儿童风险：1最严重3最轻；notifyIfRiskLevelLte=3 表示 1~3 都通知；=2 则仅 1、2'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'server.child_risk_config');
