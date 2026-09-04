# 心绪陪伴 · 小程序对接文档

> **产品定位（v1）**：家长看孩子**当前状态是否健康**（机器人 Tab），在**会话**里收陪伴建议与减焦虑支持。  
> **不是**全屏力导向「心绪星图」——力导向 graph 仅保留后端/教研，**家长端 UI 已下线**。  
> H5 参考：`docs/mind-portrait-wellness-demo.html`（详情页）、`docs/mind-portrait-split-demo.html`（Tab+会话）  
> 后端：`mp_*` 表 + `/parent-api/mind-portrait/*`

**免责声明**：页面需展示「基于对话观察，不构成医学或心理诊断」。

---

## 1. 信息架构

| 位置 | 内容 | 数据来源 |
|------|------|----------|
| **机器人 Tab** | 心绪观察卡片（图一）：整体状态 + 4 面向 + 可选按钮 | `GET /wellness-summary` |
| **详情页**（二级） | 本周心绪：7 天趋势 + 四面向展开 + 专业求助说明 | 同上 `detail` 字段或同接口 |
| **会话 Tab** | 周报卡片、即时提醒、给家长的话、陪伴建议、呼吸练习 | `notifications` + `weekly-digest` + 小智对话 |

**按钮显示规则（图一底部）**

- `showActions === false`（孩子整体正常）：**不展示**「去会话看陪伴建议」「查看详情」
- `showActions === true`：展示两个按钮；左→切会话 Tab，右→打开详情页

---

## 2. 环境与鉴权

| 服务 | 默认地址 | 鉴权 |
|------|---------|------|
| manager-api | `https://<你的域名>/` 或本地 `http://127.0.0.1:8002` | 家长端 Bearer Token |

小程序登录后已有 **parent token**（与学情 `/parent-api/learning/*` 相同），请求头：

```http
Authorization: Bearer <parent_access_token>
Content-Type: application/json
```

统一响应：

```json
{ "code": 0, "msg": "success", "data": { ... } }
```

`code !== 0` 时读 `msg`  toast。

---

## 3. 核心 API

### 3.0 心绪概览（家长端主接口 · 待实现）

机器人 Tab 卡片 + 详情页均用此接口。**不要**对家长端暴露 `/graph`。

```http
GET /parent-api/mind-portrait/wellness-summary?childId={device_child.id}
```

**Response `data`**

```typescript
interface MindWellnessSummaryVO {
  childId: number;
  childName: string;
  observeDays: number;
  weekStart: string;   // ISO date
  weekEnd: string;

  /** 整体档位，驱动 UI */
  overallLevel: 'stable' | 'watch' | 'concern';
  overallText: string; // 「整体平稳，略有波动」
  summary: string;

  chips: { text: string; type: 'ok' | 'watch' | 'neutral' }[];

  dimensions: {
    code: string;       // hub nodeCode，如 stress / emotion
    name: string;       // 「面对压力时」
    status: 'ok' | 'watch' | 'observe';
    statusText: string; // 「平稳」「需留意」「观察中」
    hint: string;       // 卡片一行说明
    detail: string;     // 详情页展开段落
  }[];

  weekTrend: {
    date: string;       // YYYY-MM-DD
    dayLabel: string;   // 一…日
    level: 'ok' | 'watch' | 'neutral';
  }[];

  /** 图一底部按钮：后端算好，前端只读 */
  showActions: boolean;
  actions?: {
    chat: { label: string };   // 「去会话看陪伴建议」
    detail: { label: string }; // 「查看详情」
  };
}
```

**`showActions` 计算规则（后端实现）**

```java
boolean anyWatch = dimensions.stream().anyMatch(d -> "watch".equals(d.getStatus()));
boolean showActions = !"stable".equals(overallLevel) || anyWatch;
// stable 且四面向均无 watch → false，图一不展示按钮
// 任一 watch 或 overallLevel 为 watch/concern → true
```

**`overallLevel` 聚合规则（后端从 mp_* hub 节点推导）**

| 条件 | overallLevel |
|------|----------------|
| 任一 hub `state=strong` 且 cluster 属压力/情绪负向 | `concern` |
| 任一 hub 对应维度 `status=watch` | `watch` |
| 其余 | `stable` |

