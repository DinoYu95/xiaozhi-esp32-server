-- 孩子档案：省/教材代码（与教研 province_code、textbook_edition 对齐）
ALTER TABLE device_child
    ADD COLUMN province_code VARCHAR(32) DEFAULT 'CN' COMMENT '省/地区代码，与教研一致' AFTER school,
    ADD COLUMN textbook_edition VARCHAR(32) DEFAULT 'generic' COMMENT '教材版本代码 pep/generic 等' AFTER province_code;

-- 升学提醒配置：province_code 为空串表示全局默认（按学段）
CREATE TABLE learning_promotion_schedule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    province_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '空串=全局默认',
    school_level VARCHAR(16) NOT NULL COMMENT 'PRIMARY|MIDDLE|HIGH',
    promotion_month TINYINT NOT NULL COMMENT '1-12',
    promotion_day TINYINT NOT NULL COMMENT '1-31',
    remark VARCHAR(255) DEFAULT NULL,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_promo_province_level (province_code, school_level)
) COMMENT='升学日期配置（教研后台维护）';

INSERT INTO learning_promotion_schedule (province_code, school_level, promotion_month, promotion_day, remark)
VALUES
    ('', 'PRIMARY', 8, 31, '全局默认：小学升学日'),
    ('', 'MIDDLE', 8, 31, '全局默认：初中升学日'),
    ('', 'HIGH', 8, 31, '全局默认：高中升学日');

-- 家长端可拉取的档案提醒（升学更新年级等）
CREATE TABLE parent_profile_reminder (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    child_id BIGINT NOT NULL,
    reminder_type VARCHAR(32) NOT NULL COMMENT 'GRADE_PROMOTION',
    title VARCHAR(128) NOT NULL,
    body VARCHAR(512) DEFAULT NULL,
    action VARCHAR(64) DEFAULT 'OPEN_CHILD_PROFILE' COMMENT '小程序动作标识',
    remind_date DATE NOT NULL COMMENT '展示日（通常为升学日前一天）',
    promotion_date DATE NOT NULL COMMENT '目标升学日',
    dismissed_at DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_reminder_child_date (child_id, remind_date, dismissed_at)
) COMMENT='孩子档案相关提醒';
