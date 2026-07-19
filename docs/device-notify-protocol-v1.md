# 设备通用下行 Notify 协议 v1

服务端经 **mqtt-gateway** 向 ESP32 推送异步任务；gateway **不解析业务**，只转发 `type: notify` + `payload`。  
**家长远程看娃**是首个落地场景（`action: camera.capture_and_upload`，`taskType: parent_snapshot`）。

---

## 1. 我们要做什么（整体）

| 角色 | 职责 |
|------|------|
| **家长小程序** | 发送「孩子现在在干啥」等消息 |
| **zhiban-agent** | 意图识别 → 工具 `fetch_child_device_snapshot` |
| **xiaozhi-server** | prepare → 查 MQTT 在线 → 发 **notify** → finalize 轮询 |
| **manager-api** | 生成 uploadToken/uploadUrl、收设备 HTTP 上传、绑聊天记录 |
| **mqtt-gateway** | 透明转发 `notify`（+ 现有 `mcp`） |
| **ESP32 固件** | 收 notify → 按 **action** 执行 → HTTP 回传 |

小程序/zhiban **协议不变**（`snapshot_pending` / `snapshot_ready`）。

---

## 2. 两条设备通道（并存）

| | **MCP**（现有） | **Notify**（本协议） |
|--|----------------|---------------------|
| 用途 | 同步小工具、对话内 tool | 异步大任务（拍照上传等） |
| gateway | `type: mcp`，**等设备响应** | `type: notify`，**立即 accepted** |
| 大图 | 不适合 | HTTP callback |
| idle 仅 MQTT | 视固件而定 | **设计目标** |

看娃走 **Notify**，不走 MCP 同步返图。

---

## 3. Gateway API

### 3.1 下发 notify

```http
POST http://{mqtt_manager_api}/api/commands/{clientId}
Authorization: Bearer {sha256(yyyy-MM-dd + mqtt_signature_key)}

{
  "type": "notify",
  "payload": { ...见下节... }
}
```

成功：

```json
{ "success": true, "data": { "accepted": true, "requestId": "...", "action": "..." } }
```

### 3.2 在线状态

```http
POST http://{mqtt_manager_api}/api/devices/status
{ "clientIds": ["GID_xxx@@@mac@@@mac"] }
```

| 字段 | 含义 |
|------|------|
| `isAlive` | MQTT 长连接在线（idle 也算） |
| `bridgeAlive` | 是否正在 WS 对话 |
| `exists` | 网关是否有连接 |

---

## 4. 设备 MQTT 消息（gateway → ESP32）

```json
{
  "type": "notify",
  "payload": {
    "v": 1,
    "action": "camera.capture_and_upload",
    "taskType": "parent_snapshot",
    "requestId": "snap_abc123",
    "params": {
      "maxWidth": 640,
      "jpegQuality": 80
    },
    "callback": {
      "mode": "http_upload",
      "url": "https://{manager-api}/parent-api/chat/snapshot/device-upload",
      "token": "one_time_token",
      "headers": {
        "X-Snapshot-Token": "one_time_token"
      }
    }
  }
}
```

固件 **只实现 payload 层**；`taskType` 供 HTTP 回传时带上，便于云端扩展。

---

## 5. 家长看娃完整链路

```
1. 小程序 WS → zhiban 识别看娃意图
2. zhiban → xiaozhi POST /internal/parent/device-snapshot
3. xiaozhi → manager-api prepare
       → requestId, clientId, uploadUrl, uploadToken, taskType=parent_snapshot
4. xiaozhi → gateway devices/status（MQTT 在线？busy？）
5. xiaozhi → gateway notify（action=camera.capture_and_upload）
6. 小程序 ← snapshot_pending + 文字 + done
7. ESP32 ← MQTT notify → 拍照 → HTTP POST callback.url
8. xiaozhi 轮询 status=uploaded → finalize → snapshot_ready
```

---

## 6. 设备 HTTP 回传

```http
POST {callback.url}
Content-Type: application/json
X-Snapshot-Token: {callback.token}

{
  "requestId": "snap_abc123",
  "taskType": "parent_snapshot",
  "uploadToken": "one_time_token",
  "mimeType": "image/jpeg",
  "imageBase64": "...",
  "width": 640,
  "height": 480
}
```

也支持 `multipart/form-data` 上传文件（字段 `file` + `requestId` + `uploadToken`）。

