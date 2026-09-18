-- 智伴 Agent 运行配置（智控台「智伴 Agent 配置」页维护）
INSERT INTO `sys_params` (id, param_code, param_value, value_type, param_type, remark)
SELECT (SELECT IFNULL(MAX(m.id), 0) + 1 FROM `sys_params` m),
       'server.zhiban_agent_config',
       '{"version":1,"llmProfiles":{"router":{"llmModelId":"","temperature":0,"maxTokens":256},"chat":{"llmModelId":"","temperature":0.45},"vision":{"llmModelId":"","temperature":0.35},"extract":{"llmModelId":"","temperature":0},"parentTools":{"llmModelId":"","temperature":0.35},"childRiskJudge":{"llmModelId":"","temperature":0}},"embedding":{"llmModelId":""},"integration":{"xiaozhiServerUrl":"http://xiaozhi-server:8003","mcpCallTimeoutSec":45,"photoMcpTimeoutSec":60},"memory":{"routerFastPath":true,"loadContextMaxChars":1200,"episodicSkipIfEmpty":true,"blockAgentIdentity":true,"homeworkEpisodicTtlDays":30,"profileEnabled":true,"summaryEnabled":true},"features":{"homeworkPhotoJudgeEnabled":true,"childRiskLlmJudgeEnabled":false,"childRiskLlmEveryN":1}}',
       'string', 1,
       '智伴 zhiban-agent 运行配置：LLM profiles、记忆、集成、功能开关。API Key 通过 llmModelId 引用模型配置。'
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_params` s WHERE s.param_code = 'server.zhiban_agent_config'
);