Hub → 四面向映射（按 age_band 模板 `node_type=hub` 的 `sort_order` 前 4 个，或固定 cluster 分组）。

**详情页与图一**：同接口；详情页**不返回** `parentTips` / `childTips`（那些进会话）。

---

### 3.1 心绪图谱 graph（内部/教研 · 家长端 UI 已下线）

```http
GET /parent-api/mind-portrait/graph?childId={device_child.id}
```

**Query**

| 参数 | 必填 | 说明 |
|------|------|------|
| childId | 是 | `device_child` 表主键，与学情接口一致 |

**Response `data` 结构**

```typescript
interface MindGraphVO {
  releaseId: number;
  ageBand: 'preschool' | 'lower' | 'upper' | 'middle';
  strongCount: number;
  center: {
    label: string;        // 孩子昵称
    shortDesc: string;    // "观察枢纽"
    avatarUrl: string;    // 头像 URL，可为空
  };
  rules: {
    observeDays: number;      // 观测周期文案用
    weeklyInstantCap: number; // 每周即时推送上限，默认 2
  };
  nodes: MindNodeVO[];
  links: { source: string; target: string; strength: number }[];
}

interface MindNodeVO {
  id: string;              // node code，唯一
  type: 'hub' | 'sub' | 'signal';
  label: string;
  shortLabel: string;
  shortDesc: string;       // 节点下方副标题 — 解决「没描述」
  cluster: string;         // 颜色分组：express/recover/self/peer/parent/stable/...
  level: 1 | 2 | 3;
  strength: number;        // 0-100
  evidenceCount: number;
  requiredCount: number;
  state: 'locked' | 'collecting' | 'visible' | 'strong';
  visualIntensity: number; // 0-1，后端已算好层级光效
  visualTier: 'none' | 'low' | 'mid' | 'high';
  parentHub?: string;
  parentSub?: string;
  evidence: string;        // 详情页文案
  suggest: string;
}
```

**渲染约定（与原型 v0.6 对齐）**

| visualTier | 表现 |
|------------|------|
| high | 柔和脉冲光晕（仅 hub 为主） |
| mid | 慢速单层光晕 |
| low | 静态淡光 |
| none | 无光晕 |

| state | 业务含义 |
|-------|---------|
| locked | 证据 < 2 条，灰点 |
| collecting | 已有 ≥2 条证据，未达 `requiredCount` |
| visible | 证据 ≥ `requiredCount` |
| strong | 证据 ≥ `requiredCount` 且（超额 ≥120% 或 `strength` ≥ 强阈值） |

**Hub 节点**：`state` 由子维度汇聚——汇总证据 ≥ 目标即「已显现」；有子维度「积极信号」且汇总证据达标则为「积极信号」。

**进度展示**：`evidenceCount` 可超过 `requiredCount`；前端应显示「已达标」而非裸 `4/3`。

**重要**：`state === 'strong'` 但 `visualTier` 可能低于父节点 —— 这是产品设计（子节点业务上亮，视觉上不超过父节点）。

**颜色表**（与原型一致，按 `cluster` 映射）：

```javascript
const CLUSTER_COLORS = {
  express:'#9254de', recover:'#597ef7', self:'#36cfc9', peer:'#ffc53d', parent:'#ff85c0', stable:'#95de64',
  logic:'#ffb020', create:'#ff7a45', study:'#597ef7', lang:'#9254de',
  social:'#36cfc9', curious:'#ffc53d', interest:'#ff85c0', meta:'#95de64',
  motor:'#69c0ff', fine:'#b37feb', math:'#ffd666', focus:'#5cdbd3',
  read:'#9e86ff', emotion:'#ff9c6e'
};
```

**节点半径建议**

```javascript
function nodeRadius(n) {
  if (n.type === 'hub') return 13 + n.strength / 12;
  if (n.type === 'sub') return 5 + n.strength / 16;
  return 4 + n.strength / 18;
}
```

**中心节点**：用 `center.avatarUrl` + `center.label`，无头像时用原型里的 SVG 动漫占位。

---

### 3.2 亮点通知列表（Phase 3）

