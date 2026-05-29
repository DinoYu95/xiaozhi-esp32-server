-- 家长端内测反馈
ALTER TABLE `parent_user`
    ADD COLUMN `is_beta_tester` TINYINT NOT NULL DEFAULT 0 COMMENT '是否内测用户：1=可提交反馈' AFTER `avatar_url`;

CREATE TABLE IF NOT EXISTS `parent_feedback` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `feedback_no` VARCHAR(32) NOT NULL COMMENT '展示编号 FB-yyyyMMdd-xxxxx',
    `parent_user_id` BIGINT NOT NULL COMMENT '提交家长 id',
    `category` VARCHAR(32) NOT NULL COMMENT 'device_bind/child_voiceprint/chat_voice/skill/shadow_mission/other',
    `description` TEXT NOT NULL COMMENT '问题描述',
    `blocking` TINYINT NOT NULL DEFAULT 0 COMMENT '是否阻塞使用 0否1是',
    `allow_contact` TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许联系 0否1是',
    `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/resolved/wont_fix',
    `context_snapshot` TEXT NULL COMMENT '自动上下文 JSON',
    `image_urls` VARCHAR(2048) NULL COMMENT '截图 URL 列表 JSON 数组',
    `admin_note` TEXT NULL COMMENT '内部备注',
    `wont_fix_reason` VARCHAR(512) NULL COMMENT '不修复原因（status=wont_fix 时）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_feedback_no` (`feedback_no`),
    KEY `idx_parent_user` (`parent_user_id`, `create_time`),
    KEY `idx_status` (`status`, `create_time`),
    KEY `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端内测反馈';

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.beta_feedback_enabled',
       'true',
       'string', 1,
       '内测反馈总开关。true 且家长 is_beta_tester=1 时小程序显示反馈入口并可提交。'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_params` s WHERE s.param_code = 'server.beta_feedback_enabled'
);
