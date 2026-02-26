-- 家长端用户表（与 sys_user 隔离）
CREATE TABLE IF NOT EXISTS `parent_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `open_id` VARCHAR(64) DEFAULT NULL COMMENT '微信 open_id',
    `union_id` VARCHAR(64) DEFAULT NULL COMMENT '微信 union_id',
    `phone` VARCHAR(256) DEFAULT NULL COMMENT '手机号（加密存储）',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    `channel` VARCHAR(32) DEFAULT NULL COMMENT '渠道：wechat/mini_program/app',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_open_id_channel` (`open_id`, `channel`),
    KEY `idx_phone` (`phone`(64))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端用户';

-- 家长端登录 token 表
CREATE TABLE IF NOT EXISTS `parent_user_token` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户 id',
    `token` VARCHAR(128) NOT NULL COMMENT 'token',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `channel` VARCHAR(32) DEFAULT NULL COMMENT '渠道',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token` (`token`),
    KEY `idx_parent_user_id` (`parent_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端登录 token';

-- 家长-设备绑定表
CREATE TABLE IF NOT EXISTS `parent_device_binding` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户 id',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备标识（mac）',
    `bind_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    `bind_source` VARCHAR(32) DEFAULT 'code' COMMENT '绑定来源：code/qrcode',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_device` (`parent_user_id`, `device_id`),
    KEY `idx_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长-设备绑定';
