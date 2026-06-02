-- 家长端风险观察：偏好、观察项、规则归属、AI 配置

ALTER TABLE `child_risk_rule`
    ADD COLUMN `rule_scope` VARCHAR(16) NOT NULL DEFAULT 'PLATFORM' COMMENT 'PLATFORM/PARENT' AFTER `category`,
    ADD COLUMN `parent_user_id` BIGINT NULL COMMENT 'PARENT 规则创建家长' AFTER `rule_scope`,
    ADD COLUMN `child_id` BIGINT NULL COMMENT 'PARENT 规则绑定孩子 device_child.id' AFTER `parent_user_id`,
    ADD KEY `idx_parent_child_rule` (`child_id`, `status`);

CREATE TABLE IF NOT EXISTS `parent_risk_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_user_id` BIGINT NOT NULL,
    `child_id` BIGINT NOT NULL COMMENT 'device_child.id',
    `focus_domains` VARCHAR(512) NULL COMMENT 'JSON 数组，如 ["peer_relation","online_safety"]',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_child_pref` (`parent_user_id`, `child_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长风险关注侧重';

CREATE TABLE IF NOT EXISTS `parent_risk_watch` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_user_id` BIGINT NOT NULL,
    `child_id` BIGINT NOT NULL,
    `watch_type` VARCHAR(16) NOT NULL COMMENT 'KEYWORD/EVALUATOR',
    `risk_domain` VARCHAR(32) NOT NULL,
    `name` VARCHAR(128) NOT NULL,
    `description` VARCHAR(512) NULL,
    `trigger_hint` VARCHAR(256) NULL,
    `pattern` VARCHAR(512) NULL COMMENT 'KEYWORD 模式',
    `rule_type` VARCHAR(16) NULL DEFAULT 'KEYWORD',
    `risk_level` INT NULL DEFAULT 2,
    `category` VARCHAR(64) NULL DEFAULT 'other',
    `instructions` TEXT NULL COMMENT 'EVALUATOR 判别 instructions',
    `allowed_categories` VARCHAR(512) NULL COMMENT 'JSON 数组',
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/enabled/rejected/disabled',
    `audit_note` VARCHAR(512) NULL COMMENT '运营审核备注',
    `reject_reason` VARCHAR(512) NULL,
    `linked_rule_id` BIGINT NULL COMMENT 'KEYWORD 审核通过后 child_risk_rule.id',
    `version` INT NOT NULL DEFAULT 1,
    `sort_order` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_parent_child_status` (`parent_user_id`, `child_id`, `status`),
    KEY `idx_child_enabled` (`child_id`, `status`, `watch_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长风险观察项';

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.parent_risk_watch_assist_config',
       '{"enabled":true,"llmModelId":"","baseUrl":"","apiKey":"","modelName":""}',
       'string', 1,
       '家长小程序 AI 生成风险观察。llmModelId 或 baseUrl+apiKey 二选一；与 parent_skill_assist_config 分离。'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_params` s WHERE s.param_code = 'server.parent_risk_watch_assist_config'
);
