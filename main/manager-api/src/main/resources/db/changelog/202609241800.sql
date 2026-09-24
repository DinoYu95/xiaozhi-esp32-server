-- 内测保密协议：文档版本 + 用户同意记录 + 默认参数
CREATE TABLE IF NOT EXISTS `parent_beta_conf_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `version` VARCHAR(32) NOT NULL COMMENT '版本号，如 20260924_v1',
    `title` VARCHAR(200) NOT NULL COMMENT '协议标题',
    `summary` VARCHAR(500) NOT NULL COMMENT '勾选旁摘要',
    `content` MEDIUMTEXT NOT NULL COMMENT 'Markdown 正文',
    `status` VARCHAR(16) NOT NULL DEFAULT 'published' COMMENT 'draft/published/archived',
    `published_at` DATETIME NULL COMMENT '发布时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_beta_conf_document_version` (`version`),
    KEY `idx_parent_beta_conf_document_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端内测保密协议版本';

CREATE TABLE IF NOT EXISTS `parent_beta_conf_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `parent_user_id` BIGINT NOT NULL COMMENT '家长用户 ID',
    `version` VARCHAR(32) NOT NULL COMMENT '同意的协议版本',
    `agreed_at` DATETIME NOT NULL COMMENT '同意时间',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'wechat_miniprogram' COMMENT '渠道',
    `client_ip` VARCHAR(64) NULL,
    `user_agent` VARCHAR(512) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_beta_conf_record_user_version` (`parent_user_id`, `version`),
    KEY `idx_parent_beta_conf_record_user_time` (`parent_user_id`, `agreed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端内测保密协议同意记录';

INSERT INTO `parent_beta_conf_document` (`version`, `title`, `summary`, `content`, `status`, `published_at`)
SELECT '20260924_v1',
       '小智内测样机保密与合规使用承诺',
       '您使用的设备与小程序功能处于内测阶段。请阅读全文并承诺：不在社交平台公开内测功能、样机外观与未发布体验；不传播内测截图、视频或对话内容。',
       CONCAT(
           '更新日期：2026-09-24\n',
           '版本号：20260924_v1\n\n',
           '感谢您参与「小智」儿童智能设备与家长端小程序的内测（以下简称「内测」）。为保障产品安全、用户隐私与商业机密，请您在继续使用前阅读并签署本承诺。\n\n',
           '## 一、内测范围\n\n',
           '- 内测样机硬件、固件与云端能力可能不稳定，功能以实际交付为准。\n',
           '- 小程序部分能力为内测专享，可能调整或下线，请勿对外宣传为已上市功能。\n\n',
           '## 二、保密义务（核心）\n\n',
           '您承诺在内测期间及内测结束后【12】个月内（或运营方另行通知的期限）：\n\n',
           '1. **不在**微博、小红书、抖音、微信朋友圈公开渠道等**社交平台**发布内测样机照片、视频、开箱、测评或功能演示；\n',
           '2. **不泄露**内测版界面、对话记录、技术细节、未公开功能名称与路线图；\n',
           '3. **不将**内测账号、设备、邀请资格转借或售予未授权人员；\n',
           '4. 仅在与您同家庭监护场景下，由您本人或已授权家庭成员使用。\n\n',
           '## 三、例外\n\n',
           '- 经运营方**书面授权**的宣传或反馈活动除外；\n',
           '- 向官方「内测反馈」渠道提交问题与建议不受本条限制。\n\n',
           '## 四、违约与退出\n\n',
           '- 若违反保密义务，运营方有权暂停或终止内测资格、收回样机并追究法律责任（如适用）。\n',
           '- 您可随时停止使用并解绑设备；已公开内容请配合删除或澄清。\n\n',
           '## 五、协议更新\n\n',
           '我们可能修订本承诺；新版本发布后，您需重新阅读并同意方可继续使用内测服务。\n\n',
           '**勾选即表示：** 您已阅读并同意遵守上述保密与合规使用要求。'
       ),
       'published',
       NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `parent_beta_conf_document` WHERE `version` = '20260924_v1');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.beta_conf.enabled',
       'false',
       'boolean',
       1,
       '内测保密协议总开关。true 时未签署当前版本将拦截 parent-api（不影响设备对话，除非另行配置）。'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.beta_conf.enabled');
