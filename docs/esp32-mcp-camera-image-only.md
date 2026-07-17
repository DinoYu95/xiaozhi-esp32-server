# ESP32 固件 MCP 拍照返图协议（image_only）

与 xiaozhi-server / zhiban-agent 并行开发用。服务端联调见 `zhiban-agent-device-mcp-image联调说明.md`。

## 1. 目标

`self.camera.take_photo` 在 `mode=image_only` 时：

- 设备只拍照并返回 JPEG base64
- **禁止** 调用 initialize 下发的 `vision.url`（`/mcp/vision/explain`）
- 由 zhiban-agent 多模态 LLM 理解图片

## 2. tools/call 入参

```json
{
  "name": "self.camera.take_photo",
  "arguments": {
    "mode": "image_only",
    "max_width": 640,
    "jpeg_quality": 80
  }
}
```

| 参数 | 默认 | 说明 |
|------|------|------|
| `mode` | `describe` | `image_only` 返图；`describe` 走旧链路 |
| `max_width` | 640 | JPEG 最大宽度 |
| `jpeg_quality` | 80 | 1–100 |
| `question` | — | 仅 `describe` 模式使用 |

## 3. 成功返回（image_only）

MCP 外层：

```json
{
  "jsonrpc": "2.0",
  "id": 42,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "<内层 JSON 字符串>"
      }
    ],
    "isError": false
  }
}
```

内层 JSON（`content[0].text`）：

```json
{
  "action": "IMAGE",
  "result": {
    "mode": "image_only",
    "mime_type": "image/jpeg",
    "width": 640,
    "height": 480,
    "image_base64": "<纯 base64，无 data: 前缀>",
    "capture_ms": 286,
    "size_bytes": 183420
  }
}
```

约束：

- JPEG 单张建议 ≤ 500KB，硬上限 1.5MB
- 整个 tools/call 建议 ≤ 15s

## 4. 错误返回（统一格式）

与成功返回同结构，**固定使用** `isError: false` + 内层 JSON：

```json
{
  "action": "ERROR",
  "response": "摄像头还没准备好，稍后再试",
  "error_code": "camera_not_ready"
}
```

| error_code | 说明 |
|------------|------|
| `camera_not_ready` | 摄像头未就绪 |
| `camera_capture_failed` | 采集失败 |
| `camera_timeout` | 采集超时 |
| `image_encode_failed` | JPEG 编码失败 |
| `image_too_large` | 压缩后仍超限 |

可选兜底：JSON-RPC 顶层 `error`（参数非法等），xiaozhi-server 也支持。

## 5. Legacy 模式（describe，保持不变）

未传 `mode` 或 `mode=describe`：

```json
{
  "action": "RESPONSE",
  "response": "我看到桌上有一个红色杯子"
}
```

流程：拍照 → POST `vision.url` + `question` → 返回文字。

## 6. tools/list Schema 更新

在 `self.camera.take_photo` 的 `inputSchema.properties` 中增加 `mode`、`max_width`、`jpeg_quality`。

## 7. 验收

| # | 调用 | 预期 |
|---|------|------|
| 1 | `{"mode":"image_only"}` | 内层 `action=IMAGE`，有 base64，无 vision HTTP |
| 2 | `{}` | 内层 `action=RESPONSE`，有文字 |
| 3 | 摄像头不可用 | 内层 `action=ERROR`，含 `error_code` |
| 4 | base64 解码 | 合法 JPEG |
