# 设备端配合 Prompt（k230 硬件 OTA）

把「以下整段」（从「你是 k230」到文末）复制给设备固件 / Linux 应用 agent。manager-api 已按此实现。

---

你是 k230 设备（硬件 key：`k230_linux_board`）固件/应用开发 agent。在开机流程里实现自动 OTA，对接 xiaozhi **manager-api**。

OTA **不是**语音技能，**不要**等唤醒词，**不要**通过 xiaozhi-server WebSocket 升级。连上 Wi‑Fi 后立刻用 HTTP 做完检查/升级，再进待机。

## 服务端

- Base：`{MANAGER_API}/xiaozhi`（例：`https://host:8002/xiaozhi`）
- **不要**带 `X-DevOps-Token`（那是 DevOps 后台用的）
- `Content-Type: application/json`
- MAC 形如 `b0:8c:b3:c6:cf:78`（小写、冒号分隔）

## 开机顺序（必须按此；要有超时，失败不能卡死开机）

```
上电 → 连 Wi-Fi
  1) POST /xiaozhi/ota/          // 激活 + 拿 websocket（现有小智逻辑保留）
  2) POST /xiaozhi/ota/check     // 拉 SWU manifest（以此为准决定是否升级）
  3) 若 data.updates 有包：
       下载 url → 校验 sha256 → 刷写 → POST /xiaozhi/ota/report
       若刷了 system：立刻重启，重启后从步骤 1 再走一遍（再处理 app）
  4) updates 为空，或 OTA 超时/失败：连步骤 1 拿到的 WebSocket，进入待机
  5) 之后才是唤醒 / 对话
```

- HTTP 检查建议超时 15s；整段 OTA（不含大文件下载）建议 60s 上限
- 下载单独超时（按包大小，建议数分钟）；超时 `report failed`，然后进待机
- 智控台把设备 `auto_update` 关掉时，`/ota/check` 返回空 `updates`，直接进待机

## 版本字段（不要填错）

| 字段 | 含义 | 错误示范 |
|------|------|----------|
| `system_version` | 当前**系统/固件** SWU，例如 `1.3.0` | 不要空着只填 `application.version` 当应用 |
| `app_version` | 当前**应用** SWU；没有应用包就省略/`null` | **禁止**把固件版本填进这里 |
| `board` | 硬件类型，必须是 `k230_linux_board` | 不要用 `toy` 或芯片名冒充 |
| `device_type` | 业务类型，如 `toy` | 可省略 |
| `ota_channel` | `stable`（默认）或 `beta` | 不要自造通道名 |

服务端灰度/白名单自己算，设备**不要**算 `hash(mac)%100`。

## 1) POST /xiaozhi/ota/（每次开机都打）

**Header 必填：** `Device-Id: {mac}`  
可选：`Client-Id`（没有就等于 MAC）

注意：`board` 是**对象**。响应**不是** `{code,data}` 包裹，是扁平 JSON。

```http
POST /xiaozhi/ota/
Device-Id: b0:8c:b3:c6:cf:78
Content-Type: application/json
```

```json
{
  "mac_address": "b0:8c:b3:c6:cf:78",
  "board": { "type": "k230_linux_board" },
  "device_type": "toy",
  "system_version": "1.3.0",
  "app_version": "2.0.0",
  "ota_channel": "stable"
}
```

未绑定也会把这些字段写入 Redis，家长/智控台绑定后进 `ai_device`。**第一次没绑定也要带齐**，否则列表里 System/类型/通道是空的。

响应（节选）：

```json
{
  "activation": { "code": "123456", "message": "...", "challenge": "..." },
  "firmware": { "version": "1.3.0", "url": "..." },
  "updates": { "system": { "version": "1.3.1", "url": "..." } },
  "websocket": { "url": "wss://...", "token": "..." }
}
```

- 有 `activation`：未绑定，照旧显示激活码；**仍要继续 `/ota/check`**
- `firmware` / 这里的 `updates` **只有 version/url，没有 sha256 / release_id**
- **禁止**用 `firmware.url` 当 k230 SWU（ESP32 旧 `.bin` 通道）。k230 只认下一步 `/ota/check`

## 2) POST /xiaozhi/ota/check（刷写的唯一依据）

`board` 这里是**字符串**，不是对象。建议同样带 `Device-Id`。

```http
POST /xiaozhi/ota/check
Device-Id: b0:8c:b3:c6:cf:78
Content-Type: application/json
```

```json
{
  "mac_address": "b0:8c:b3:c6:cf:78",
  "board": "k230_linux_board",
  "device_type": "toy",
  "system_version": "1.3.0",
  "app_version": "2.0.0",
  "ota_channel": "stable"
}
```

成功：`code=0`

```json
{
  "code": 0,
  "msg": "ok",
  "data": {
    "updates": {
      "system": {
        "version": "1.3.1",
        "url": "https://signed-oss-or-local-url...",
        "sha256": "abc...",
        "release_id": 101,
        "mandatory": false
      }
    }
  }
}
```

- `data.updates` 空或不存在 → 无更新，去连 WebSocket
- 可能同时有 `system` 和 `app` 两个 key
- 只返回比当前**新**的包（回滚时可能是更低版本，同样要升）

## 3) 下载与刷写

1. 同时有 system 和 app：**先 system，后 app**
2. HTTP 下载 `url` 得到 `.swu`，按 `sha256`（小写 hex）校验；不对就 `report failed`，**不要刷**
3. 刷 system 成功后：尽量先 `report success`，再重启；重启后重新 `/ota/` + `/ota/check` 再处理 app
4. 只刷 app 可以不重启（按你们分区设计），但仍要 `report`

## 4) POST /xiaozhi/ota/report（成功失败都要打）

覆盖度全靠这个。`release_id` **必须**用 check 返回的数字，不要自己编。

```http
POST /xiaozhi/ota/report
Content-Type: application/json
```

```json
{
  "mac_address": "b0:8c:b3:c6:cf:78",
  "release_id": 101,
  "type": "system",
  "from_version": "1.3.0",
  "to_version": "1.3.1",
  "status": "success",
  "error_message": null
}
```

| status | 何时 |
|--------|------|
| `downloading` | 可选，开始下载 |
| `success` | 刷写成功。重启前能报就报；漏报时服务端会在下次 check 按版本补 success |
| `failed` | 下载失败、sha256 不对、刷写失败；`error_message` 写原因（英文或中文均可） |
| `skipped` | 策略跳过 |

`type` 只能是 `system` 或 `app`，与正在升的包一致。

## 不要做

- 等唤醒词再 OTA
- 把固件版本写入 `app_version`
- 请求 `/devops/ota/**` 或带 `X-DevOps-Token`
- 设备自己算灰度
- 用旧 `ai_ota` / `firmware.url` 的 `.bin` 规则刷 k230
- 无超时死等下载

## 联调自测

1. 未绑定开机：`/ota/` 有 `activation.code`；绑定后 DevOps 设备列表 System 有值、board=`k230_linux_board`
2. DevOps 发更高版本 system：再开机，`/ota/check` 出现 `data.updates.system`
3. 升完 `report success`：覆盖度 success+1，列表 System 变新版本
4. 故意坏 sha256 / 拔网：`report failed`，设备仍进待机并能对话

## 服务端已具备（不要在设备上再做）

硬件类型、SWU 上传 OSS、发布/灰度/白名单、覆盖度、`/ota/check` `/ota/report`、绑定回写 `device_type` / `system_version` / `app_version` / `ota_channel`。
