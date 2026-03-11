-- 家长端自定义技能表（与管理员在后台添加的 ai_skill 区分）
CREATE TABLE IF NOT EXISTS `parent_user_skill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户ID',
    `name` VARCHAR(128) NOT NULL COMMENT '技能名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '简短用途说明',
    `instructions` TEXT NOT NULL COMMENT '技能说明/系统级提示',
    `version` VARCHAR(32) DEFAULT '1.0' COMMENT '版本号',
    `tools` VARCHAR(1024) DEFAULT NULL COMMENT '工具 id 列表，JSON 数组字符串',
    `metadata` VARCHAR(2048) DEFAULT NULL COMMENT '扩展字段，JSON 对象字符串',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent_user_id` (`parent_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端自定义技能表';
