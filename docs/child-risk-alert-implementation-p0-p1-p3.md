# 儿童对话风险预警：P0 + P1 + P3 实施说明（异步）

本文约定：**风险判定在 zhiban-agent 中异步执行**，不阻塞主对话；**P1 仅做「家长小程序可拉取的通知/站内信」**（不接短信/外呼、不上 MQ，outbox 用 DB + 定时任务）；**P2（短信/电话）不纳入本期**。

---

## 一、范围对照

| 包 | 内容 |
|----|------|
| **P0** | 落库、内部上报接口、总开关、智控台**只读**事件列表、基础配置项（如开关） |
| **P1** | 定级/去重/冷却、outbox 表、定时任务消费 outbox、**写入家长端可见「风险通知」**、家长小程序 **HTTP 拉取列表/未读** |
| **P3** | zhiban：每 N 轮 + 规则命中 → 异步 JSON 风险 + 调内部接口；管理端 **风险规则**（关键词/正则）+ **冷却/频控** 可配置 |

**本期不做**：微信模板消息推送（需订阅消息模板与微信侧配置）、真实短信/外呼、Kafka/RocketMQ（仅预留 outbox 形态）。

---

## 二、目标架构（逻辑）

```text
zhiban-agent
  主对话返回 reply 后（不等待）
  └─ 异步线程/任务池
       ├─(P3) 每 N 轮 或 规则先扫 文本 → 命中则定级/写 reason
       ├─(P3) 可选再调一次 低温「仅 JSON」LLM
       └─ need_alert 时 httpx → manager-api: POST /config/child/risk-signal
                                    （Bearer: server.secret）

manager-api
  ChildRiskService.receiveSignal()
    ├─ 总开关关 → 只记审计日志/可选丢弃
    ├─ 与 DB 中 risk_rule 合并、取 max(级别)
    ├─ 去重/冷却（Redis 或 查 child_risk_event 表）
    ├─ 写 child_risk_event
    ├─ 需触达时写 child_risk_outbox(channel=MINI_APP)
  @Scheduled
    OutboxProcessor → 更新 outbox=SUCCESS、写 parent_risk_notification（或合并到一张表）
```

家长打开小程序：调用 **`GET /parent-api/risk-alerts`（已存在 P1 设计）** 拉列表；**未读数**可 `GET /parent-api/risk-alerts/unread-count`。

管理端：**列表/详情只读** + **规则 CRUD** + **参数**（开关、N、冷却分钟、每级是否启用等）。

---

## 三、数据库设计（建议表名，可按项目规范微调）

### 1. `child_risk_config`（或用 `sys_params` 一条 JSON 简版）

若快速上线可 **全部用 `sys_params` 一个 JSON**；独立表更利于多环境迁移。

**推荐字段**（单表行或 JSON 内键）：

| 键 | 含义 | 默认 |
|----|------|------|
| `enabled` | 总开关 | false |
| `zhiban_eval_interval_rounds` | 每 N 轮做一次 LLM 风险 JSON，N≥1 | 3 |
| `cooldown_minutes` | 同一 `(childId, category)` 合并冷却 | 30 |
| `min_level_to_notify` | 不低于该级别才写 outbox（1=高） | 2（示例） |
| `rule_pre_scan_enabled` | 是否在本机先跑关键词/规则 | true |

（级别数字：**1=最高严重**，与接口约定写死。）

### 2. `child_risk_rule`

| 字段 | 说明 |
|------|------|
| id | PK |
| name | 展示名 |
| rule_type | `KEYWORD` / `REGEX` |
| pattern | 关键词子串 或 正则（慎用性能） |
| level | 1-3 |
| category | 字符串，如 `self_harm` / `bully` / `distress` / `other` |
| sort_order | int |
| status | 0 禁用 1 启用 |

### 3. `child_risk_event`（每次「成案」一条，含去重后仍决定发出的一条）

| 字段 | 说明 |
|------|------|
| id | PK |
| child_id, device_id | 来自上报 |
| parent_user_id | **服务端**根据绑定反查，可冗余存储 |
| session_id / trace | 可空，便于对账 |
| level | 1-3 |
| category | string |
| source | `ZhibAN_JSON` / `RULE` / `MERGED` |
| need_alert | bool |
| reason_public | 给家长**摘要**的一小段（脱敏，避免贴原文） |
| dedupe_key | 如 `childId:category:yyyyMMddHH` 或 hash |
| status | `CREATED` / `SUPPRESSED_COOLDOWN` / `OUTBOX_QUEUED` / `NOTIFIED` / `FAILED` |
| create_time | |

**说明**：`SUPPRESSED_COOLDOWN` 时也可写一行做审计，或只打日志；二选一在实现时定。

### 4. `child_risk_outbox`（P1 可靠投递，便于以后换 MQ）

