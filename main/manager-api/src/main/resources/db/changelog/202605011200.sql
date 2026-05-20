-- 儿童风险：多领域 Evaluator + 扩展全局配置默认值
CREATE TABLE IF NOT EXISTS `child_risk_evaluator` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `code` VARCHAR(64) NOT NULL COMMENT '稳定标识 psychological_v1',
  `name` VARCHAR(128) NOT NULL,
  `risk_domain` VARCHAR(32) NOT NULL,
  `version` INT NOT NULL DEFAULT 1,
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1启用',
  `model_name` VARCHAR(64) NULL COMMENT '空则用智伴侧默认',
  `temperature` DECIMAL(3,2) NOT NULL DEFAULT 0.00,
  `timeout_ms` INT NOT NULL DEFAULT 45000,
  `instructions` TEXT NOT NULL,
  `allowed_categories` VARCHAR(512) NOT NULL COMMENT 'JSON数组',
  `sort_order` INT NOT NULL DEFAULT 0,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  KEY `idx_domain_status` (`risk_domain`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='儿童风险领域判别器';
