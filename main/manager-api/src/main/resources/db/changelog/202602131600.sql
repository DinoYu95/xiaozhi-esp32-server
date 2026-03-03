-- 多角色与智伴 Agent 适配：技能表 + 智能体说话人类型→技能映射表
-- ai_skill: 技能定义（固定格式，类 Claude Skill）
CREATE TABLE IF NOT EXISTS `ai_skill` (
    `id` VARCHAR(64) NOT NULL COMMENT '技能唯一标识，如 skill_children_chat',
    `name` VARCHAR(128) NOT NULL COMMENT '展示名称',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '简短用途说明',
    `instructions` TEXT NOT NULL COMMENT '技能说明/系统级提示',
    `version` VARCHAR(32) DEFAULT '1.0' COMMENT '版本号',
    `tools` JSON DEFAULT NULL COMMENT '该技能可用的工具 id 列表',
    `metadata` JSON DEFAULT NULL COMMENT '扩展字段',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能定义表';

-- ai_agent_skill_mapping: 智能体维度「说话人类型 → 技能」映射
CREATE TABLE IF NOT EXISTS `ai_agent_skill_mapping` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `agent_id` VARCHAR(32) NOT NULL COMMENT '智能体ID',
    `speaker_type` VARCHAR(32) NOT NULL COMMENT '说话人类型: owner_child/parent/other_child/other_adult/unknown',
    `skill_id` VARCHAR(64) NOT NULL COMMENT '技能ID',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_speaker` (`agent_id`, `speaker_type`),
    KEY `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体说话人类型→技能映射表';
