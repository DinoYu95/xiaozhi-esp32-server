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

## 设备端

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/ota/check` | 返回 `{ updates: { system\|app: { version, url, sha256, release_id, mandatory } } }` |
| POST | `/ota/report` | `{ mac_address, release_id, type, from_version, to_version, status, error_message }` |
| POST | `/ota/` | 现有激活检查；若 `board` 为已登记硬件类型，firmware/updates 走新 SWU 链路 |

`ai_device` 新增：`device_type`、`system_version`、`ota_channel`。`app_version` 语义为应用 SWU 版本。智控台设备列表 VO 同步返回 Device Type / System Version / App Version / board。
