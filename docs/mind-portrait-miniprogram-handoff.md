# 心绪陪伴 · 小程序开发对接手册

> **给小程序 Cursor 的一文档**：产品说明 + 页面结构 + 接口定义 + 渲染规则 + 交付清单。  
> 后端已实现（manager-api），按本文对接即可。

**免责声明（所有页面必须展示）**：基于日常对话观察，不构成医学或心理诊断。如有持续困扰，请咨询专业人士。

---

## 0. 你要做什么（一句话）

在**现有小程序**（首页 / 机器人 / 会话 / 我的）上接入「心绪陪伴」：

- **机器人 Tab**：展示孩子本周状态卡片（图一）
- **详情页**（二级）：仅状态详情，无家长建议（图二精简版）
- **会话 Tab**：周报卡片、即时提醒、给家长的话、陪伴建议（图四全部在这里）

**不要**做力导向「心绪星图」、不要调 `/graph` 接口。

---

## 1. 产品原则

| 位置 | 回答的问题 | 内容 |
|------|-----------|------|
| 机器人 Tab | What（孩子怎么样） | 整体状态 + 4 面向 + 条件按钮 |
| 详情页 | What+（展开看） | 7 天趋势 + 四面向 detail |
| 会话 Tab | So what + 怎么做 | 建议、共情、呼吸、追问 |

**按钮规则（图一底部）**

- `showActions === false` → **不渲染**「去会话看陪伴建议」「查看详情」
- `showActions === true` → 展示两个按钮

孩子整体 `stable` 且四面向均无 `watch` 时，后端返回 `showActions: false`。

---

## 2. 环境与鉴权

| 项 | 值 |
|----|-----|
| Base URL | 与学情相同，如 `https://<域名>/` 或 `http://127.0.0.1:8002` |
| 鉴权 | 家长 Bearer Token，与 `/parent-api/learning/*` 相同 |

```http
Authorization: Bearer <parent_access_token>
Content-Type: application/json
```

统一响应：

```json
{ "code": 0, "msg": "success", "data": { ... } }
```

`code !== 0` 时 toast `msg`。

---

## 3. 页面结构

### 3.1 机器人 Tab

在**现有设备卡片下方**新增「心绪观察 · 本周」卡片：

```
机器人 Tab
├── 设备卡片（已有）
├── 【新增】心绪观察卡片 ← GET /wellness-summary
│   ├── 标题：心绪观察 · 本周
│   ├── 整体：overallText（前加状态圆点）
│   ├── 摘要：summary
│   ├── chips：chips[]
│   ├── 四面向列表：dimensions[]
│   └── [if showActions] 两个按钮
├── 成长星图入口（已有，不动）
└── 免责一行
```

**按钮交互**

| 按钮 | 条件 | 行为 |
|------|------|------|
| 去会话看陪伴建议 | `showActions` | `switchTab` 到会话 |
| 查看详情 | `showActions` | 打开详情页（原生或 web-view） |

详情页路由建议：`/pages/mind-wellness/detail?childId=xxx`  
或 web-view：`/static/mind-portrait/wellness.html?childId=xxx`（待 H5 部署）

### 3.2 详情页（二级）

**只展示状态，不含任何家长建议。**

```
详情页
├── 顶栏：childName · weekStart–weekEnd
├── 整体状态 hero（overallText + summary + chips）
├── 近 7 天趋势（weekTrend）
├── 四面向 grid（dimensions，点击展开 detail）
└── 静态块：「何时寻求专业帮助」（写死文案即可）
```

**不要放**：陪伴建议、给家长的你、呼吸练习、力导向图、Tab 切换。

数据来源：`GET /wellness-summary?childId=`（与机器人 Tab 同接口）。

### 3.3 会话 Tab

用**消息流 + 卡片消息**（复用现有聊天气泡组件）：

| 消息类型 | 渲染 | 数据来源 |
|---------|------|---------|
| `mind_weekly_card` | 周报卡片 | `GET /weekly-digest` |
| `mind_instant_card` | 即时提醒卡片 | `GET /notifications` 中 `cardType=mind_instant_card` |
| `mind_parent_support` | 纯文本共情 | `weekly-digest.parentSupport` |
| `mind_parent_tips` | 编号列表 + 可选呼吸 | `weekly-digest.childTips` |
| `text` | 普通气泡 | 用户追问 / 小智回复 |

**周报卡片结构**

```
┌─────────────────────────────────┐
│ 💚 本周心绪周报 · 给{childName}家长 │
│ title（如：整体平稳，「面对压力时」值得多陪） │
│ summary                         │
│ • parentActions[0]              │
│ • parentActions[1]              │
├──────────────┬──────────────────┤
│ 查看机器人 Tab │ 30 秒安顿自己      │
└──────────────┴──────────────────┘
```

**即时提醒卡片结构**

