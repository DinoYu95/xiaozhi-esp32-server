-- 多领域判别器 source 格式 SKILL:{domain}:{code} 超过原 VARCHAR(32)
ALTER TABLE `child_risk_event`
  MODIFY COLUMN `source` VARCHAR(128) NOT NULL COMMENT 'ZhibAN_JSON/RULE/SKILL:domain:code';
