-- DevOps 硬件 OTA：ai_device 扩展 + 新包/发布/白名单模型（k230 SWU 走此链路）

CREATE TABLE IF NOT EXISTS `ota_hardware_type` (
    `hw_key` VARCHAR(64) NOT NULL COMMENT '与 SWU 文件名 hardware 段一致',
    `name` VARCHAR(128) NOT NULL,
    `description` TEXT NULL,
    `enabled` TINYINT(1) NOT NULL DEFAULT 1,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`hw_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OTA 硬件类型枚举';

INSERT INTO `ota_hardware_type` (`hw_key`, `name`, `description`, `enabled`)
VALUES ('k230_linux_board', 'K230 Linux 板', '默认硬件类型', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

CREATE TABLE IF NOT EXISTS `ota_package` (
    `id` VARCHAR(36) NOT NULL,
    `type` VARCHAR(16) NOT NULL COMMENT 'system|app',
    `hardware` VARCHAR(64) NOT NULL,
    `version` VARCHAR(32) NOT NULL,
    `channel` VARCHAR(16) NOT NULL COMMENT 'stable|beta',
    `filename` VARCHAR(256) NOT NULL,
    `oss_key` VARCHAR(512) NOT NULL,
    `size_bytes` BIGINT NOT NULL DEFAULT 0,
    `sha256` VARCHAR(64) NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|published|archived',
    `notes` TEXT NULL,
    `created_by` VARCHAR(64) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ota_pkg_hw_ch_type` (`hardware`, `channel`, `type`, `status`),
    KEY `idx_ota_pkg_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OTA SWU 包元数据';

CREATE TABLE IF NOT EXISTS `ota_whitelist_pool` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(128) NOT NULL,
    `description` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OTA 白名单池';

CREATE TABLE IF NOT EXISTS `ota_whitelist_pool_device` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `pool_id` BIGINT NOT NULL,
    `mac_address` VARCHAR(64) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ota_pool_mac` (`pool_id`, `mac_address`),
    KEY `idx_ota_pool_device_mac` (`mac_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OTA 白名单池设备';

CREATE TABLE IF NOT EXISTS `ota_release` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `package_id` VARCHAR(36) NOT NULL,
    `channel` VARCHAR(16) NOT NULL COMMENT 'stable|beta',
    `rollout_percent` INT NOT NULL DEFAULT 100,
    `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active|rolled_back|superseded',
    `previous_release_id` BIGINT NULL,
    `extra_mac_addresses` TEXT NULL COMMENT 'JSON 数组，额外命中 MAC',
    `published_by` VARCHAR(64) NULL,
    `published_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ota_rel_status` (`status`, `channel`),
    KEY `idx_ota_rel_pkg` (`package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OTA 发布记录';

CREATE TABLE IF NOT EXISTS `ota_release_pool` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `release_id` BIGINT NOT NULL,
    `pool_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ota_rel_pool` (`release_id`, `pool_id`),
    KEY `idx_ota_rel_pool_pool` (`pool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发布与白名单池关联';

CREATE TABLE IF NOT EXISTS `ota_device_upgrade_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `release_id` BIGINT NOT NULL,
    `mac_address` VARCHAR(64) NOT NULL,
    `pkg_type` VARCHAR(16) NOT NULL COMMENT 'system|app',
    `from_version` VARCHAR(32) NULL,
    `to_version` VARCHAR(32) NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending|downloading|success|failed|skipped',
    `error_message` TEXT NULL,
    `reported_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_ota_upg_rel` (`release_id`, `status`),
    KEY `idx_ota_upg_mac` (`mac_address`, `reported_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备升级状态流水';

INSERT INTO `sys_params` (`id`, `param_code`, `param_value`, `value_type`, `param_type`, `remark`)
SELECT 801, 'devops.ota.service_token', '', 'string', 1, 'DevOps 硬件 OTA 服务令牌（Header X-DevOps-Token）'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` WHERE `id` = 801 OR `param_code` = 'devops.ota.service_token');
