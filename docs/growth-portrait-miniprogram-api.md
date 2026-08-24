# 成长星图 · 小程序对接文档（Phase 1–3）

> 给小程序前端同学。UI 原型见 `xiaozhi-esp32-server/docs/growth-portrait-miniprogram-prototype.html`（v0.6）。  
> 后端已实现：模板发版、图谱渲染 API、证据写入、家长通知、周报。

---

## 1. 你要做什么

1. **把 HTML 原型改成小程序页面**（D3 可换 canvas / 小程序 canvas / echarts 力导向，字段对齐即可）
2. **调 parent-api 拿图谱数据**，不要写死 JS 里的 `AGE_DIMS` / `SUB_TEMPLATES`
3. **Tab 结构**：与「掌握地图」并列 — `学习` | `成长星图`
4. **通知**：拉取 `/notifications`，未读角标；周报入口调 `/weekly-digest`

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

### 3.1 获取成长星图（主接口）

```http
GET /parent-api/growth-portrait/graph?childId={device_child.id}
```

**Query**

| 参数 | 必填 | 说明 |
|------|------|------|
| childId | 是 | `device_child` 表主键，与学情接口一致 |

**Response `data` 结构**

```typescript
interface GrowthGraphVO {
  releaseId: number;
  ageBand: 'preschool' | 'lower' | 'upper' | 'middle';
  strongCount: number;
  center: {
    label: string;        // 孩子昵称
    shortDesc: string;    // "成长枢纽"
    avatarUrl: string;    // 头像 URL，可为空
  };
  rules: {
    observeDays: number;      // 观测周期文案用
    weeklyInstantCap: number; // 每周即时推送上限，默认 2
  };
  nodes: GrowthNodeVO[];
  links: { source: string; target: string; strength: number }[];
}

interface GrowthNodeVO {
  id: string;              // node code，唯一
  type: 'hub' | 'sub' | 'signal';
  label: string;
  shortLabel: string;
  shortDesc: string;       // 节点下方副标题 — 解决「没描述」
  cluster: string;         // 颜色分组：logic/create/study/...
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
| locked | 证据 < 2，灰点 |
| collecting | 收集中，虚线环 |
| visible | 已显现 |
| strong | 强烈亮点（可触发家长通知） |

**重要**：`state === 'strong'` 但 `visualTier` 可能低于父节点 —— 这是产品设计（子节点业务上亮，视觉上不超过父节点）。

**颜色表**（与原型一致，按 `cluster` 映射）：

```javascript
const CLUSTER_COLORS = {
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
GET /parent-api/growth-portrait/notifications?childId={id}&page=1&pageSize=20
```

```typescript
interface GrowthNotificationPageVO {
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
POST /parent-api/growth-portrait/notifications/{id}/read
```

---

### 3.3 周报（Phase 3）

```http
GET /parent-api/growth-portrait/weekly-digest?childId={id}&weekStart=2026-08-18
```

`weekStart` 可选，默认本周一。

```typescript
interface GrowthWeeklyDigestVO {
  weekStart: string;
  weekEnd: string;
  newStrongCount: number;
  topHighlights: { nodeCode, label, shortDesc, strength }[];
  parentTip: string;  // 亲子建议一句
}
```

---

### 3.4 通知偏好设置（Phase 3）

```http
POST /parent-api/growth-portrait/settings
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
POST /config/growth-portrait/evidence
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

**档案年级 Picker** 请使用 `GET /parent-api/learning/meta/profile-options` 返回的 `grades`，首项为「幼小衔接 3-6岁」（`value: 0`），保存时写 `currentGrade: 0` 即可对应成长星图 **幼儿 3–6 岁** 区域。

小程序 **无需传 ageBand**，只传 `childId`。

---

## 6. 页面结构建议

```
成长 Tab
├── 顶栏：孩子名 · 观测 N 天 · 强烈亮点 {strongCount}
├── 模式切换（可选）：全部维度 | 筛选 chip（cluster）
├── 力导向星图（graph 区域）
├── 点击节点 → 底部抽屉：label / state / evidence / suggest
├── 帮助按钮：四层结构说明
└── 通知 toast：有新 instant 通知时展示（也可只做消息中心）
```

与掌握地图 **不要共用** 路由/state，只共用 `childId` 和家长 token。

---

## 7. 联调步骤

1. 教研 admin 登录 → `teaching-web/growth-admin.html` → 四个 age_band 各 **提交审核 → 通过**
2. manager-api 跑 Liquibase 迁移（含 `gp_*` 表）
3. 小程序带 parent token 调 `GET .../graph?childId=`
4. 手动灌证据测试（可选）：

```bash
curl -X POST 'http://127.0.0.1:8002/config/growth-portrait/evidence' \
  -H 'Authorization: Bearer <SERVER_SECRET>' \
  -H 'Content-Type: application/json' \
  -d '{"childId":1,"text":"找反例 主动表现","sourceType":"manual"}'
```

5. 再拉 graph，看节点 `evidenceCount` / `state` / `visualTier` 变化
6. 调 notifications 看是否生成 instant 通知

---

## 8. 错误码

| 场景 | msg 示例 |
|------|---------|
| 模板未发布 | `暂无已发布的成长星图模板：upper` |
| 无权限 | 家长 token 无效 / 未绑定设备 |
| child 不存在 | `孩子不存在` |

---

## 9. 原型 → API 字段对照

| 原型 mock | API 字段 |
|-----------|---------|
| `buildGraph()` nodes | `data.nodes` |
| links | `data.links` |
| center 头像 | `data.center.avatarUrl` |
| `visualIntensity` / `visualTier` | 同名，后端已算 |
| `LIGHT_STATES` | `node.state` |
| 家长通知 demo | `notifications` 接口 |

**删除原型里所有** `AGE_DIMS`、`SUB_TEMPLATES`、`seededRandom` —— 改为 API 驱动。

---

## 10. 交付清单

- [ ] 成长 Tab 页面 + 路由
- [ ] graph 渲染（力导向 + 层级光效）
- [ ] 节点详情抽屉
- [ ] 通知列表 + 未读角标
- [ ] 周报页或弹层
- [ ] 设置页：即时推送 / 周报开关
- [ ] 空态：模板未发布时的友好提示

---

## 12. 黑屏排查（有节点数、无图形）

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
你是微信小程序前端，对接「成长星图」功能。

参考 UI 原型：docs/growth-portrait-miniprogram-prototype.html（v0.6 视觉规则）
参考接口文档：docs/growth-portrait-miniprogram-api.md

任务：
1. 新建「成长星图」Tab，与掌握地图并列
2. GET /parent-api/growth-portrait/graph?childId= 拉取 nodes/links/center
3. 按 visualTier 渲染光效，按 cluster 上色，中心显示 avatarUrl
4. 节点点击展示 state/evidence/suggest
5. GET /notifications 做消息中心；POST /settings 做通知开关
6. GET /weekly-digest 做周报

约束：
- 不要写死维度/子能力数据，全部来自 API
- state 与 visualTier 分离：strong 节点不一定 visualTier=high
- 使用现有家长 login token，与 /parent-api/learning 相同鉴权方式
```
