-- 儿童隐私协议：文档版本 + 用户同意记录 + 默认参数
CREATE TABLE IF NOT EXISTS `parent_consent_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version` VARCHAR(32) NOT NULL COMMENT '版本号，如 20260712_v1',
    `title` VARCHAR(200) NOT NULL COMMENT '协议标题',
    `summary` VARCHAR(500) NOT NULL COMMENT '勾选旁摘要',
    `content` MEDIUMTEXT NOT NULL COMMENT 'Markdown 正文',
    `status` VARCHAR(16) NOT NULL DEFAULT 'published' COMMENT 'draft/published/archived',
    `published_at` DATETIME NULL COMMENT '发布时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_consent_document_version` (`version`),
    KEY `idx_parent_consent_document_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端儿童隐私协议版本';

CREATE TABLE IF NOT EXISTS `parent_consent_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户 ID',
    `version` VARCHAR(32) NOT NULL COMMENT '同意的协议版本',
    `agreed_at` DATETIME NOT NULL COMMENT '同意时间',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'wechat_miniprogram' COMMENT '渠道',
    `client_ip` VARCHAR(64) NULL,
    `user_agent` VARCHAR(512) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_consent_record_user_version` (`parent_user_id`, `version`),
    KEY `idx_parent_consent_record_user_time` (`parent_user_id`, `agreed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端协议同意记录';

INSERT INTO `parent_consent_document` (`version`, `title`, `summary`, `content`, `status`, `published_at`)
SELECT '20260712_v1',
       '小智儿童智能设备用户服务与儿童个人信息保护说明',
       '为保障儿童安全使用本设备与小程序，我们将收集设备标识、语音对话、声纹特征等信息用于陪伴对话、家长监护与风险提醒。请您阅读全文后自愿同意；不同意将无法绑定或使用设备。',
       CONCAT(
           '更新日期：2026-07-12\n',
           '版本号：20260712_v1\n\n',
           '欢迎使用小智儿童智能陪伴设备及家长端小程序（以下统称「本服务」）。本说明适用于您为未成年人配置、绑定和使用本服务的全部场景。\n\n',
           '## 一、我们收集哪些信息\n\n',
           '账户信息、设备信息、儿童档案、声纹信息、语音与对话、风险信号及使用日志等，用于陪伴对话、家长监护、风险提醒与产品改进。\n\n',
           '## 二、我们如何使用\n\n',
           '仅用于本说明所列功能；我们不出售儿童个人信息；第三方服务商仅接收实现功能所必需的数据。\n\n',
           '## 三、存储与保留\n\n',
           '数据存储于中华人民共和国境内；对话与日志默认保留 180 天，到期删除或匿名化。\n\n',
           '## 四、您的权利\n\n',
           '您作为监护人可以查询、更正孩子档案与声纹，查看对话记录，解绑设备或注销账户。撤回同意后我们将停止收集新数据，设备将无法继续对话直至重新同意。\n\n',
           '## 五、儿童使用须知\n\n',
           '本服务需监护人绑定并同意；请指导儿童安全使用，避免透露住址、学校、联系方式等敏感信息。\n\n',
           '## 六、协议更新\n\n',
           '我们可能修订本说明，重大变更将在小程序内提示，需重新同意后继续使用。\n\n',
           '**勾选即表示：** 您是儿童的监护人或已取得监护人授权，已阅读并同意本说明全文。'
       ),
       'published',
       NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `parent_consent_document` WHERE `version` = '20260712_v1');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.consent.enabled',
       'true',
       'boolean',
       1,
       '儿童隐私协议总开关。false 时不拦截小程序与设备。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.consent.enabled');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.consent.device_block_mode',
       'owner_only',
       'string',
       1,
       '设备对话阻断模式：owner_only=仅 Primary Owner 需同意；all_members=所有 active 成员均需同意。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.consent.device_block_mode');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'consent_blocked.prompt',
       '请先由主账号家长在小程序中阅读并同意儿童隐私保护说明，同意后设备即可正常使用。',
       'string',
       1,
       '主账号未同意协议时设备 TTS 播报文案（xiaozhi-server 通过 getConfig 拉取）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'consent_blocked.prompt');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.consent.retention_days_display',
       '180',
       'number',
       1,
       '协议正文中展示的数据保留天数（展示用）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.consent.retention_days_display');