| 字段 | 说明 |
|------|------|
| id | PK |
| event_id | FK |
| channel | 本期固定 `MINI_APP` |
| payload | JSON，含 title、body 片段 |
| status | `PENDING` / `SUCCESS` / `FAILED` |
| attempts | int |
| next_retry_time | 失败重试用 |
| create_time, update_time | |

### 5. `parent_risk_notification`（家长拉取，类站内信）

| 字段 | 说明 |
|------|------|
| id | PK |
| parent_user_id | |
| child_id | |
| event_id | 关联 |
| title | 如「孩子对话风险提示」 |
| summary | 短摘要（来自 reason_public） |
| level | 1-3 |
| is_read | 0/1 |
| create_time | |

> **实现捷径**：若希望少一张表，可把 `parent_risk_notification` 与 outbox 成功后的展示合并，**outbox 成功后只插 notification**，event 只保留技术字段。

**Liquibase/Flyway**：在 `db/changelog` 新增 `2026xxxx_risk.sql` 并在 `db.changelog-master.yaml` **include**。

---

## 四、interface 设计（必须）

### 1. 内部（zhiban / 运维脚本）

- **`POST /config/child/risk-signal`**
- **Header**：`Authorization: Bearer {server.secret}`（与现有 `ServerSecret` / `config` 路由一致，按你项目里 **`ParentInternalController` 同鉴权** 方式挂在 `/config/...` 上）。

**Request Body（JSON）**：

```json
{
  "childId": 1,
  "deviceId": "string",
  "sessionId": "string",
  "level": 2,
  "category": "distress",
  "needAlert": true,
  "source": "ZhibAN_JSON",
  "reasonPublic": "检测到孩子情绪明显低落，请关注。",
  "rawConfidence": 0.85
}
```

**行为**：校验 `childId+device` 与库一致、算最终 level（与规则 merge）、**冷却**、写 event、**写 outbox**（若需通知）。

**Response**：`{ "eventId": 10001, "suppressed": false }` 等。

> **注意**：内部接口**不要**要求传完整儿童原话；如必须带，应配置项**默认关**，且入参单独字段、存储加密或仅存 hash。

### 2. 家长小程序（`parent-api`，Bearer 家长 token）

- **`GET /parent-api/risk-alerts?childId=&page=&pageSize=`** 列表  
- **`GET /parent-api/risk-alerts/unread-count?childId=`** 或全局  
- **`POST /parent-api/risk-alerts/{id}/read` 或 PUT 标记已读**  

权限：`parentUserId` 须能管该 `childId`（与影子任务、聊天同一套**绑定校验**）。

### 3. 智控台管理端（shiro 管理员，非 parent）

- **`GET/POST/PUT/DELETE /sys/child/risk-rule/...`** 或放在自定义模块，与现有 `SysUser` 权限继承  
- **`GET /sys/child/risk-event/page`** 只读列表（筛选时间、孩子、级别）  
- **配置项**：在「参数管理」增加上述 `child_risk_config` 的键值，**或** 专用配置页

（具体 URL 与现有 `manager-web` 路由保持一致即可。）

---

## 五、manager-api 服务内模块（建议包名）

```
xiaozhi.modules.risk
  entity / dao
  service ChildRiskService, ChildRiskRuleService, ParentRiskNotificationService
  controller
    ChildRiskInternalController   → /config/child/risk-signal
    ChildRiskRuleAdminController  → 管理端规则 CRUD
    ChildRiskEventAdminController → 只读事件
  task ChildRiskOutboxTask        → @Scheduled
```

- **去重/冷却**：  
  - **简单版**：`child_risk_event` 上查 `create_time` + `child_id` + `category` 在冷却窗口内则 `suppressed`；  
  - **优**：`Redis` `SETNX` key=`risk:cd:{childId}:{category}` TTL=冷却秒数。  

### Outbox 定时任务（P1）

- `@Scheduled(fixedDelayString = "${risk.outbox.poll-ms:2000}")`  
- 查 `PENDING` 且 `next_retry_time` <= now，**行锁/乐观锁**防并发。  
- 成功：`parent_risk_notification` insert，`outbox=SUCCESS`，`event.status=NOTIFIED`。  
- 失败：`attempts++`，`next_retry_time=now+退避`，超过上限标 `FAILED`。

---

## 六、zhiban-agent 改造（P3 + 与 P0/P1 对接）

### 1. 环境变量

- `MANAGER_API_BASE`  
- `MANAGER_API_SECRET`（= server.secret）  
- `CHILD_RISK_ENABLED=true`（总开关在服务端为准时，可双开关）  
- `CHILD_RISK_ASYNC=true`（固定 true）  
- `CHILD_RISK_EVAL_EVERY_N_ROUNDS=3`（可被服务端配置覆盖时，**首期可只信 env**，或启动时拉一次 config 接口）

