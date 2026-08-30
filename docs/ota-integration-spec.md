# 硬件 OTA · DevOps 集成规格 & manager-api 开发 Prompt

> 本文档供 DevOps 平台开发 + manager-api 开发（可整段交给 code agent）使用。  
> 交互 Demo：`docs/ota-demo.html`（v2）

---

## 0. 总体架构

```
┌──────────────── DevOps Platform ────────────────┐
│  Vue 页面（风格与现有 DevOps 一致）              │
│  · 更新包上传（调 manager-api）                 │
│  · 硬件类型 CRUD（调 manager-api）              │
│  · 设备列表 / 批量建白名单池                     │
│  · 发布管理 / 覆盖度 / Beta 回滚                │
│  PostgreSQL：可选仅存 DevOps 操作审计            │
└───────────────────────┬─────────────────────────┘
                        │ HTTPS + Service Token
                        ▼
┌──────────────── manager-api (xiaozhi-esp32-server) ────────────────┐
│  · SWU 上传 OSS + 包元数据                                         │
│  · 设备数据（ai_device 扩展字段）                                   │
│  · 白名单池 / 发布规则 / 升级状态                                   │
│  · 设备端 manifest（连接时拉取）                                    │
│  · 设备端升级结果上报                                               │
└────────────────────────────────────────────────────────────────────┘
```

### DevOps 菜单位置建议

侧边栏在 **「数据库」下方** 增加一级菜单 **「硬件 OTA」**，路由 `/ota`，页内 Tab（与 Database 类似）：

| Tab | 路由 | 说明 |
|-----|------|------|
| 更新包 | `/ota/packages` | 上传 SWU、draft 列表 |
| 发布管理 | `/ota/releases` | 发布、覆盖度、Beta 回滚 |
| 设备 | `/ota/devices` | 读 manager-api，批量建白名单池 |
| 白名单池 | `/ota/pools` | 池子 CRUD |
| 硬件类型 | `/ota/hardware-types` | 枚举 CRUD |

UI 组件复用：`PageShell`、`panel-card`、`table-card`、Element Plus，**不要**用 OTA demo 的深色主题。

### DevOps 后端（可选薄代理层）

`devops-platform/backend` 增加 `MANAGER_API_BASE_URL` + `MANAGER_API_SERVICE_TOKEN`，前端只调 DevOps `/api/ota/*`，由 DevOps 转发到 manager-api（避免浏览器暴露 service token）。也可前端直连 manager-api（不推荐）。

---

## 1. 设备表字段（与智控台改造对齐）

**另一 agent 在智控台 / ai_device 增加的字段（manager-api 需同步）：**

| 字段 | DB 列建议 | 说明 |
|------|-----------|------|
| device_type | `device_type` VARCHAR(32) | 业务设备类型（与用户场景相关，非 board） |
| system_version | `system_version` VARCHAR(32) | 原 firmwareVersion 语义迁移；系统 SWU 版本 |
| app_version | `app_version` VARCHAR(32) | 应用 SWU 版本（已有列可复用但语义明确为 app） |
| board | `board` | 保留，= Device Model = 硬件板型，如 `k230_linux_board` |
| mac_address | `mac_address` | 设备 MAC，运维主标识 |
| ota_channel | `ota_channel` VARCHAR(16) DEFAULT 'stable' | 订阅通道 stable/beta |

设备连接/OTA 检查时更新 `system_version`、`app_version`（从设备上报写入）。

---

## 2. SWU 包命名与校验（上传时必须）

### 文件名规则（正则）

```regex
^(system|app)_([A-Za-z0-9_-]+)_(\d+\.\d+\.\d+(?:[-+][\w.]+)?)_(stable|beta)\.swu$
```

示例：`system_k230_linux_board_1.3.1_stable.swu`

| 段 | 含义 |
|----|------|
| type | `system` \| `app` |
| hardware | 必须在硬件类型枚举内（与 `board`/硬件 key 一致） |
| version | semver |
| channel | `stable` \| `beta` |

### 上传校验

