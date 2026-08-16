-- 孩子档案 + 已发布图谱：增加市、上下册（与教研 tr_project 对齐）
ALTER TABLE device_child
    ADD COLUMN city_code VARCHAR(32) DEFAULT NULL COMMENT '地市编码，如 shandong_jinan' AFTER province_code,
    ADD COLUMN semester VARCHAR(16) DEFAULT 'upper' COMMENT 'upper=上册 lower=下册' AFTER city_code;

ALTER TABLE kg_graph_release
    ADD COLUMN city_code VARCHAR(32) NOT NULL DEFAULT 'all' COMMENT '地市编码，all=不限/旧数据' AFTER province_code,
    ADD COLUMN semester VARCHAR(16) NOT NULL DEFAULT 'all' COMMENT 'all=不限/旧数据 upper lower' AFTER city_code;

DROP INDEX idx_kg_release_dim ON kg_graph_release;
CREATE INDEX idx_kg_release_dim ON kg_graph_release (
    subject,
    province_code,
    city_code,
    semester,
    textbook_edition,
    grade_min,
    grade_max,
    status
);
