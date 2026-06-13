-- Boson Higgs Audio v3 流式 TTS

DELETE FROM `ai_model_provider` WHERE id = 'SYSTEM_TTS_boson_stream';
INSERT INTO `ai_model_provider`
(`id`, `model_type`, `provider_code`, `name`, `fields`, `sort`, `creator`, `create_date`, `updater`, `update_date`)
VALUES
('SYSTEM_TTS_boson_stream', 'TTS', 'boson_stream', 'Boson Higgs Audio v3',
 '[{"key":"api_key","label":"API Key","type":"password"},{"key":"api_url","label":"API地址","type":"string"},{"key":"model","label":"模型","type":"string"},{"key":"voice","label":"默认音色","type":"string"},{"key":"output_dir","label":"输出目录","type":"string"},{"key":"request_timeout","label":"请求超时(秒)","type":"number"}]',
 20, 1, NOW(), 1, NOW());

DELETE FROM `ai_model_config` WHERE id = 'TTS_BosonHiggsTTS';
INSERT INTO `ai_model_config`
(`id`, `model_type`, `model_code`, `model_name`, `is_default`, `is_enabled`, `config_json`, `doc_link`, `remark`, `sort`, `creator`, `create_date`, `updater`, `update_date`)
VALUES
('TTS_BosonHiggsTTS', 'TTS', 'BosonHiggsTTS', 'Boson Higgs Audio v3', 0, 1,
 '{"type":"boson_stream","api_key":"","api_url":"https://api.boson.ai/v1/audio/speech","model":"higgs-audio-v3-tts","voice":"default","output_dir":"tmp/","request_timeout":30}',
 'https://docs.boson.ai/models/higgs-audio-tts/overview',
 'Boson Higgs Audio v3 配置说明：
1. 在 https://docs.boson.ai 申请 API Key（环境变量 BOSON_API_KEY 亦可）
2. model 固定为 higgs-audio-v3-tts
3. 流式 PCM 24kHz，需 xiaozhi-server 可访问 api.boson.ai
4. 当前 Public Preview 免费但有限流',
 20, 1, NOW(), 1, NOW());

DELETE FROM `ai_tts_voice` WHERE tts_model_id = 'TTS_BosonHiggsTTS';
INSERT INTO `ai_tts_voice` VALUES ('TTS_BosonHiggsTTS_0001', 'TTS_BosonHiggsTTS', 'Default', 'default', '多语言', NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, NULL);
INSERT INTO `ai_tts_voice` VALUES ('TTS_BosonHiggsTTS_0002', 'TTS_BosonHiggsTTS', 'Jake', 'jake', '英文', NULL, NULL, NULL, NULL, 2, NULL, NULL, NULL, NULL);
INSERT INTO `ai_tts_voice` VALUES ('TTS_BosonHiggsTTS_0003', 'TTS_BosonHiggsTTS', 'Chloe', 'chloe', '英文', NULL, NULL, NULL, NULL, 3, NULL, NULL, NULL, NULL);
INSERT INTO `ai_tts_voice` VALUES ('TTS_BosonHiggsTTS_0004', 'TTS_BosonHiggsTTS', 'Nora', 'nora', '英文', NULL, NULL, NULL, NULL, 4, NULL, NULL, NULL, NULL);
INSERT INTO `ai_tts_voice` VALUES ('TTS_BosonHiggsTTS_0005', 'TTS_BosonHiggsTTS', 'Oliver', 'oliver', '英文', NULL, NULL, NULL, NULL, 5, NULL, NULL, NULL, NULL);