1. 扩展名必须为 `.swu`
2. 文件名必须匹配上述正则
3. `hardware` 必须在 `ota_hardware_type` 表中且 `enabled=true`
4. 计算文件 sha256、size，写入包记录
5. OSS path：`ota/{hardware}/{channel}/{type}/{version}/{filename}`

---

## 3. manager-api 需实现的 API（DevOps 专用）

**统一前缀建议：** `/manager-api/devops/ota`  
**鉴权：** Header `X-DevOps-Token: <service_token>`（与 DevOps `.env` 配置一致，不走家长 Token）

**统一响应：**

```json
{ "code": 0, "msg": "ok", "data": { ... } }
```

失败：`code != 0`，`msg` 可读错误信息。

---

### 3.1 硬件类型 CRUD

DevOps「硬件类型」页调用。

```
GET    /devops/ota/hardware-types
POST   /devops/ota/hardware-types
PUT    /devops/ota/hardware-types/{key}
DELETE /devops/ota/hardware-types/{key}   # 软删 enabled=false
```

**HardwareType 对象：**

```json
{
  "key": "k230_linux_board",
  "name": "K230 Linux 板",
  "description": "",
  "enabled": true,
  "created_at": "2026-08-30T12:00:00Z",
  "updated_at": "2026-08-30T12:00:00Z"
}
```

- `key` 唯一，与 SWU 文件名 hardware 段一致
- 删除时若已有包/发布引用则禁止硬删

---

### 3.2 上传 SWU 包

```
POST /devops/ota/packages/upload
Content-Type: multipart/form-data
```

| 字段 | 类型 | 说明 |
|------|------|------|
| file | file | .swu 文件 |
| notes | string | 可选说明 |

**服务端逻辑：** 校验文件名 → 解析 type/hardware/version/channel → 上传 OSS → 插入 `ota_package`，`status=draft`

**响应 Package：**

```json
{
  "id": "uuid",
  "type": "system",
  "hardware": "k230_linux_board",
  "version": "1.3.1",
  "channel": "stable",
  "filename": "system_k230_linux_board_1.3.1_stable.swu",
  "oss_key": "ota/k230_linux_board/stable/system/1.3.1/...",
  "size_bytes": 129394932,
  "sha256": "abc...",
  "status": "draft",
  "notes": "",
  "created_by": "devops_user",
  "created_at": "..."
}
```

```
GET /devops/ota/packages?type=&hardware=&channel=&status=draft|published|archived
DELETE /devops/ota/packages/{id}   # 仅 draft 可删
```

---

### 3.3 设备列表（DevOps 设备 Tab）

```
GET /devops/ota/devices?page=1&page_size=20&keyword=&hardware=&device_type=&channel=
```

**DeviceOtaView 对象：**

```json
{
  "device_id": "b0:8c:b3:c6:cf:78",
  "mac_address": "b0:8c:b3:c6:cf:78",
  "board": "k230_linux_board",
  "device_type": "toy",
  "system_version": "1.3.0",
  "app_version": "2.0.0",
  "ota_channel": "stable",
  "online": true,
  "auto_update": true,
  "last_connected_at": "2026-08-30T13:55:00Z",
  "parent_display_name": "客厅小智",
  "latest_visible": {
    "system": "1.3.1",
    "app": "2.0.0"
  },
  "update_available": {
    "system": false,
    "app": false
  }
}
```

- `latest_visible` / `update_available`：服务端按当前 active 发布 + 灰度 + 白名单池计算，DevOps 直接展示
- `keyword` 匹配 mac / device_id / parent_display_name

---

### 3.4 白名单池

```
GET    /devops/ota/whitelist-pools
POST   /devops/ota/whitelist-pools
PUT    /devops/ota/whitelist-pools/{id}
DELETE /devops/ota/whitelist-pools/{id}
POST   /devops/ota/whitelist-pools/{id}/devices   # body: { "mac_addresses": ["..."] }
DELETE /devops/ota/whitelist-pools/{id}/devices/{mac}
```

**Pool 对象：**