---

## 7. ESP32 固件要怎么改（配合说明）

### 7.1 不必改

- 日常对话、UDP 音频、MQTT hello/goodbye
- 现有 **MCP** 栈（`type: mcp`）
- 设备上 **不需要** zhiban/意图识别

### 7.2 必须新增

#### （1）MQTT 路由增加 `notify`

在现有消息分发处：

```
type == "mcp"     → 现有逻辑
type == "notify"  → on_device_notify(json.payload)
```

#### （2）通用分发器（长期稳定）

```cpp
using ActionHandler = std::function<void(const cJSON*)>;
std::map<std::string, ActionHandler> g_notify_actions;

void register_notify_action(const char* action, ActionHandler handler);

void on_device_notify(const cJSON* payload) {
  const char* action = cJSON_GetStringValue(cJSON_GetObjectItem(payload, "action"));
  const char* request_id = cJSON_GetStringValue(cJSON_GetObjectItem(payload, "requestId"));
  auto it = g_notify_actions.find(action ? action : "");
  if (it == g_notify_actions.end()) {
    ESP_LOGW(TAG, "unknown notify action: %s", action ? action : "(null)");
    return;
  }
  // 建议丢到 worker 任务队列，勿阻塞 MQTT 线程
  it->second(payload);
}
```

#### （3）注册首个 action：`camera.capture_and_upload`

1. 读 `params.maxWidth` / `params.jpegQuality`  
2. 调用 **与 MCP take_photo 共用** 的 camera 底层拍照  
3. 读 `callback`：当 `mode == "http_upload"` 时调用通用上传模块  
4. POST `callback.url`，Header 使用 `callback.headers` 或至少 `X-Snapshot-Token: callback.token`  
5. Body 见第 6 节  

#### （4）通用 HTTP 上传模块（建议独立，便于以后复用）

```cpp
bool device_http_upload_callback(const cJSON* callback, const uint8_t* data, size_t len,
                               const char* mime, const char* request_id, const char* task_type);
```

支持：超时、失败重试 1 次、大小上限（与 manager-api 2MB 对齐）。

#### （5）可选：`notify_ack`

完成后 MQTT 回 `{ "type": "notify_ack", "requestId", "ok", "code" }`。  
**当前云端不依赖 ack**，只靠 HTTP + 轮询；ack 便于日志与排障。

### 7.3 与 MCP 的关系

```
CameraCapture(jpeg)  ← 共用底层
    ↑              ↑
MCP take_photo   notify camera.capture_and_upload
（同步 base64）   （HTTP 上传，不阻塞 MCP）
```

### 7.4 联调检查清单

- [ ] 设备 idle、MQTT 连着，能收到 `type: notify`  
- [ ] `action=camera.capture_and_upload` 能拍照  
- [ ] HTTP POST 到 uploadUrl 返回 200  
- [ ] 家长小程序出现 `snapshot_ready`  

---

## 8. Action Registry v1

| action | 说明 | callback.mode | 状态 |
|--------|------|---------------|------|
| `camera.capture_and_upload` | 拍照并 HTTP 上传 | `http_upload` | **看娃已用** |
| （预留）`diagnostics.upload_bundle` | 日志包上传 | `http_upload` | 未实现 |

新能力 = 新 action + 固件注册 handler + 云端 prepare/finalize，**gateway 不改**。

---

## 9. 服务端代码位置（联调参考）

| 组件 | 路径 |
|------|------|
| 协议常量 / 看娃 payload 构造 | `xiaozhi-server/core/api/device_notify_protocol.py` |
| gateway 客户端 | `xiaozhi-server/core/api/mqtt_gateway_client.py` |
| 看娃编排 | `xiaozhi-server/core/api/parent_device_snapshot.py` |
| prepare/upload/finalize | `manager-api` `ParentSnapshotServiceImpl` |
| gateway 转发 | `xiaozhi-mqtt-gateway/app.js` `/api/commands` |

---

## 10. 部署顺序

1. 部署 **manager-api** + **xiaozhi-server**  
2. 部署 fork 后的 **xiaozhi-mqtt-gateway**（含 `notify`）  
3. **ESP32 固件** 合入 notify 分发 + `camera.capture_and_upload`  
4. 联调家长看娃全链路  

固件未上线前，云端会 `NOTIFY_FAILED` 或 `UPLOAD_TIMEOUT`，属预期。
