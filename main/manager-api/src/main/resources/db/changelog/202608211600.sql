-- 成长星图：模板发版 + 儿童证据/状态/通知

CREATE TABLE IF NOT EXISTS gp_template_release (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    age_band VARCHAR(16) NOT NULL COMMENT 'preschool|lower|upper|middle',
    version_label VARCHAR(128) NOT NULL,
    teaching_submission_id BIGINT NULL,
    rules_json JSON NULL COMMENT '点亮/视觉/通知规则',
    status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|published|archived',
    published_at DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gp_release_age_status (age_band, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长星图模板发版';

CREATE TABLE IF NOT EXISTS gp_template_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    release_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    node_type VARCHAR(16) NOT NULL COMMENT 'hub|sub|signal',
    parent_code VARCHAR(128) NULL,
    label VARCHAR(128) NOT NULL,
    short_label VARCHAR(64) NULL,
    short_desc VARCHAR(256) NULL,
    cluster_code VARCHAR(32) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    required_evidence INT NOT NULL DEFAULT 3,
    visible_threshold INT NOT NULL DEFAULT 52,
    strong_threshold INT NOT NULL DEFAULT 72,
    match_hints JSON NULL COMMENT '预置 signal 匹配关键词',
    properties_json JSON NULL,
    KEY idx_gp_node_release (release_id),
    UNIQUE KEY uk_gp_node_release_code (release_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gp_template_edge (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    release_id BIGINT NOT NULL,
    from_code VARCHAR(128) NOT NULL,
    to_code VARCHAR(128) NOT NULL,
    edge_type VARCHAR(32) NOT NULL DEFAULT 'CONTAINS',
    KEY idx_gp_edge_release (release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learner_growth_evidence (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    child_id BIGINT NOT NULL,
    release_id BIGINT NOT NULL,
    node_code VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'conversation' COMMENT 'conversation|task|manual',
    source_ref VARCHAR(128) NULL,
    confidence INT NOT NULL DEFAULT 70 COMMENT '0-100',
    snippet VARCHAR(512) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_lge_child_release (child_id, release_id),
    KEY idx_lge_node (node_code, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS learner_growth_state (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    child_id BIGINT NOT NULL,
    release_id BIGINT NOT NULL,
    node_code VARCHAR(128) NOT NULL,
    evidence_count INT NOT NULL DEFAULT 0,
    strength INT NOT NULL DEFAULT 0 COMMENT '0-100',
    state VARCHAR(16) NOT NULL DEFAULT 'locked' COMMENT 'locked|collecting|visible|strong',
    visual_intensity DECIMAL(6,4) NOT NULL DEFAULT 0,
    visual_tier VARCHAR(8) NOT NULL DEFAULT 'none' COMMENT 'none|low|mid|high',
    first_strong_at DATETIME NULL,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_lgs_child_node (child_id, node_code),
    KEY idx_lgs_child_release (child_id, release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS gp_parent_notification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_user_id BIGINT NOT NULL,
    child_id BIGINT NOT NULL,
    node_code VARCHAR(128) NOT NULL,
    notify_type VARCHAR(16) NOT NULL COMMENT 'instant|weekly',
    title VARCHAR(128) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_gpn_parent_child (parent_user_id, child_id, is_read),
    KEY idx_gpn_child_week (child_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成长星图家长通知';

CREATE TABLE IF NOT EXISTS gp_parent_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    parent_user_id BIGINT NOT NULL,
    child_id BIGINT NOT NULL,
    instant_notify_enabled TINYINT(1) NOT NULL DEFAULT 1,
    weekly_digest_enabled TINYINT(1) NOT NULL DEFAULT 1,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_gps_parent_child (parent_user_id, child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
