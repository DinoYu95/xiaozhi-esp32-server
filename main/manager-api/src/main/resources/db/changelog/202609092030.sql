-- 家长成员声纹：与 parent_user 绑定，一家长在该 agent 下至多一条（child_id 为空）
ALTER TABLE `ai_agent_voice_print`
    ADD COLUMN `parent_user_id` BIGINT NULL DEFAULT NULL COMMENT '家长成员声纹归属 parent_user.id；与 child_id 互斥' AFTER `child_id`;

ALTER TABLE `ai_agent_voice_print`
    ADD UNIQUE KEY `uk_agent_parent_user` (`agent_id`, `parent_user_id`);
