-- 设备最近一次上报的电量、WiFi（离线后仍保留，供家长端列表展示）
ALTER TABLE `ai_device`
    ADD COLUMN `battery_level` TINYINT UNSIGNED NULL DEFAULT NULL COMMENT '最近一次上报电量 0-100' AFTER `last_connected_at`,
    ADD COLUMN `wifi_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '最近一次上报 WiFi 名称' AFTER `battery_level`,
    ADD COLUMN `telemetry_updated_at` DATETIME NULL DEFAULT NULL COMMENT '电量/WiFi 最近上报时间' AFTER `wifi_name`;
