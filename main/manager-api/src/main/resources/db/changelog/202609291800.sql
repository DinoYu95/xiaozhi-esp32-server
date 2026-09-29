-- 小程序审核合规：公开配置参数 + 内测登录白名单
CREATE TABLE IF NOT EXISTS `parent_beta_allowlist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `wechat_openid` VARCHAR(64) NOT NULL COMMENT '微信小程序 openid',
    `channel` VARCHAR(32) NOT NULL DEFAULT 'mini_program' COMMENT '登录渠道',
    `phone` VARCHAR(32) NULL COMMENT '运营对照手机号',
    `source` VARCHAR(32) NOT NULL DEFAULT 'manual' COMMENT 'manual/device_bind/invite',
    `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '1=生效',
    `reviewer_test` TINYINT NOT NULL DEFAULT 0 COMMENT '1=审核测试账号（reviewMode 下放行）',
    `remark` VARCHAR(255) NULL,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_parent_beta_allowlist_openid_channel` (`wechat_openid`, `channel`),
    KEY `idx_parent_beta_allowlist_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家长端小程序登录内测白名单';

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.app.access_mode',
       'internal_beta',
       'string',
       1,
       '小程序访问模式：open=登录即可用；internal_beta=须命中登录白名单或已绑定设备等策略'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.app.access_mode');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.app.review_mode',
       'false',
       'boolean',
       1,
       '审核模式：true 时 reviewer_test=1 的白名单 openid 登录永远放行'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.app.review_mode');

INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'parent.app.public_config',
       '{"appName":"智伴家语","homeBanner":{"title":"智伴家语 · 家长端（内测）","subtitle":"当前面向智伴内测家庭开放。您可先浏览功能介绍；使用设备绑定、与孩子对话等服务需登录并完成账号验证。","tags":["内测","家长端"]},"loginHint":"登录使用微信身份，不强制授权手机号。非内测账号可能无法使用完整功能。","guestTabHints":{"companion":"登录并绑定设备后，可在此与机器人沟通、查看简报","agents":"登录后可管理并下发对话技能","robot":"登录后可绑定设备、管理孩子档案","learning":"登录后查看学习进展","mine":"登录后管理账号与家庭共享"}}',
       'string',
       1,
       'GET /parent-api/app/public-config 文案 JSON（不含 accessMode/reviewMode，见独立参数）'
WHERE NOT EXISTS (SELECT 1 FROM `sys_params` s WHERE s.param_code = 'parent.app.public_config');