```json
{
  "id": 1,
  "name": "beta-内测-8台",
  "description": "展厅+研发手板",
  "device_count": 8,
  "created_at": "...",
  "updated_at": "..."
}
```

设备 Tab 批量勾选 → 调 `POST .../whitelist-pools` + `POST .../devices`。

---

### 3.5 发布管理

```
GET  /devops/ota/releases
POST /devops/ota/releases
POST /devops/ota/releases/{id}/rollback   # 仅 beta 且 active
GET  /devops/ota/releases/{id}/coverage
```

**创建发布 POST body：**

```json
{
  "package_id": "uuid",
  "channel": "beta",
  "rollout_percent": 30,
  "whitelist_pool_ids": [1, 2],
  "extra_mac_addresses": []
}
```

**Release 对象：**

```json
{
  "id": 101,
  "package_id": "uuid",
  "type": "app",
  "hardware": "k230_linux_board",
  "version": "2.1.0",
  "channel": "beta",
  "rollout_percent": 30,
  "whitelist_pool_ids": [1],
  "status": "active",
  "published_at": "...",
  "published_by": "dino",
  "coverage": {
    "eligible_count": 120,
    "success_count": 36,
    "failed_count": 2,
    "pending_count": 82,
    "percent": 30.0
  },
  "rollback_available": true,
  "previous_release_id": 100
}
```

**发布规则（manifest 计算，manager-api 实现）：**

1. 同 `hardware + channel + type`，active 发布中 **版本最高** 的一条为候选（或 DevOps 指定 package 发布即激活该 package）
2. 设备可见条件：  
   `hash(mac) % 100 < rollout_percent` **OR** mac ∈ 关联白名单池 **OR** mac ∈ extra_mac_addresses
3. stable 设备只看 stable 发布；beta 通道设备可看 beta + stable（beta 优先）

**不要「暂停」操作**；仅 `active` / `rolled_back` / `superseded`。

---

### 3.6 覆盖度（发布详情页）

```
GET /devops/ota/releases/{id}/coverage
```

**响应：**

```json
{
  "eligible_count": 120,
  "success_count": 36,
  "failed_count": 2,
  "downloading_count": 5,
  "pending_count": 77,
  "percent": 30.0,
  "devices": [
    {
      "mac_address": "b0:8c:b3:c6:cf:78",
      "status": "success",
      "from_version": "2.0.0",
      "to_version": "2.1.0",
      "reported_at": "..."
    }
  ]
}
```

**设备升级状态枚举：** `pending` | `downloading` | `success` | `failed` | `skipped`

---

### 3.7 Beta 回滚设计

**场景 A：覆盖度 0%（尚无设备成功升级）**

- 操作：将当前 release 标记为 `rolled_back`
- manifest：该 package 对所有人不可见（等同从未发布）
- 无需下发降级包

**场景 B：已有部分/全部设备成功升级到 beta 包**

- 操作：`POST .../releases/{id}/rollback`
- 逻辑：
  1. 当前 release → `rolled_back`
  2. 自动 **重新激活** 同 hardware+channel+type 的上一条 `previous_release`（或 DevOps 回滚时指定目标 package_id）
  3. manifest 对未升级设备：不再返回 rolled_back 包
  4. 对已升级设备：若 `system/app` 支持降级 SWU，返回 **previous 版本** 的 manifest（optional，看硬件能力）；若不支持降级，仅停止继续推送 beta，已升设备保持现状并在 UI 标记「需人工处理」

**DevOps UI：**

- Beta 发布行显示 **回滚** 按钮（非 pause）
- 回滚前弹窗展示：`success_count`、`failed_count`，提示场景 B 风险
- 覆盖度进度条：`success_count / eligible_count`

---

### 3.8 设备端 Manifest（现有 OTA 流程扩展）

供设备/xiaozhi-server 调用（**非 DevOps Token**，设备鉴权）：

```
POST /ota/check   # 或扩展现有 device report 响应中的 firmware 段
```

**请求（示例）：**

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

**响应：**

