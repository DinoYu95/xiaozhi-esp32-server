-- 长短期记忆（短期本地摘要 + 长期 Mem0）模型供应器与配置，供智控台「记忆模式」下拉选择
DELETE FROM `ai_model_provider` WHERE `id` = 'SYSTEM_Memory_short_long_memory';
INSERT INTO `ai_model_provider` (`id`, `model_type`, `provider_code`, `name`, `fields`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
('SYSTEM_Memory_short_long_memory', 'Memory', 'short_long_memory', '长短期记忆', '[{"key":"llm","label":"LLM模型","type":"string"},{"key":"api_key","label":"Mem0 API密钥(可选)","type":"string"},{"key":"base_url","label":"Mem0 自建地址(可选)","type":"string"}]', 4, 1, NOW(), 1, NOW());

DELETE FROM `ai_model_config` WHERE `id` = 'Memory_short_long_memory';
INSERT INTO `ai_model_config` (`id`, `model_type`, `model_code`, `model_name`, `is_default`, `is_enabled`, `config_json`, `doc_link`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
('Memory_short_long_memory', 'Memory', 'short_long_memory', '长短期记忆', 0, 1, '{\"type\": \"short_long_memory\", \"llm\": \"LLM_ChatGLMLLM\"}', NULL, '短期：本地 LLM 摘要；长期：Mem0。可选填 api_key 或 base_url 启用长期记忆。', 4, 1, NOW(), 1, NOW());
