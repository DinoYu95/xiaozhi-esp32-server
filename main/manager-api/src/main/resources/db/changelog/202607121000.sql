-- 设备成员是否接收儿童风险提示通知（Owner 始终接收，Member 由 Owner 配置）
ALTER TABLE `parent_device_binding`
    ADD COLUMN `receive_risk_notify` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否接收该设备风险提示：owner 恒为1' AFTER `status`;

UPDATE `parent_device_binding`
SET `receive_risk_notify` = 1
WHERE LOWER(`role`) = 'owner' AND `status` = 'active';
