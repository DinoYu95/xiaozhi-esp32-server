# 家长小程序 · 影子任务 API 说明

供小程序（家长端）管理「下发给孩子的限时引导任务」。后端实现：`manager-api`，路径前缀 **`/parent-api/shadow-mission`**。

## 环境与鉴权

| 项 | 说明 |
|----|------|
| Base URL | 部署智控台后端地址，例如 `https://your-domain.com`（请按实际替换） |
| 鉴权 | Header：`Authorization: Bearer <家长登录 token>`，与现有 `/parent-api/*` 一致 |
| Content-Type | `application/json`（`POST`/`PUT`/`cancel-all`  body） |

**成功响应包装**（与项目统一 `Result` 一致）：

```json
{
  "code": 0,
  "msg": "success",
  "data": { }
}
```

`code !== 0` 表示失败，`msg` 为错误说明。

---

## 数据类型摘要

### ParentShadowMissionActiveVO（进行中列表项）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 任务 ID |
| title | string | 短标题 |
| instructions | string | 详细说明 |
| endsAt | string (ISO8601) \| null | 截止时间 |
| priority | int | 越小越优先 |

### ParentShadowMissionDetailVO（详情 / 分页行）

在 ActiveVO 基础上增加：

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | string | 设备 ID |
| childId | long | 孩子 `device_child.id` |
| status | string | `active` / `cancelled` / `expired` / `completed` |
| createTime | string \| null | 创建时间 |
| updateTime | string \| null | 更新时间 |

### ParentShadowMissionPageVO（分页）

| 字段 | 类型 | 说明 |
|------|------|------|
| list | ParentShadowMissionDetailVO[] | 当前页 |
| total | long | 总条数 |
| page | int | 当前页，从 1 开始 |
| pageSize | int | 每页条数 |
| hasMore | boolean | 是否还有下一页 |

### ParentShadowMissionUpsertResultVO（创建成功）

| 字段 | 类型 |
|------|------|
| id | long |
| title | string |

---

## 接口列表

### 1. 进行中任务列表（无分页）

- **GET** `/parent-api/shadow-mission/active?childId={childId}`
- **说明**：仅 `active` 且未过期；过期会在服务端懒标记为 `expired`，同一孩子进行中最多 **5** 条。
- **Query**：`childId` 必填，`device_child` 表主键。

**响应 `data`**：`ParentShadowMissionActiveVO[]`

---

### 2. 分页查询（含历史）

- **GET** `/parent-api/shadow-mission?childId={childId}&status={status}&page={page}&pageSize={pageSize}`
- **Query**：
  - `childId` 必填
  - `status` 可选：`active` | `cancelled` | `expired` | `completed`，不传则**全部状态**
  - `page` 默认 `1`
  - `pageSize` 默认 `20`，最大 `100`
- **排序**：按 `id` 降序（新在前）。

**响应 `data`**：`ParentShadowMissionPageVO`

---

### 3. 任务详情

- **GET** `/parent-api/shadow-mission/{id}`
- **Path**：`id` = 任务主键。

**响应 `data`**：`ParentShadowMissionDetailVO`

---

### 4. 新建任务

- **POST** `/parent-api/shadow-mission`
- **Body**：

```json
{
  "childId": 123,
  "title": "今晚 9 点前收好玩具",
  "instructions": "提醒时语气轻松，可以说收完有小奖励",
  "durationMinutes": 30
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| childId | 是 | 孩子主键 |
| title | 是 | 最长 128 字 |
| instructions | 是 | 最长 2000 字 |
| durationMinutes | 否 | 默认 30，范围 **5～180**（分钟），用于计算 `endsAt` |

**业务规则**：同一孩子当前 `active` 任务数 **≤ 5**，否则 `code != 0`。

**响应 `data`**：`ParentShadowMissionUpsertResultVO`

---

### 5. 更新进行中任务

- **PUT** `/parent-api/shadow-mission/{id}`
- **Body**（**至少填一项**；未传的字段不改）：

```json
{
  "title": "新标题",
  "instructions": "新说明",
  "durationMinutes": 60
}
```

| 字段 | 说明 |
|------|------|
| title | 可选；非空，≤128 |
| instructions | 可选；非空，≤2000 |
| durationMinutes | 可选；若传，则从**当前时刻**重算 `endsAt`（5～180） |

**约束**：仅 `status=active` 且未过期可改；已过期会报错并提示新建。

**响应 `data`**：`null`

---

### 6. 取消单条进行中任务

- **DELETE** `/parent-api/shadow-mission/{id}`
- **说明**：将单条 `active` 置为 `cancelled`。

**响应 `data`**：`null`

---

### 7. 取消该孩子全部进行中任务

- **POST** `/parent-api/shadow-mission/cancel-all`
- **Body**：

```json
{
  "childId": 123
}
```

**响应 `data`**：`null`

---

## 权限与错误说明

- 所有接口会校验：当前家长是否已绑定该孩子对应**设备**（与现有家长端设备孩子逻辑一致）。
- 常见错误：`code` 非 0，`msg` 如「孩子不存在」「未绑定设备」「仅进行中的任务可编辑」等。
- Token 无效或过期：与现有 `/parent-api` 一致，返回未授权。

---

## 与内部接口的关系

- **`/config/parent/shadow-mission/*`**：仍供 **xiaozhi-server / zhiban-agent** 使用 **`Bearer server.secret`**，**不要**写进小程序。
- 小程序**仅调用**本文 **`/parent-api/shadow-mission/*`** + 家长 Token。

---

## 小程序调用示例（伪代码）

```http
GET /parent-api/shadow-mission/active?childId=123 HTTP/1.1
Host: your-api-host
Authorization: Bearer eyJhbGciOi...
```

```http
POST /parent-api/shadow-mission HTTP/1.1
Host: your-api-host
Authorization: Bearer eyJhbGciOi...
Content-Type: application/json

{"childId":123,"title":"任务标题","instructions":"任务说明","durationMinutes":30}
```

可将本文整份提供给 Cursor 作为小程序对接上下文。
