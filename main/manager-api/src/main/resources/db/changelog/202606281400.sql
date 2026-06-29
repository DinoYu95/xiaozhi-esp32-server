-- 技能表：意图未匹配时的全局默认兜底 skill（智控台技能管理可改，全平台仅一个）
ALTER TABLE `ai_skill`
    ADD COLUMN `is_default_fallback` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否意图未匹配时的默认兜底技能：0否 1是' AFTER `is_official_recommended`;

-- 默认将「通用闲聊」设为兜底（若存在）
UPDATE `ai_skill` SET `is_default_fallback` = 0;
UPDATE `ai_skill` SET `is_default_fallback` = 1 WHERE `id` = 'skill_general_chat' LIMIT 1;
