-- 更新参数字典说明：支持 llmModelId 或 baseUrl+apiKey 直连
UPDATE `sys_params`
SET `remark` = '家长小程序 AI 生成对话能力。方式1：llmModelId=ai_model_config 的 LLM id；方式2：baseUrl+apiKey(+modelName) 直连 OpenAI 兼容 API（与 llmModelId 同时填时优先 llmModelId）；都留空则用模型配置默认 LLM。enabled=false 关闭。'
WHERE `param_code` = 'server.parent_skill_assist_config';
