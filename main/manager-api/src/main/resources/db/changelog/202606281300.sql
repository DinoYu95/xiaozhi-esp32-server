-- 家长端绑设备自动创建智能体：记忆/TTS/音色/聊天记录上报默认值（智控台参数管理可改）
DELETE FROM `sys_params` WHERE param_code = 'server.agent_device_bind_defaults';
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.agent_device_bind_defaults',
       '{"memModelId":"Memory_nomem","chatHistoryConf":2,"ttsModelId":"TTS_HuoshanDoubleStreamTTS","ttsVoiceId":"TTS_HuoshanDoubleStreamTTS_0023"}',
       'string',
       1,
       '绑设备新建 agent 默认：无记忆、上报文字+语音(2)、火山双流式 TTS、音色かずね(和音)；id 须与 ai_model_config/ai_tts_voice 一致';
