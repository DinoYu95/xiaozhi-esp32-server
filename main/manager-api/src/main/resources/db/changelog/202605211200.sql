-- 家长端：对话能力 AI 生成（draft-from-intent）所用 LLM，与聊天总结默认模型解耦
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.parent_skill_assist_config',
       '{"enabled":true,"llmModelId":"","baseUrl":"","apiKey":"","modelName":""}',
       'string', 1,
       '家长小程序 AI 生成对话能力。llmModelId 或 baseUrl+apiKey 二选一；都空则用默认 LLM。'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_params` s WHERE s.param_code = 'server.parent_skill_assist_config'
);