```http
GET /parent-api/mind-portrait/notifications?childId={id}&page=1&pageSize=20
```

```typescript
interface MindNotificationPageVO {
  unreadCount: number;
  items: {
    id: number;
    notifyType: 'instant' | 'weekly';
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

### 3.3 周报（推送到会话 · 卡片消息）

```http
GET /parent-api/mind-portrait/weekly-digest?childId={id}&weekStart=2026-08-18
```

`weekStart` 可选，默认本周一。

```typescript
interface MindWeeklyDigestVO {
  weekStart: string;
  weekEnd: string;
  newStrongCount: number;
  topHighlights: { nodeCode, label, shortDesc, strength }[];
  /** 会话卡片标题区 */
  title: string;           // 「整体平稳，「面对压力时」值得多陪」
  summary: string;
  /** 给家长的行动建议 bullet（会话卡片内，不进详情页） */
  parentActions: string[];
  /** 共情短句（会话独立消息或卡片内） */
  parentSupport: string;   // 「不等于你的教育出了问题…」
  childTips: string[];       // 陪伴孩子的 3 个小动作
}
```

会话侧渲染为 **weekly_card** 消息类型（见 §6.2）。

---

### 3.4 通知偏好设置（Phase 3）

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

## 4. 设备/对话侧证据（你不用调，了解即可）

对话结束后 **xiaozhi-server / manager-api** 内部调用：

```http
POST /config/mind-portrait/evidence
Authorization: Bearer <server_secret>

{
  "childId": 123,
  "sourceType": "conversation",
  "sourceRef": "session-uuid",
  "text": "孩子在讨论中主动找了反例",
  "confidence": 80
}
```

后端用预置 `match_hints` 匹配 signal 节点 → 写证据 → 重算状态 → 首次 strong 推家长（每周 ≤2）。

---

## 5. 年龄段匹配

后端按 `device_child` 自动解析 `ageBand`：

1. 优先 `ageStage`（含「幼小衔接」「3-6」等 → preschool）
2. 否则 `currentGrade`（**0 或负数 → preschool**；1–2 → lower；3–6 → upper；7+ → middle）
3. 否则 `birthday` 推算

**档案年级 Picker** 请使用 `GET /parent-api/learning/meta/profile-options` 返回的 `grades`，首项为「幼小衔接 3-6岁」（`value: 0`），保存时写 `currentGrade: 0` 即可对应心绪图谱 **幼儿 3–6 岁** 区域。

小程序 **无需传 ageBand**，只传 `childId`。

---

## 6. 页面结构

### 6.1 机器人 Tab

```
机器人 Tab
├── 设备卡片（已有）
├── 心绪观察 · 本周（wellness-summary）
│   ├── 整体状态 + 摘要 + chips
│   ├── 四面向列表
│   └── [showActions] 去会话 | 查看详情
├── 成长星图入口（已有，与心绪并列）
└── 免责一行
```

### 6.2 会话 Tab · 消息类型

| msgType | 展示 | 来源 |
|---------|------|------|
| `mind_weekly_card` | 周报卡片 + 「查看机器人 Tab」「30 秒安顿自己」 | weekly-digest，周日或首次打开推送 |
| `mind_instant_card` | 即时提醒 + 「看孩子状态」「知道了」 | notifications `instant` |
| `mind_parent_support` | 共情短文本（图四「给家长的你」首条） | weekly-digest.parentSupport 或模板 |
| `mind_parent_tips` | 编号列表 3 条 + 可选呼吸按钮 | weekly-digest.parentTips |
| `text` | 小智自由回复 | 用户追问 / LLM |

**图四内容全部在会话，不进详情页。**

即时提醒频控：沿用 `rules.weeklyInstantCap`（默认 2）；`知道了` → `POST .../notifications/{id}/read`。

快捷 chips（客户端写死）：`最近心绪怎么样` · `我该怎么少焦虑` · `何时寻专业帮助` —— 可走小智 RAG，上下文注入 wellness-summary。

### 6.3 详情页（二级）

```
详情页（web-view 或原生页）
├── 顶栏：孩子名 · 周区间
├── 整体状态 hero
├── 近 7 天趋势
├── 四面向 grid + 点击展开 detail
└── 何时寻求专业帮助（静态文案）
```

**不含**：Tab 切换、心谱力导向图、陪伴建议、给家长的你、呼吸练习。

与掌握地图 **不要共用** 路由/state，只共用 `childId` 和家长 token。

---

## 7. 联调步骤

1. 教研 admin → `teaching-web/mind-admin.html` → 发布模板
2. manager-api 跑迁移（`mp_*`）
3. 小程序调 `GET .../wellness-summary?childId=` 渲染机器人 Tab
4. 会话拉 `weekly-digest` + `notifications` 渲染卡片
5. （可选）灌证据测 watch 档位：

```bash
curl -X POST 'http://127.0.0.1:8002/config/mind-portrait/evidence' \
  -H 'Authorization: Bearer <SERVER_SECRET>' \
  -H 'Content-Type: application/json' \
  -d '{"childId":1,"text":"找反例 主动表现","sourceType":"manual"}'
