-- P1 智伴学习系统：知识图谱 + 作业 session + 证据 + 儿童掌握度

ALTER TABLE device_child
    ADD COLUMN current_grade TINYINT NULL COMMENT '小学年级1-6' AFTER school,
    ADD COLUMN textbook_series VARCHAR(64) NULL COMMENT '教材系列可选' AFTER current_grade,
    ADD COLUMN subjects_enabled VARCHAR(256) NULL COMMENT 'JSON数组如["math"]' AFTER textbook_series;

ALTER TABLE parent_shadow_mission
    ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'parent' COMMENT 'parent|learning' AFTER status,
    ADD COLUMN learning_session_id BIGINT NULL COMMENT 'learning_homework_session.id' AFTER source,
    ADD COLUMN skill_code VARCHAR(128) NULL AFTER learning_session_id;

CREATE TABLE kg_graph_release (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version_label VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'draft' COMMENT 'draft|published|archived',
    subject VARCHAR(16) NOT NULL DEFAULT 'math',
    grade_min TINYINT NOT NULL DEFAULT 1,
    grade_max TINYINT NOT NULL DEFAULT 3,
    published_at DATETIME NULL,
    checksum VARCHAR(64) NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    KEY idx_kg_release_status (status, subject)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识图谱发布版本';

CREATE TABLE kg_node (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    node_type VARCHAR(32) NOT NULL,
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_kg_node_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱节点稳定身份';

CREATE TABLE kg_node_revision (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    graph_release_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    name VARCHAR(256) NOT NULL,
    description TEXT NULL,
    grade TINYINT NULL,
    properties JSON NULL,
    UNIQUE KEY uk_kg_rev (graph_release_id, node_id),
    KEY idx_kg_rev_release (graph_release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='某版本下节点文案';

CREATE TABLE kg_edge (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    graph_release_id BIGINT NOT NULL,
    from_node_id BIGINT NOT NULL,
    to_node_id BIGINT NOT NULL,
    edge_type VARCHAR(32) NOT NULL,
    required TINYINT(1) NOT NULL DEFAULT 1,
    strength DECIMAL(4,2) NULL,
    properties JSON NULL,
    KEY idx_kg_edge_from (graph_release_id, edge_type, from_node_id),
    KEY idx_kg_edge_to (graph_release_id, edge_type, to_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图谱直接关系';

CREATE TABLE kg_closure (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    graph_release_id BIGINT NOT NULL,
    relation_type VARCHAR(32) NOT NULL,
    ancestor_node_id BIGINT NOT NULL,
    descendant_node_id BIGINT NOT NULL,
    min_depth INT NOT NULL,
    UNIQUE KEY uk_kg_closure (graph_release_id, relation_type, ancestor_node_id, descendant_node_id),
    KEY idx_kg_closure_desc (graph_release_id, relation_type, descendant_node_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多级关系预计算';

CREATE TABLE learning_homework_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_uuid CHAR(36) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    child_id BIGINT NOT NULL,
    graph_release_id BIGINT NULL,
    started_at DATETIME NOT NULL,
    ended_at DATETIME NULL,
    end_reason VARCHAR(32) NULL,
    observation_level VARCHAR(16) NULL,
    user_turn_count INT NOT NULL DEFAULT 0,
    photo_count INT NOT NULL DEFAULT 0,
    longest_silence_sec INT NOT NULL DEFAULT 0,
    summary_json JSON NULL,
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_learning_session_uuid (session_uuid),
    KEY idx_learning_session_child (child_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='作业辅导会话';

CREATE TABLE learning_evidence_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    child_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at DATETIME NOT NULL,
    payload JSON NULL,
    skill_codes JSON NULL,
    misconception_codes JSON NULL,
    confidence DECIMAL(4,2) NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_learning_evidence_idem (idempotency_key),
    KEY idx_learning_evidence_session (session_id),
    KEY idx_learning_evidence_child (child_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习证据流水';

CREATE TABLE learner_skill_state (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    child_id BIGINT NOT NULL,
    skill_node_id BIGINT NOT NULL,
    graph_release_id BIGINT NULL,
    evidence_stage VARCHAR(32) NOT NULL DEFAULT 'SCAFFOLDED',
    p_mastery DECIMAL(4,2) NOT NULL DEFAULT 0.50,
    evidence_count INT NOT NULL DEFAULT 0,
    last_evidence_at DATETIME NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_learner_skill (child_id, skill_node_id),
    KEY idx_learner_skill_child (child_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='儿童知识点掌握度';
