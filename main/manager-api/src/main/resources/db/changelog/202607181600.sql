-- 家长端聊天：远程看娃快照（OSS objectKey + 请求追踪）
ALTER TABLE `parent_chat_history`
    ADD COLUMN `image_object_key` VARCHAR(512) NULL COMMENT '聊天图片 OSS objectKey（远程看娃等）' AFTER `audio_id`,
    ADD COLUMN `message_kind` VARCHAR(32) NULL DEFAULT 'text' COMMENT 'text/snapshot/text_with_snapshot' AFTER `image_object_key`,
    ADD COLUMN `snapshot_request_id` VARCHAR(64) NULL COMMENT '远程看娃请求 id' AFTER `message_kind`;

CREATE INDEX `idx_parent_chat_snapshot_request` ON `parent_chat_history` (`snapshot_request_id`);
