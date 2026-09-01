# DevOps 硬件 OTA · manager-api 接口

规格原文：`docs/ota-integration-spec.md`（第 1～3.8 节）。  
旧 `OTAMagController` / `ai_ota` 仍保留，供 ESP32 `.bin` 使用；**k230 SWU 走本链路**（`ota_package` / `ota_release`）。

统一响应：`{ "code": 0, "msg": "ok", "data": ... }`。失败 `code != 0`。

鉴权：

| 接口 | 鉴权 |
|------|------|
| `/devops/ota/**` | Header `X-DevOps-Token`，配置项 `devops.ota.service_token`（参数字典或 `DEVOPS_OTA_SERVICE_TOKEN`） |
| `/ota/check` `/ota/report` | 设备端，现有 `/ota/**` 匿名 |

可选 Header `X-DevOps-User`：写入 `created_by` / `published_by`。

OSS：复用参数字典 `aliyun.oss.*`（endpoint / AK 等与家长端相同）。**Bucket 用独立项 `aliyun.oss.ota.bucket`**，留空才回退 `aliyun.oss.bucket`。不要改 `xiaozhi-parent`。对象键：`ota/{hardware}/{channel}/{type}/{version}/{filename}`。未启用 OSS 时落到 `uploadfile/`，设备通过 `/ota/swu/file/{oss_key}` 下载。

**设备 SWU 下载 URL（`/ota/check` 返回的 `url`）：**

| 参数字典 | 说明 |
|----------|------|
| `aliyun.oss.ota.device_download_mode` | `presigned`（默认）：OSS **预签名 URL**，私有桶可用；`proxy`：经 manager-api `GET /ota/swu/file/{oss_key}` 代理（嵌入式缺 CA 证书时用） |
| `aliyun.oss.signed_url_expire_seconds` | 预签名有效期，默认 86400 秒 |
| `xiaozhi.parent.public-base-url` | `proxy` 模式必填，如 `http://公网IP:8002/xiaozhi`（manager-api 经 Nginx，**不是** 8003） |

私有桶 **不要** 依赖 `aliyun.oss.public_read=true` 的直链（会 403）。设备端 `/ota/check` 已改为走 `resolveDeviceDownloadUrl`，与家长端 `public_read` 解耦。

**嵌入式 wget CA 报错：** 优先把 `device_download_mode` 改为 `proxy`，设备只访问 HTTP 的 manager-api；或在设备上安装 `ca-certificates` 后继续用 `presigned`。

SWU 文件名：`^(system|app)_([A-Za-z0-9_-]+)_(\d+\.\d+\.\d+(?:[-+][\w.]+)?)_(stable|beta)\.swu$`

覆盖度：`percent = success_count / eligible_count`。eligible = 同 hardware 且命中灰度（`hash(mac)%100 < rollout`）或白名单池 / extra MAC，并满足通道可见性。

发布状态仅 `active` / `rolled_back` / `superseded`，无「暂停」。Beta + active 可 `POST .../rollback`：

- 覆盖度 0%：只标记 `rolled_back`
- 已有成功升级：标记 `rolled_back` 并重激活 `previous_release`（或 body `package_id`）

## DevOps 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/devops/ota/hardware-types` | 硬件类型列表 / 创建 |
| PUT/DELETE | `/devops/ota/hardware-types/{key}` | 更新 / 软删 |
| POST | `/devops/ota/packages/upload` | multipart `file` + `notes` |
| GET | `/devops/ota/packages` | `type` `hardware` `channel` `status` |
| DELETE | `/devops/ota/packages/{id}` | 仅 draft |
| GET | `/devops/ota/devices` | `page` `page_size` `keyword` `hardware` `device_type` `channel` |
| GET/POST | `/devops/ota/whitelist-pools` | 池列表 / 创建（可带 `mac_addresses`） |
| PUT/DELETE | `/devops/ota/whitelist-pools/{id}` | 更新 / 删除 |
| POST | `/devops/ota/whitelist-pools/{id}/devices` | `{ "mac_addresses": [] }` |
| DELETE | `/devops/ota/whitelist-pools/{id}/devices/{mac}` | 移除设备 |
| GET/POST | `/devops/ota/releases` | 列表 / 创建 |
| POST | `/devops/ota/releases/{id}/rollback` | Beta 回滚 |
| GET | `/devops/ota/releases/{id}/coverage` | 覆盖度明细 |

## 设备端开机流程（k230，无需唤醒）

```
上电 → Wi-Fi
  → POST /ota/            激活 + websocket（Header Device-Id = MAC）
  → POST /ota/check       SWU manifest（sha256 / release_id）
  → 有 updates：下载 → 校验 → 刷写 → POST /ota/report
       升了 system 则重启，再走一遍 /ota/ + /ota/check
  → 无更新或超时：连 WebSocket，进入待机
```

设备固件实现说明（可直接丢给设备端 agent）：`docs/ota-device-firmware-prompt.md`。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ota/` | 现有激活检查。k230 已登记硬件时响应可带 `updates`（仅 version/url）。**刷写必须以 `/ota/check` 为准** |
| POST | `/ota/check` | `{ updates: { system\|app: { version, url, sha256, release_id, mandatory } } }`。`auto_update=0` 时为空 |
| POST | `/ota/report` | `{ mac_address, release_id, type, from_version, to_version, status, error_message }` |

字段：`system_version` = 系统/固件 SWU；`app_version` = 应用 SWU（不要用固件冒充）；`board` = 硬件 key（如 `k230_linux_board`）；`device_type` = 业务类型；`ota_channel` = `stable` \| `beta`。

未绑定设备：`POST /ota/` 把上述字段写入 Redis，绑定后落 `ai_device`。已在目标版本但漏报 success 时，`/ota/check` 会按版本补记覆盖度。

`ai_device` 新增：`device_type`、`system_version`、`ota_channel`。智控台设备列表 VO 同步返回 Device Type / System Version / App Version / board。
