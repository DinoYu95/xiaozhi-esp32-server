-- 若 202608162000 因索引重建失败而未写入 device_child 字段，本脚本补列（幂等）
-- liquibase formatted sql

-- changeset xiaozhi:202608171000-device-child-city
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_child' AND COLUMN_NAME = 'city_code'
ALTER TABLE device_child
    ADD COLUMN city_code VARCHAR(32) DEFAULT NULL COMMENT '地市编码，如 shandong_jinan' AFTER province_code;

-- changeset xiaozhi:202608171000-device-child-semester
-- preconditions onFail:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'device_child' AND COLUMN_NAME = 'semester'
ALTER TABLE device_child
    ADD COLUMN semester VARCHAR(16) DEFAULT 'upper' COMMENT 'upper=上册 lower=下册' AFTER city_code;
