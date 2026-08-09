-- 教研发布：按 省+学科+教材+单年级 维度区分图谱版本
ALTER TABLE kg_graph_release
    ADD COLUMN province_code VARCHAR(16) NOT NULL DEFAULT 'CN' COMMENT '省编码，CN=全国通用' AFTER subject,
    ADD COLUMN textbook_edition VARCHAR(32) NOT NULL DEFAULT 'generic' COMMENT '教材版本' AFTER province_code;

CREATE INDEX idx_kg_release_dim ON kg_graph_release (subject, province_code, textbook_edition, grade_min, grade_max, status);