### 2. 执行点（不阻塞主路径）

- 在 **`/api/chat` 与 `/api/chat/stream` 在拿到最终 `reply` 并即将返回**之后（流式在 **full_reply 拼完** 之后）：
  - 提交到 **`ThreadPoolExecutor`（单例、有界队列）** 或 `background task`：  
    - 计数器 `round_counter` per `session_id`（内存 dict + 过期淘汰）。  
    - 若 `round_counter % N == 0` 或**本轮**用户文本命中**本地从服务端拉取/缓存的** `risk_rule` 关键词：  
      - 调 **小模型 JSON** 风险（**独立 system prompt，temperature=0**）。  
    - 合并：规则 level 与 JSON level **取更严重者**（数字更小更严重，需统一约定表）。  
    - 若 `needAlert` 或规则已够级别：`httpx.post` 内部接口（短超时 3s，**失败只打日志**不抛给主线程）。  

### 3. 规则从哪来

- **MVP**：zhiban **启动时**或 **每 5 分钟** 拉 `GET /config/child/risk-rules` 内部只读（需 secret）**或** 规则硬编码一版，**上屏前以 DB 为准**。  
- **推荐 P3**：manager-api 提供 **`GET /config/child/risk-rules`（secret）** 返回启用规则列表；zhiban 缓存在内存。  

### 4. 说话人 gating

- 仅对 **`speaker_type` 为 `owner_child` 或 你们允许的「主孩子+unknown」** 跑风险（与影子任务 gating 对齐），**家长自己聊天**不跑儿童风险（或单走别类规则，本期可排除）。

### 5. 日志

- 异步任务内打：`session_id, child_id, level, source, eventId/suppressed`；**不**默认打**完整**用户原话。

---

## 七、智控台（manager-web）要做的事

1. **菜单**：「安全与风险」→ **风险事件（只读）**、**风险规则**、**基础参数**（或只用参数管理一个 JSON）。  
2. **风险规则页**：表格 + 新增/编辑/禁用；字段见 `child_risk_rule`。  
3. **事件只读页**：时间范围、孩子、级别筛选；**详情**不展示大段原文，仅 `reasonPublic` 与元数据。  
4. **总开关 + N + 冷却**：表单写 `sys_params` 或专用接口。  
5. **权限**：`risk:view`、`risk:rule:edit` 等（可与现有角色菜单绑定，最小实现：**admin 全量**也可）。

**若时间紧**：P0 可只用 **「参数管理」** 里手工加 `child.risk.*` 键 + **仅后端初始化 SQL 规则**；P3 规则页下一迭代补。

---

## 八、实施顺序（按依赖排序）

1. **DB 迁移**：表 `child_risk_rule`, `child_risk_event`, `child_risk_outbox`, `parent_risk_notification` + 索引（`parent_user_id+is_read+create_time`, `child_id+create_time`）。  
2. **配置**：`sys_params` 或表插入默认 `enabled=false`（上线后人工开）。  
3. **Service**：合并规则、**resolve parentUserId**（复用 `DeviceChildDao` + `ParentDeviceBindingDao`）。  
4. **内部 Controller**：`POST /config/child/risk-signal`，挂 **与 `/config/parent` 相同 secret 过滤器**（查 Shiro `filterMap` 里 `config` 路径是否已包 `/config/child/*`，否则加一条）。  
5. **Outbox 任务** + **notification 插入**。  
6. **家长 API**：list / unread / read。  
7. **管理端 API**：规则 CRUD、事件 page。  
8. **zhiban**：线程池、N 轮、规则缓存、JSON LLM、HTTP 上报。  
9. **联调**：内部接口手工 curl → DB 有 event/outbox/notification → 小程序拉列表。  
10. **开总开关** 灰度一名测试家长。

---

## 九、测试清单（必做）

- 总开关关：接口 200 但**不写** outbox 或**明确** no-op 策略。  
- 冷却内连发两条：第二条 `suppressed=true`，家长端**一条**通知。  
- 规则仅命中、不跑 LLM：仍上报且级别正确。  
- zhiban 主接口 **延迟** 与改造前**对比**无显著回退。  
- 无绑定家长 device：内部接口应 **4xx** 且**不落**敏感数据。

---

## 十、风险与后续

- **误报**：依赖运营调规则、调 `min_level`、看事件页。  
- **性能**：正则规则过多时加 **单机缓存** 与**条数上限**。  
- **后续 P2/P4**：outbox 的 `channel` 扩展 `SMS`/`VOICE`；`next_retry` 已够用；**再**接 MQ 仅替换调度器实现。

将本文配合「接口契约」给 Cursor 可分工：**后端（manager）** 与 **zhiban** 可并行，最后联调 `risk-signal`。
