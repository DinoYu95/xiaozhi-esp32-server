-- 设备家庭共享：成员在本设备上的家庭角色备注（爸爸/妈妈/爷爷…）
ALTER TABLE `parent_device_binding`
    ADD COLUMN `family_role` VARCHAR(32) NULL DEFAULT NULL COMMENT '家庭角色：father|mother|paternal_grandfather|paternal_grandmother|maternal_grandfather|maternal_grandmother|other' AFTER `receive_risk_notify`;
