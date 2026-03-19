-- 家长端设备规则表：家长为该设备设置的「不要讲什么」等规则，供设备对话时注入 prompt
CREATE TABLE IF NOT EXISTS `parent_device_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备ID（与 ai_device.id 一致）',
    `parent_user_id` BIGINT NOT NULL COMMENT '添加该规则的家长用户ID',
    `rule_text` VARCHAR(500) NOT NULL COMMENT '规则内容，如「不要讲鬼故事」「少提零食」',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_parent_device_rule_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端设备规则表';
