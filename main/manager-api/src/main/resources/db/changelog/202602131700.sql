-- 一说话人对应多技能：同一 (agent_id, speaker_type) 可对应多行不同 skill_id，由意图决定走哪个 skill
-- 去掉 (agent_id, speaker_type) 唯一约束，改为 (agent_id, speaker_type, skill_id) 唯一
ALTER TABLE `ai_agent_skill_mapping` DROP INDEX `uk_agent_speaker`;
ALTER TABLE `ai_agent_skill_mapping` ADD UNIQUE KEY `uk_agent_speaker_skill` (`agent_id`, `speaker_type`, `skill_id`);