```
┌─────────────────────────────────┐
│ 🔔 即时提醒                      │
│ title                           │
│ summary                         │
├──────────────┬──────────────────┤
│ 看孩子状态    │ 知道了            │
└──────────────┴──────────────────┘
```

- 「知道了」→ `POST /notifications/{id}/read`
- 「看孩子状态 / 查看机器人 Tab」→ 切到机器人 Tab
- 「30 秒安顿自己」→ 本地呼吸动画 / toast（3 次呼吸）

**快捷 chips（客户端写死）**

- `最近心绪怎么样`
- `我该怎么少焦虑`
- `何时寻专业帮助`

追问可走现有小智对话；上下文可注入 wellness-summary 的 summary。

**推送节奏**

- 周报：每周日或首次进入会话时，若本地无本周 digest 则拉取并插入一条 `mind_weekly_card`
- 即时：`notifications` 有未读 `instant` 时插入；频控后端已做（默认 2 次/周）

---

## 4. API 接口

### 4.1 心绪概览（主接口）

```http
GET /parent-api/mind-portrait/wellness-summary?childId={device_child.id}
```

**Response `data`**

```typescript
interface MindWellnessSummaryVO {
  childId: number;
  childName: string;
  observeDays: number;
  weekStart: string;      // "2026-08-25"
  weekEnd: string;

  overallLevel: 'stable' | 'watch' | 'concern';
  overallText: string;    // 「整体平稳，略有波动」
  summary: string;

  chips: { text: string; type: 'ok' | 'watch' | 'neutral' }[];

  dimensions: {
    code: 'emotion' | 'stress' | 'relation' | 'self';
    name: string;         // 「面对压力时」
    icon: string;         // emoji，可直接展示
    status: 'ok' | 'watch' | 'observe';
    statusText: string;   // 「平稳」「需留意」「观察中」
    hint: string;
    detail: string;       // 详情页展开用
  }[];

  weekTrend: {
    date: string;
    dayLabel: string;     // 一…日
    level: 'ok' | 'watch' | 'neutral';
  }[];

  showActions: boolean;
  actions?: {
    chat: { label: string };
    detail: { label: string };
  };
}
```

**前端渲染要点**

```javascript
// 图一按钮
if (data.showActions && data.actions) {
  // 渲染 data.actions.chat.label / data.actions.detail.label
}

// chip 颜色
const chipClass = { ok: 'green', watch: 'orange', neutral: 'gray' };

// 面向 status 颜色
const statusClass = { ok: 'green', watch: 'orange', observe: 'gray' };

// 7 天趋势柱色
const trendClass = { ok: 'teal', watch: 'orange', neutral: 'light-teal' };
```

**示例（有需留意 → 显示按钮）**

```json
{
  "childId": 1,
  "childName": "小亮",
  "observeDays": 14,
  "weekStart": "2026-08-25",
  "weekEnd": "2026-08-31",
  "overallLevel": "watch",
  "overallText": "整体平稳，略有波动",
  "summary": "基于日常对话：小亮多数时候情绪表达自然；压力相关话题本周出现 2 次信号…",
  "chips": [
    { "text": "观察 14 天", "type": "neutral" },
    { "text": "压力需留意", "type": "watch" },
    { "text": "比上周更稳定", "type": "ok" }
  ],
  "dimensions": [
    {
      "code": "emotion",
      "name": "情绪与状态",
      "icon": "🌤",
      "status": "ok",
      "statusText": "平稳",
      "hint": "表达感受自然",
      "detail": "本周在「情绪表达、状态稳定」相关表达整体自然…"
    },
    {
      "code": "stress",
      "name": "面对压力时",
      "icon": "🌊",
      "status": "watch",
      "statusText": "需留意",
      "hint": "相关话题本周 2 次",
      "detail": "本周在「压力恢复」相关对话中出现 6 条观测信号…"
    }
  ],
  "weekTrend": [
    { "date": "2026-08-25", "dayLabel": "一", "level": "ok" },
    { "date": "2026-08-28", "dayLabel": "四", "level": "watch" }
  ],
  "showActions": true,
  "actions": {
    "chat": { "label": "去会话看陪伴建议" },
    "detail": { "label": "查看详情" }
  }
}
```

**示例（整体平稳 → 无按钮）**

```json
{
  "overallLevel": "stable",
  "overallText": "整体平稳",
  "showActions": false
}
```

---

### 4.2 周报（会话卡片）

```http
GET /parent-api/mind-portrait/weekly-digest?childId={id}&weekStart=2026-08-25
```

`weekStart` 可选，默认本周一。

