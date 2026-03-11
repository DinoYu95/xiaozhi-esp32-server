-- ai_skill 增加「是否官方推荐」标识，家长端展示推荐技能
ALTER TABLE `ai_skill` ADD COLUMN `is_official_recommended` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否官方推荐：0否 1是' AFTER `metadata`;
