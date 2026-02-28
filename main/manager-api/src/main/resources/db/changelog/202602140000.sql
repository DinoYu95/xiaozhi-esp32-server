-- 设备主孩子表（一设备一孩）
CREATE TABLE IF NOT EXISTS `device_child` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `device_id` VARCHAR(32) NOT NULL COMMENT '设备ID（= ai_device.id）',
    `name` VARCHAR(64) DEFAULT NULL COMMENT '孩子姓名/昵称',
    `avatar_url` VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    `birthday` DATE DEFAULT NULL COMMENT '生日',
    `gender` TINYINT DEFAULT 0 COMMENT '性别：0未知/1男/2女',
    `age_stage` VARCHAR(20) DEFAULT NULL COMMENT '年龄段，如3-6岁、学龄前',
    `hobbies` VARCHAR(500) DEFAULT NULL COMMENT '爱好',
    `favorite_topics` VARCHAR(500) DEFAULT NULL COMMENT '喜欢的话题',
    `favorite_stories` VARCHAR(500) DEFAULT NULL COMMENT '喜欢的故事/绘本',
    `personality_note` VARCHAR(500) DEFAULT NULL COMMENT '性格/偏好备注',
    `school` VARCHAR(128) DEFAULT NULL COMMENT '学校/幼儿园',
    `create_time` DATETIME DEFAULT NULL COMMENT '创建时间',
    `update_time` DATETIME DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_id` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备主孩子表';

-- 智能体声纹表增加 child_id，并加唯一约束（一孩一声纹）
ALTER TABLE `ai_agent_voice_print`
    ADD COLUMN `child_id` BIGINT NULL COMMENT '关联 device_child.id，非空表示主孩子声纹' AFTER `audio_id`;
ALTER TABLE `ai_agent_voice_print`
    ADD UNIQUE KEY `uk_agent_child` (`agent_id`, `child_id`);
