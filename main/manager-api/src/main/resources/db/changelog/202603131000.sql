-- 家长端聊天音频表（家长语音消息的原始音频）
CREATE TABLE IF NOT EXISTS `parent_chat_audio` (
    `id` VARCHAR(32) NOT NULL COMMENT '主键ID' PRIMARY KEY,
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户ID',
    `audio` LONGBLOB NOT NULL COMMENT '音频数据',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    INDEX `idx_parent_chat_audio_parent` (`parent_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端聊天音频表';

-- 家长端聊天记录表
CREATE TABLE IF NOT EXISTS `parent_chat_history` (
    `id` BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户ID',
    `child_id` BIGINT NOT NULL COMMENT '孩子ID（device_child.id）',
    `device_id` VARCHAR(32) NOT NULL COMMENT '设备ID，用于关联 agent',
    `agent_id` VARCHAR(32) NOT NULL COMMENT '智能体ID',
    `session_id` VARCHAR(128) NOT NULL COMMENT '会话ID，格式 parent_{parentUserId}_{childId}',
    `chat_type` TINYINT(3) NOT NULL COMMENT '消息类型: 1-家长, 2-助手',
    `content` VARCHAR(2048) COMMENT '聊天内容（ASR 结果或用户输入）',
    `audio_id` VARCHAR(32) COMMENT '音频ID，非空表示语音消息',
    `create_time` DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    INDEX `idx_parent_chat_parent_child` (`parent_user_id`, `child_id`),
    INDEX `idx_parent_chat_session_created` (`session_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端聊天记录表';
