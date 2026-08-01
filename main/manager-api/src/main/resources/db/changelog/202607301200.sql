-- 家长端远程实时监控会话（腾讯云直播 RTMP 推流）
CREATE TABLE IF NOT EXISTS `parent_live_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `session_no` VARCHAR(64) NOT NULL COMMENT '对外会话号 live_xxx',
    `parent_user_id` BIGINT NOT NULL COMMENT '发起家长',
    `device_id` VARCHAR(64) NOT NULL COMMENT '设备 ID',
    `child_id` BIGINT NULL COMMENT '可选孩子 ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'starting' COMMENT 'starting/live/stopping/stopped/failed',
    `stream_app` VARCHAR(64) NOT NULL DEFAULT 'parent' COMMENT '腾讯云 AppName',
    `stream_name` VARCHAR(128) NOT NULL COMMENT '腾讯云 StreamName',
    `push_url` VARCHAR(1024) NOT NULL COMMENT '完整 RTMP 推流地址',
    `play_url_flv` VARCHAR(1024) NULL COMMENT 'FLV 播放地址',
    `play_url_hls` VARCHAR(1024) NULL COMMENT 'HLS 播放地址',
    `push_expire_at` DATETIME NULL COMMENT '推流鉴权过期时间',
    `client_id` VARCHAR(128) NULL COMMENT 'MQTT clientId',
    `fail_code` VARCHAR(64) NULL COMMENT '失败码',
    `fail_message` VARCHAR(512) NULL COMMENT '失败说明',
    `started_at` DATETIME NULL COMMENT '推流成功时间',
    `stopped_at` DATETIME NULL COMMENT '结束时间',
    `stop_reason` VARCHAR(64) NULL COMMENT 'user/heartbeat_timeout/push_timeout/stream_end/admin',
    `last_heartbeat_at` DATETIME NULL COMMENT '最近心跳',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_live_session_no` (`session_no`),
    KEY `idx_parent_live_device_status` (`device_id`, `status`),
    KEY `idx_parent_live_parent` (`parent_user_id`, `create_time`),
    KEY `idx_parent_live_stream_name` (`stream_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长远程实时监控会话';

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.enabled',
       'false',
       'boolean',
       1,
       '家长远程实时监控总开关。true 时小程序可发起 live；需配置 parent.live.tencent.*。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.enabled');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.max_duration_sec',
       '600',
       'number',
       1,
       '单次远程查看最长时间（秒），默认 10 分钟。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.max_duration_sec');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.heartbeat_interval_sec',
       '20',
       'number',
       1,
       '小程序心跳间隔（秒）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.heartbeat_interval_sec');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.heartbeat_timeout_sec',
       '60',
       'number',
       1,
       '心跳超时自动停止（秒）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.heartbeat_timeout_sec');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.push_timeout_sec',
       '30',
       'number',
       1,
       'starting 阶段等待腾讯云推流成功超时（秒）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.push_timeout_sec');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.tencent.push_domain',
       '',
       'string',
       1,
       '腾讯云 RTMP 推流域名（不含 rtmp://）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.tencent.push_domain');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.tencent.play_domain',
       '',
       'string',
       1,
       '腾讯云播放域名（FLV/HLS，不含 https://）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.tencent.play_domain');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.tencent.app_name',
       'parent',
       'string',
       1,
       '腾讯云直播 AppName，默认 parent。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.tencent.app_name');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.tencent.push_auth_key',
       '',
       'string',
       1,
       '腾讯云推流鉴权 Key（txSecret）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.tencent.push_auth_key');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.live.tencent.push_expire_buffer_sec',
       '300',
       'number',
       1,
       '推流 URL 过期时间 = max_duration + buffer（秒）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.live.tencent.push_expire_buffer_sec');
