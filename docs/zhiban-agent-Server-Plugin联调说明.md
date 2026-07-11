# zhiban-agent 调用 xiaozhi Server Plugin 联调说明

## 链路概览

```
设备语音 → xiaozhi-server(ASR) → zhiban-agent /api/chat/stream
  → environment_context（device_id、plugin_functions、device_mcp）
  → xiaozhi_device_tools 路径
  → GET /internal/zhiban/plugins/schemas
  → LLM tool_call: get_weather
  → POST /internal/zhiban/plugins/execute
  → plugins_func/get_weather.py
  → 结果回 LLM → TTS
```

**Device MCP**（激光、音量等）与 **Server Plugin**（天气、音乐等）共用同一 tool loop，但插件**不依赖** `device_mcp.ready`。

## zhiban-agent 配置（`.env`）

```bash
XIAOZHI_SERVER_URL=http://127.0.0.1:8003   # xiaozhi HTTP 端口
MANAGER_API_SECRET=                          # 与智控台 server.secret 一致
XIAOZHI_MCP_CALL_TIMEOUT=45
```

## 智控台前提

1. 角色 **Intent** 不能为「无意图」
2. **配置角色 → 编辑功能** 勾选 `get_weather`、`play_music` 等
3. 设备 **重连** 后 `plugin_functions` 才会出现在 `environment_context`
4. 新增自定义插件需在 **参数字典 → 字段管理** 登记（超管）

## xiaozhi internal API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/internal/zhiban/plugins/schemas?device_id=` | 拉取当前连接可用插件 schema |
| POST | `/internal/zhiban/plugins/execute` | 执行插件，body 含 `function_name`、`arguments`、`device_id` |
| GET | `/internal/zhiban/device/mcp/tools` | Device MCP 工具列表（需 MCP 握手 ready） |
| POST | `/internal/zhiban/device/mcp/call` | 调用设备 MCP |

鉴权：`Authorization: Bearer {server.secret}`

## 进入 tool 路径的条件（zhiban-agent）

`xiaozhi_device_tools_env_ok` 为 true 当且仅当：

- 已配置 `XIAOZHI_SERVER_URL` + `MANAGER_API_SECRET`
- 有 `device_id` 或 `xiaozhi_session_id`
- **且** 满足其一：`device_mcp.ready == true`，或 `plugin_functions` 非空

## 日志关键字

| 日志 | 含义 |
|------|------|
| `api_chat_stream: xiaozhi_device_tools_path=True` | 已进入设备/插件 tool 路径 |
| `device_tools: 调用 Server Plugin get_weather` | 插件执行成功发起 |
| `xiaozhi_device_tools: 跳过 ... reason=...` | 未进 tool 路径，查 reason |

## 手工验证

设备在线且 Zhiban 模式已连接后：

```bash
SECRET=你的server.secret
DEVICE=设备MAC或ID

curl -s -H "Authorization: Bearer $SECRET" \
  "http://127.0.0.1:8003/internal/zhiban/plugins/schemas?device_id=$DEVICE" | jq .

curl -s -X POST -H "Authorization: Bearer $SECRET" -H "Content-Type: application/json" \
  -d "{\"device_id\":\"$DEVICE\",\"function_name\":\"get_weather\",\"arguments\":{\"location\":\"杭州\",\"lang\":\"zh_CN\"}}" \
  "http://127.0.0.1:8003/internal/zhiban/plugins/execute" | jq .
```

## 常见问题

| 现象 | 排查 |
|------|------|
| LLM 编造天气、无 tool 调用 | 检查 `plugin_functions` 是否为空；Intent 是否无意图；`.env` 是否配齐 |
| `plugins/execute` 404 插件不存在 | 智控台未勾选该功能，或字段管理未登记 |
| 设备会话不在线 | 设备需 WebSocket 连上 xiaozhi 且为 ZhibanAgent 模式 |
| MCP 未 ready 但天气可用 | 正常；Server Plugin 不依赖 MCP 握手 |
