-- 影子任务：支持多任务排序；新增 completed 状态由孩子侧对话工具回写
ALTER TABLE `parent_shadow_mission`
    ADD COLUMN `priority` INT NOT NULL DEFAULT 0 COMMENT '越小越优先，新建递增' AFTER `status`;