```typescript
interface MindWeeklyDigestVO {
  weekStart: string;
  weekEnd: string;
  newStrongCount: number;
  topHighlights: { nodeCode, label, shortDesc, strength }[];

  title: string;           // 卡片标题
  summary: string;         // 卡片正文
  parentActions: string[]; // bullet 行动建议
  parentSupport: string;   // 共情短句 → mind_parent_support 消息
  childTips: string[];     // 陪伴动作 → mind_parent_tips 消息

  parentTip: string;       // 兼容旧字段，等同 parentSupport
}
```

**会话插入顺序建议**

1. `mind_weekly_card`（用 title / summary / parentActions）
2. `mind_parent_support`（parentSupport 纯文本）
3. （可选）`mind_parent_tips`（childTips 列表，可折叠）

---

### 4.3 通知列表（即时提醒卡片）

```http
GET /parent-api/mind-portrait/notifications?childId={id}&page=1&pageSize=20
```

```typescript
interface MindNotificationPageVO {
  unreadCount: number;
  items: {
    id: number;
    notifyType: 'instant' | 'weekly';
    cardType: 'mind_instant_card' | 'mind_weekly_card';
    title: string;
    summary: string;
    nodeCode: string;
    isRead: 0 | 1;
    createTime: string;
  }[];
}
```

标记已读：

```http
POST /parent-api/mind-portrait/notifications/{id}/read
```

---

### 4.4 通知设置

```http
POST /parent-api/mind-portrait/settings
Content-Type: application/json

{
  "childId": 123,
  "instantNotifyEnabled": true,
  "weeklyDigestEnabled": true
}
```

---

### 4.5 已废弃（不要对接）

```http
GET /parent-api/mind-portrait/graph?childId=
```

仅供教研/内部，**小程序不要调用**。

---

## 5. 对接流程（推荐顺序）

1. **机器人 Tab**：进入页 / 切换孩子时调 `wellness-summary`，渲染卡片；根据 `showActions` 控制按钮
2. **详情页**：同接口，渲染 weekTrend + dimensions.detail
3. **会话 Tab**：
   - onShow 调 `weekly-digest` → 若无本周消息则插入 weekly_card + parent_support
   - 调 `notifications` → 未读 instant 插入 instant_card
4. **我的 / 设置**：`POST /settings` 开关

---

## 6. 错误处理

| msg 关键词 | UI 处理 |
|-----------|---------|
| 暂无已发布的心绪图谱模板 | 卡片空态：「观察数据积累中，请继续让孩子与小智聊天」 |
| 孩子不存在 / 无权限 | toast + 返回 |

`wellness-summary` 在模板未发布时返回 `showActions: false` 的空概览，不抛错。

---

## 7. 样式约束

- **复用现有小程序**组件、配色、圆角，不要另起视觉系统
- 参考 Demo（仅交互参考，样式跟项目）：
  - `docs/mind-portrait-split-demo.html`
  - `docs/mind-portrait-wellness-demo.html`
- chip / 状态色：`ok` 绿、`watch` 橙、`neutral` 灰

---

## 8. 交付清单

- [ ] 机器人 Tab：`wellness-summary` 卡片 + `showActions` 条件按钮
- [ ] 详情页：趋势 + 四面向 + 专业求助说明（无建议）
- [ ] 会话：`mind_weekly_card` / `mind_instant_card` / `mind_parent_support` 消息组件
- [ ] 通知已读 + 设置页开关
- [ ] 快捷 chips + 与机器人 Tab 互跳
- [ ] 全页免责声明
- [ ] **不做** graph 力导向 / 心绪星图 Tab

---

## 9. Cursor 提示词（可直接粘贴）

```
你是微信小程序前端，对接「心绪陪伴」功能。

请先阅读项目内文档：docs/mind-portrait-miniprogram-handoff.md（产品+接口+页面结构）。

任务：
1. 机器人 Tab：GET /parent-api/mind-portrait/wellness-summary?childId= 渲染心绪卡片
   - showActions=false 时不展示底部两个按钮
   - showActions=true 时：左按钮切会话 Tab，右按钮打开详情页
2. 详情页：同接口，只展示状态（weekTrend + dimensions），不要任何家长建议
3. 会话 Tab：
   - GET /weekly-digest 渲染 mind_weekly_card + parentSupport + childTips
   - GET /notifications 渲染 mind_instant_card，知道了调 POST .../read
4. 复用现有 Tab/聊天/卡片样式，不要力导向图，不要调 /graph

鉴权与学情接口相同。childId 用 device_child.id。
```

---

## 10. 后端实现位置（供联调）

| 项 | 路径 |
|----|------|
| 概览接口 | `MindPortraitParentController` → `/wellness-summary` |
| 聚合逻辑 | `MindWellnessSupport.java` |
| VO | `MindWellnessSummaryVO.java` |
| 周报扩展 | `MindWeeklyDigestVO.java` + `MindPortraitServiceImpl.enrichWeeklyDigestForChat` |

部署 manager-api 后即可联调。