```json
{
  "updates": {
    "system": {
      "version": "1.3.1",
      "url": "https://signed-oss-url...",
      "sha256": "...",
      "release_id": 101,
      "mandatory": false
    }
  }
}
```

仅返回比当前新的、且该设备有权看到的包。

**升级结果上报（设备完成/失败后）：**

```
POST /ota/report
```

```json
{
  "mac_address": "...",
  "release_id": 101,
  "type": "app",
  "from_version": "2.0.0",
  "to_version": "2.1.0",
  "status": "success",
  "error_message": null
}
```

用于覆盖度统计。

---

## 4. 建议新增表（manager-api）

| 表 | 说明 |
|----|------|
| `ota_hardware_type` | key, name, description, enabled |
| `ota_package` | SWU 包元数据 + OSS |
| `ota_release` | 发布记录、灰度、状态、previous_release_id |
| `ota_release_pool` | release_id ↔ pool_id |
| `ota_whitelist_pool` | 池名、描述 |
| `ota_whitelist_pool_device` | pool_id, mac_address |
| `ota_device_upgrade_log` | 升级状态流水 |
| `ai_device` 扩展 | device_type, system_version, ota_channel（app_version/board 已有） |

---

## 5. DevOps `.env` 配置项

```env
MANAGER_API_BASE_URL=https://your-manager-api.example.com/manager-api
MANAGER_API_SERVICE_TOKEN=change-me-devops-ota-token
```

manager-api 参数字典可复用现有 `aliyun.oss.*`（与 ParentStorage 相同）。

---

## 6. 给 manager-api Code Agent 的 Prompt（可直接复制）

```
你在 xiaozhi-esp32-server 的 manager-api 模块实现「DevOps 硬件 OTA」后端能力。

请完整阅读 devops-platform 仓库中的 docs/ota-integration-spec.md（路径可能需从 DevOps 项目拷贝），并严格实现其中第 1～3.8 节约定。

优先级：
P0: 硬件类型 CRUD、SWU 上传 OSS（文件名正则校验）、包列表
P0: DevOps 设备列表 API（含 system/app 版本、latest_visible、update_available）
P0: 白名单池 CRUD + 批量添加设备
P1: 发布创建、manifest 灰度逻辑（rollout + 白名单池）、覆盖度统计
P1: 设备升级 report 接口
P2: Beta 回滚（0% 覆盖 vs 已有成功升级两种分支）

与智控台改造对齐：
- ai_device 增加 device_type、system_version；app_version 语义明确为应用版本
- 智控台 Device Management 会展示 Device Type、System Version、App Version（另一 agent 负责前端）

技术约束：
- OSS 上传复用 ParentStorageServiceImpl 的 aliyun.oss.* 配置模式
- DevOps 专用接口使用 X-DevOps-Token 鉴权，新增配置项 devops.ota.service_token
- SWU 文件名规则：^(system|app)_([A-Za-z0-9_-]+)_(\d+\.\d+\.\d+(?:[-+][\w.]+)?)_(stable|beta)\.swu$
- 不要使用「暂停发布」状态；Beta 发布支持 rollback API
- 覆盖度 = success_count / eligible_count（eligible 由灰度+池计算）

交付物：
- 数据库 changelog SQL
- Controller / Service / Entity
- 接口文档（OpenAPI 或 README 片段）
- 关键单测：文件名解析、灰度命中、回滚状态机

如有与现有 OTAMagController / ai_ota 冲突，以新 ota_package / ota_release 模型为准，旧 OTA 可保留但 k230 SWU 走新链路。
```

---

## 7. DevOps 前端开发顺序（本仓库）

1. 侧边栏 + 路由 `/ota/*`（PageShell 风格）
2. 硬件类型页（调 manager-api）
3. 更新包上传页
4. 设备页 + 批量创建白名单池
5. 白名单池管理页
6. 发布管理 + 覆盖度 + Beta 回滚

Demo `ota-demo.html` 仅作交互参考，正式 UI 必须用 DevOps 浅色主题。
