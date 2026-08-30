-- 历史 ai_device.app_version 存的是固件版本，迁移到 system_version；app_version 改为应用 SWU（现网暂无值）
UPDATE `ai_device`
SET `system_version` = `app_version`,
    `app_version` = NULL
WHERE (`system_version` IS NULL OR `system_version` = '')
  AND `app_version` IS NOT NULL
  AND `app_version` <> '';
