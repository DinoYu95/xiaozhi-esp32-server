-- 家长-设备绑定：角色与状态（设备家庭共享一期）
ALTER TABLE `parent_device_binding`
    ADD COLUMN `role` VARCHAR(16) NOT NULL DEFAULT 'owner' COMMENT 'owner|member' AFTER `bind_source`,
    ADD COLUMN `is_primary` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=首绑Owner' AFTER `role`,
    ADD COLUMN `invited_by` BIGINT NULL DEFAULT NULL COMMENT 'member 时邀请人 parent_user_id' AFTER `is_primary`,
    ADD COLUMN `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|removed' AFTER `invited_by`,
    ADD COLUMN `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `create_time`;

UPDATE `parent_device_binding` SET `role` = 'owner', `is_primary` = 1, `status` = 'active' WHERE `role` IS NULL OR `role` = '';

CREATE TABLE IF NOT EXISTS `device_invite` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备 MAC',
    `inviter_parent_id` BIGINT NOT NULL COMMENT '邀请人，须为 owner',
    `token_hash` VARCHAR(128) NOT NULL COMMENT 'inviteToken SHA-256',
    `expires_at` DATETIME NOT NULL COMMENT '过期时间',
    `max_uses` INT NOT NULL DEFAULT 1 COMMENT '最大可用次数',
    `used_count` INT NOT NULL DEFAULT 0 COMMENT '已使用次数',
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|expired|revoked|exhausted',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `revoked_at` DATETIME NULL DEFAULT NULL COMMENT '撤销时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token_hash` (`token_hash`),
    KEY `idx_device_status` (`device_id`, `status`),
    KEY `idx_inviter` (`inviter_parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备邀请加入（家长共享）';