```

5. 调 wellness-summary 看 `showActions` / `overallLevel`
6. 调 notifications 看 instant 卡片

---

## 8. 错误码

| 场景 | msg 示例 |
|------|---------|
| 模板未发布 | `暂无已发布的心绪图谱模板：upper` |
| 无权限 | 家长 token 无效 / 未绑定设备 |
| child 不存在 | `孩子不存在` |

---

## 9. 字段对照

| UI | API |
|----|-----|
| 图一卡片 | `wellness-summary` |
| 图二详情 | 同上，不含 advice 字段 |
| 图四会话 | `weekly-digest` + `notifications` + 对话 |
| ~~力导向心谱~~ | ~~`/graph`~~ 家长端不用 |

---

## 10. 交付清单

- [ ] `GET /wellness-summary` 后端 VO + 聚合逻辑
- [ ] 机器人 Tab 心绪卡片 + `showActions` 条件按钮
- [ ] 详情页（仅状态，无建议）
- [ ] 会话：weekly_card / instant_card / parent_support 消息组件
- [ ] 通知已读 + 设置页开关
- [ ] ~~graph 力导向~~（不做家长端）
- [ ] 空态：观察天数不足 / 模板未发布

---

## 11. graph 黑屏排查（仅教研/内部 H5）

若 UI 已显示「201 节点」但画布全黑 → **服务端数据 OK，客户端绘制失败**。

常见原因：

1. **未跑力导向**且未使用 API 返回的 `node.x / node.y`（0~1 归一化坐标）
2. **缺少 center 节点**：`nodes` 里需有 `id:"center", type:"center"`（新版 API 已自动注入）
3. **连线 id 未解析**：`links[].source/target` 是字符串，需映射到 node.id
4. **canvas 高度为 0** 或未在 `onReady` 后初始化
5. **颜色与背景同色**（如 stroke/fill 透明）

小程序绘制示例：

```javascript
const W = canvasWidth, H = canvasHeight;
data.nodes.forEach(n => {
  if (n.x == null || n.y == null) return;
  const px = n.x * W, py = n.y * H;
  ctx.beginPath();
  ctx.arc(px, py, n.type === 'hub' ? 8 : 4, 0, Math.PI * 2);
  ctx.fillStyle = CLUSTER_COLORS[n.cluster] || '#597ef7';
  ctx.fill();
});
```

```
你是微信小程序前端，对接「心绪陪伴」功能（不是心绪星图）。

参考 Demo：
- docs/mind-portrait-split-demo.html（机器人 Tab + 会话）
- docs/mind-portrait-wellness-demo.html（详情页，仅状态）

接口文档：docs/mind-portrait-miniprogram-api.md

任务：
1. 机器人 Tab：GET /wellness-summary 渲染心绪卡片；showActions=false 时隐藏底部两按钮
2. 详情页：同接口，展示趋势+四面向，不含任何家长建议
3. 会话 Tab：weekly-digest + notifications 渲染卡片消息；图四内容全在这里
4. 快捷提问 chips + 小智对话
5. POST /settings 通知开关

约束：
- 不要对接 /graph，不要力导向图
- 详情页与机器人 Tab 只展示孩子状态（What），建议只在会话（So what）
- 使用现有家长 token
```
