-- 家长影子任务（限时带娃目标）：孩子与智伴对话时注入 environment_context，规则仍优先
CREATE TABLE IF NOT EXISTS `parent_shadow_mission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID（与 ai_device.id 一致）',
    `child_id` BIGINT NOT NULL COMMENT 'device_child.id',
    `parent_user_id` BIGINT NOT NULL COMMENT '创建任务的家长用户ID',
    `title` VARCHAR(128) NOT NULL COMMENT '任务短标题',
    `instructions` VARCHAR(2000) NOT NULL COMMENT '给模型的详细说明（话术与步骤偏好）',
    `ends_at` DATETIME(3) NOT NULL COMMENT '失效时间',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active/cancelled/expired',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    `update_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) NOT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_shadow_device_child_status` (`device_id`, `child_id`, `status`),
    INDEX `idx_shadow_ends` (`ends_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长影子任务（限时）';
