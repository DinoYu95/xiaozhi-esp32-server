# 智控台 · 智伴 Agent 配置

zhiban-agent 的运行参数（LLM 模型、temperature、记忆开关、集成地址等）在智控台统一维护，无需再改 ECS 上的大量 `OPENAI_*` 环境变量。

## 入口

**参数字典** 下拉 → **智伴 Agent 配置**  
（路由：`/zhiban-agent-config`，需超级管理员）

## ECS 上仍需保留的 `.env.prod`

```bash
MANAGER_API_BASE=http://xiaozhi-web:8002/xiaozhi
MANAGER_API_SECRET=与 server.secret 一致
POSTGRES_MEMORY_URL=postgresql://...
```

## 配置如何生效

1. 智控台保存 → 写入参数字典 `server.zhiban_agent_config`
2. zhiban-agent 启动及每约 60 秒 → `GET /config/zhiban/runtime`（Bearer server.secret）
3. 各 LLM profile 的 **API Key 来自「模型配置」**，此处只选 `llmModelId`

## LLM Profile 说明

| profile | 用途 | 建议 |
|---------|------|------|
| router | 意图路由、作业照判断 | qwen-turbo，temp=0 |
| chat | 主对话 | qwen-plus，temp≈0.45 |
| vision | 设备拍照多模态 | qwen-vl-max |
| extract | 记忆抽取/摘要 | qwen-turbo，temp=0 |
| parentTools | 家长 tool loop | qwen-plus |
| childRiskJudge | 风险 LLM 判别 | qwen-turbo |

## 接口

| 接口 | 调用方 |
|------|--------|
| `GET/PUT /admin/zhiban-agent/config` | 智控台页面 |
| `GET /config/zhiban/runtime` | zhiban-agent |
