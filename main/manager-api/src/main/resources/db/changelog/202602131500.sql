-- 家长端登录身份表：一种登录方式一条记录，关联 parent_user（一人多登录方式）
CREATE TABLE IF NOT EXISTS `parent_auth` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户 id',
    `auth_type` VARCHAR(16) NOT NULL COMMENT '认证类型：wechat/phone',
    `channel` VARCHAR(32) NOT NULL COMMENT '渠道：wechat/mini_program/app',
    `open_id` VARCHAR(64) DEFAULT NULL COMMENT '微信 open_id（auth_type=wechat 时必填）',
    `union_id` VARCHAR(64) DEFAULT NULL COMMENT '微信 union_id',
    `phone` VARCHAR(256) DEFAULT NULL COMMENT '手机号加密（auth_type=phone 时必填）',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_channel_open_id` (`channel`, `open_id`),
    UNIQUE KEY `uk_channel_phone` (`channel`, `phone`(64)),
    KEY `idx_parent_user_id` (`parent_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端登录身份（多方式关联同一用户）';

-- 将现有 parent_user 的微信登录身份迁移到 parent_auth
INSERT INTO `parent_auth` (`parent_user_id`, `auth_type`, `channel`, `open_id`, `union_id`, `phone`, `create_time`, `update_time`)
SELECT `id`, 'wechat', COALESCE(`channel`, 'mini_program'), `open_id`, `union_id`, NULL, `create_time`, NOW()
FROM `parent_user`
WHERE `open_id` IS NOT NULL AND TRIM(`open_id`) != '';

-- 将现有 parent_user 的手机登录身份迁移到 parent_auth（同一用户可能先有微信后有手机，故用 INSERT）
INSERT INTO `parent_auth` (`parent_user_id`, `auth_type`, `channel`, `open_id`, `union_id`, `phone`, `create_time`, `update_time`)
SELECT `id`, 'phone', COALESCE(`channel`, 'app'), NULL, NULL, `phone`, `create_time`, NOW()
FROM `parent_user`
WHERE `phone` IS NOT NULL AND TRIM(`phone`) != '';

-- 删除 parent_user 上的登录标识列，仅保留资料（昵称、头像等）
ALTER TABLE `parent_user`
    DROP INDEX `uk_open_id_channel`,
    DROP INDEX `idx_phone`,
    DROP COLUMN `open_id`,
    DROP COLUMN `union_id`,
    DROP COLUMN `phone`,
    DROP COLUMN `channel`;
