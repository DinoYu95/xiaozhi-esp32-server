-- 内测体验任务用户进度
CREATE TABLE IF NOT EXISTS `beta_mission_user_state` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户 id',
    `campaign_code` VARCHAR(32) NOT NULL DEFAULT 'beta_core_v1' COMMENT '任务包编码',
    `context_child_id` BIGINT NULL COMMENT '体验对象 device_child.id',
    `step_states` TEXT NOT NULL COMMENT '各步骤状态 JSON：pending/completed/skipped',
    `required_done_count` INT NOT NULL DEFAULT 0 COMMENT '必做完成数冗余',
    `pack_completed_at` DATETIME NULL COMMENT '必做全完成时间',
    `popup_dismissed` TINYINT NOT NULL DEFAULT 0 COMMENT '是否点过稍后再说',
    `risk_alert_visited` TINYINT NOT NULL DEFAULT 0 COMMENT 'B3 访问标记',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_user` (`parent_user_id`),
    KEY `idx_pack_completed` (`pack_completed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='内测体验任务用户进度';

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.beta_mission_enabled',
       'false',
       'string', 1,
       '内测体验任务总开关。true 且家长 is_beta_tester=1 时小程序显示内测任务入口。'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_params` s WHERE s.param_code = 'server.beta_mission_enabled'
);
