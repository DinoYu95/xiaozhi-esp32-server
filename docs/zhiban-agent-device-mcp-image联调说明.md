# zhiban-agent 调用设备拍照返图联调说明

## 链路（新）

```
用户语音 → xiaozhi-server(ASR) → zhiban-agent 意图识别
  → 判定需要拍照
  → POST /internal/zhiban/device/mcp/call
       { tool_name: "self_camera_take_photo", arguments: { mode: "image_only" } }
  → ESP32 拍照，返回 image_base64
  → zhiban-agent 多模态 LLM（图 + 文）生成回复
  → SSE → xiaozhi TTS
```

**不再经过** `/mcp/vision/explain`（Zhiban + 新固件路径）。

## xiaozhi-server 已实现

| 能力 | 说明 |
|------|------|
| `Action.IMAGE` | 解析设备 MCP 内层 `action: IMAGE` |
| `device_mcp/call` 返图 | 响应含 `action`、`result`、`image_base64`（顶层展平）、`mime_type`、`width`、`height` 等 |
| 日志 | `device_mcp/call 返图` / `execute_device_mcp 返图`，不含 base64 正文 |

## internal API

### POST `/internal/zhiban/device/mcp/call`

请求：

```json
{
  "device_id": "设备MAC或ID",
  "tool_name": "self_camera_take_photo",
  "arguments": {
    "mode": "image_only",
    "max_width": 640,
    "jpeg_quality": 80
  },
  "timeout": 45
}
```

成功响应（返图）：

```json
{
  "ok": true,
  "action": "IMAGE",
  "result": {
    "mode": "image_only",
    "mime_type": "image/jpeg",
    "width": 640,
    "height": 480,
    "image_base64": "...",
    "capture_ms": 286,
    "size_bytes": 183420
  },
  "response": null,
  "image_base64": "...",
  "mime_type": "image/jpeg",
  "width": 640,
  "height": 480,
  "capture_ms": 286,
  "size_bytes": 183420,
  "device_id": "...",
  "session_id": "...",
  "tool_name": "self_camera_take_photo"
}
```

错误响应：

```json
{
  "ok": false,
  "action": "ERROR",
  "response": "摄像头还没准备好，稍后再试",
  "result": null
}
```

鉴权：`Authorization: Bearer {server.secret}`

## zhiban-agent 已实现

| 能力 | 说明 |
|------|------|
| `Action.IMAGE` 解析 | xiaozhi-server 侧（见上文） |
| 拍照 tool 调用 | `device_tools_runner` 强制 `mode=image_only` |
| 多模态 LLM | `OPENAI_VISION_MODEL` + `_reply_after_photo_vision` |
| 快速路径 | 「拍照」「帮我看看」等 → `self_camera_take_photo` |
| 作业辅导 | `environment_context.active_mode=homework_tutor` 时使用作业向 vision prompt |

### 参考调用（Python）

```python
result = xiaozhi_client.device_mcp_call(
    "self_camera_take_photo",
    {"mode": "image_only", "max_width": 640},
    device_id=device_id,
    timeout=45,
)
if result.get("action") == "IMAGE" and result.get("ok"):
    image_b64 = result.get("image_base64") or (result.get("result") or {}).get("image_base64")
    mime = result.get("mime_type") or "image/jpeg"
    # 送入多模态 messages
elif result.get("action") == "ERROR":
    # 用 result["response"] 回复用户
    ...
```

## 手工 curl 验证

```bash
SECRET=你的server.secret
DEVICE=设备MAC或ID

curl -s -X POST -H "Authorization: Bearer $SECRET" -H "Content-Type: application/json" \
  -d "{\"device_id\":\"$DEVICE\",\"tool_name\":\"self_camera_take_photo\",\"arguments\":{\"mode\":\"image_only\"},\"timeout\":45}" \
  "http://127.0.0.1:8003/internal/zhiban/device/mcp/call" | jq '{ok, action, mime_type, width, height, size_bytes}'
```

## 相关文档

- 固件协议：`docs/esp32-mcp-camera-image-only.md`
- Server Plugin 联调：`docs/zhiban-agent-Server-Plugin联调说明.md`
