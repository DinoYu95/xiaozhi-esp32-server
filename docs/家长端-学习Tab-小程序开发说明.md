# 家长端 ·「学习」Tab 小程序开发说明

面向 **微信小程序** 对接文档：在 Tab「学习」中展示孩子掌握情况、掌握地图、辅导记录。与 [`家长端-学习洞察-小程序接口.md`](./家长端-学习洞察-小程序接口.md) 共用同一套鉴权与 `childId` 规则。

---

## 0. 环境与鉴权

| 项 | 值 |
|----|-----|
| 服务 | `manager-api` |
| Context | 默认 `/xiaozhi` |
| 业务前缀 | `/parent-api` |
| 学习模块 | `/parent-api/learning/*` |
| 影子任务 | `/parent-api/shadow-mission/*` |
| 孩子档案 | `/parent-api/device/child` |

**请求头**：`Authorization: Bearer {家长登录 token}`

**统一响应**

```json
{
  "code": 0,
  "msg": "success",
  "data": { }
}
```

`code !== 0` 时以 `msg`  toast；未登录跳登录页。

**childId**：必须为当前家长有权的 `device_child.id`（与周报、影子任务一致）。

**小程序 baseUrl 示例**

若 `baseUrl = https://host/xiaozhi/parent-api`，则：

- 首页：`GET /learning/overview?childId=2`
- 掌握地图：`GET /learning/mastery-map?childId=2&grade=1`

---

## 1. Tab 结构与页面路由（建议）

| 路由/页面 | 说明 | 主要接口 |
|-----------|------|----------|
| `pages/learning/index` | **学习 Tab 首页** | `GET /learning/overview` |
| `pages/learning/mastery-map` | 数学 · 掌握地图 | `GET /learning/mastery-map` |
| `pages/learning/module` | 模块清单（如「加法」） | 复用 mastery-map 中对应 module，或整包传入 |
| `pages/learning/skill-detail` | 知识点详情 | `GET /learning/skills/{code}` |
| `pages/learning/sessions` | 本周辅导列表 | `GET /learning/sessions` |
| `pages/learning/session-detail` | 单次辅导详情 | `GET /learning/sessions/{sessionId}` |
| 跳转已有页 | 回炉/影子任务 | `GET /shadow-mission/list?childId=&status=active` |
| 跳转已有页 | 补年级 | `PUT /device/child` |

**学科（P1）**

- **数学 / 语文 / 英语**：`subject=math|chinese|english`，需该学科在管理端 **publish** 过图谱；未发布时接口 `msg` 为「尚未发布学科图谱」。
- **科学**：仓库暂无 `science` 图谱 CSV，接口也未定义该 subject，小程序卡片会保持「未开通」。

---

## 2. 学习 Tab 首页（overview）

### 2.1 接口

```
GET /xiaozhi/parent-api/learning/overview?childId={id}&weekStart={yyyy-MM-dd}
```

| Query | 必填 | 说明 |
|-------|------|------|
| childId | 是 | |
| weekStart | 否 | 周一日期；不传为本周一（Asia/Shanghai） |

### 2.2 响应字段（`LearningOverviewVO`）

| 字段 | 类型 | UI 用法 |
|------|------|---------|
| gradeConfigured | boolean | `false` → 顶栏黄条「填写年级，掌握地图更准确」→ 档案页 |
| graphReady | boolean | `false` → 数学卡片不可点，提示「教材图谱准备中」 |
| currentGrade | number | 展示「小学 x 年级」；掌握地图默认年级 |
| textbookSeries | string | 可选展示 |
| subjectsEnabled | string | JSON 字符串，如 `["math"]`，控制学科卡片顺序 |
| weeklyDigest | object | 见下 |

**weeklyDigest（周报）** — 字段说明见 [`家长端-学习洞察-小程序接口.md`](./家长端-学习洞察-小程序接口.md) 第二节。

首页建议布局：

1. 孩子名 + **周切换**（改 `weekStart` 重新拉 overview）
2. 状态条：`gradeConfigured` / `graphReady`
3. **本周摘要卡**：`parentHeadline`、`parentSuggestion`
4. 指标行：`sessionCount`；可选展示 strong/medium/weak 三枚小标签（带 tooltip：拍题/口头/陪伴）
5. **薄弱 Top5**：`topWeakSkills[]` → 点击进 `skill-detail`（传 `code`）
6. **回炉任务**：`remedialShadowMissions[]` → 影子任务页或详情
7. **学科入口**：数学 → `mastery-map`；语/英占位
8. **本周辅导记录** 入口 → `sessions` 列表
9. 页脚：`weeklyDigest.coverageNote` 或 `overview` 内嵌的 coverage 文案

---

## 3. 掌握地图（核心）

### 3.1 接口

```
GET /xiaozhi/parent-api/learning/mastery-map?childId={id}&subject=math&grade={1-6}&weekStart=2026-08-04
```

| Query | 必填 | 说明 |
|-------|------|------|
| childId | 是 | |
| subject | 否 | 默认 `math`；需该学科已有 **published** 图谱（`chinese` / `english` 等） |
| grade | 否 | 默认孩子 `currentGrade`；未填档案时用 **1**，且 `gradeConfigured=false` |
| weekStart | 否 | 周一 `yyyy-MM-dd`，与 `GET /learning/overview` 一致；默认当前自然周（Asia/Shanghai） |

**年级与空态**

| 字段 | 说明 |
|------|------|
| `grade` | 与请求 query 一致（**不再** silent 改成别的年级） |
| `graphGradeMin` / `graphGradeMax` | 当前发布版本声明的覆盖范围，用于 Picker 上限 |
| `gradeSupported` | `false`：`grade` 超出范围或该年级无 SKILL 节点；`modules=[]`，`summary.skillTotal=0`，UI 展示「暂无该年级图谱」 |
| `gradeSupported` | `true`：正常展示模块树 |

学科首页卡片：若 `gradeSupported === false`，勿用 clamp 后的假进度；可展示「暂无 x 年级图谱」并提示可选 `graphGradeMin～graphGradeMax`。

### 3.2 响应 `data`（`LearningMasteryMapVO`）

```json
{
  "childId": 2,
  "subject": "math",
  "subjectLabel": "数学",
  "grade": 1,
  "gradeConfigured": true,
  "graphGradeMin": 1,
  "graphGradeMax": 3,
  "gradeSupported": true,
  "graphReleaseId": 1,
  "graphVersionLabel": "2026.01-math-g1g3",
  "weekStart": "2026-08-04",
  "weekEnd": "2026-08-10",
  "summary": {
    "skillTotal": 28,
    "observedCount": 12,
    "needConsolidateCount": 3,
    "practicingCount": 6,
    "stableCount": 3,
    "unobservedCount": 16,
    "coverageScope": "grade_cumulative",
    "termLabel": "小学1年级图谱累计",
    "observedThisWeekCount": 2,
    "suggestedConsolidateCount": 1
  },
  "modules": [
    {
      "moduleKey": "ADD",
      "moduleLabel": "加法",
      "skillTotal": 4,
      "observedCount": 2,
      "needConsolidateCount": 1,
      "skills": [
        {
          "code": "MATH.G1.ADD.WITHIN_10",
          "name": "10以内加法",
          "description": "10以内不进位加法",
          "status": "practicing",
          "pMastery": 0.62,
          "evidenceCount": 4,
          "lastEvidenceAt": "2026-08-01T14:30:00.000+00:00",
          "observedThisWeek": true,
          "consolidateThisPeriod": false
        }
      ]
    }
  ],
  "coverageNote": "掌握度来自作业辅导中的问答/拍题观察..."
}
```

### 3.3 掌握状态 `status`（与 UI 色）

| status | 含义 | 建议文案 | 建议色 |
|--------|------|----------|--------|
| `unobserved` | 尚无证据 | 尚未观察 | 灰 `#d9d9d9` |
| `need_consolidate` | 有证据且偏弱 | 建议巩固 | 橙 `#fa8c16` |
| `practicing` | 练习中 | 练习中 | 蓝 `#1677ff` |
| `stable` | 较稳 | 较稳 | 绿 `#52c41a` |

**判定规则（后端）**

- 无证据或 `evidenceCount === 0` → `unobserved`，**不展示** `pMastery`（为 null）
- 有证据：`pMastery < 0.45` → `need_consolidate`；`< 0.75` → `practicing`；否则 `stable`

**禁止**用「不及格」「差」等字样。

### 3.3.1 Tab 筛选（小程序）

| Tab | 筛选 |
|-----|------|
| 本学期（`all`） | 展示全部 `modules` |
| 本周（`week`） | 仅 `skills[].observedThisWeek === true`；若 `summary.observedThisWeekCount === 0` 可灰掉 Tab |
| 本次建议巩固（`weak`） | 若存在任一 `consolidateThisPeriod === true`，只展示这些；否则退回 `status === need_consolidate` |

**字段口径**

- `observedThisWeek`：`weekStart`～`weekEnd` 内存在 `DIAGNOSIS_VISION` / `DIAGNOSIS_VERBAL` 证据且 `skill_codes` 含该 code。
- `consolidateThisPeriod`：与周报 `weeklyDigest.topWeakSkills` 同源（孩子全局掌握度最低的 5 个 SKILL code），**不会**把所有 `need_consolidate` 标为 true。
- `summary.observedCount / skillTotal`：当前**年级图谱**下累计有证据的知识点（文案「本学期观察覆盖」与此一致；`termLabel` 说明范围）。

请求掌握地图时请传与 overview **相同的** `weekStart`，以便「本周」与周报对齐。

### 3.4 页面交互

**掌握地图页**

- 顶栏：**年级 Picker**（建议上限取响应 `graphGradeMax`；切换 grade 重新请求）
- **`gradeSupported === false`**：空态文案，例如「暂无该年级图谱（当前覆盖 x～y 年级）」
- **进度摘要**：`summary.observedCount / summary.skillTotal` 文案如「已观察 12/28 个知识点 · 3 个建议巩固」
- **模块列表**：每行 `moduleLabel` + 迷你条（该 module 内各 status 占比）+ 箭头
- 点击进入 **模块页**：展示该 module 的 `skills[]` 列表；右上角可选「学习顺序」→ 调 module-path

**模块页 · 列表模式**

- 每行：`name` + status 标签 + 可选细条（`pMastery * 100`%）
- 点击 → **知识点详情**

**模块页 · 学习顺序（可选 Tab）**

```
GET /xiaozhi/parent-api/learning/mastery-map/module-path?childId=2&moduleKey=ADD&grade=1&subject=math
```

响应 `data.path[]` 与 `skills[]` 元素结构相同，顺序为图谱 `PREREQUISITE_OF` 拓扑序，用于竖向步骤条 UI。

| Query | 必填 |
|-------|------|
| childId | 是 |
| moduleKey | 是 | 与 `modules[].moduleKey` 一致，如 `ADD`、`SUB` |
| subject / grade | 否 | 同 mastery-map |

---

## 4. 知识点详情

### 4.1 接口

**注意**：`code` 含 `.`，路径需 **URL 编码**。

```
GET /xiaozhi/parent-api/learning/skills/MATH.G1.ADD.WITHIN_10?childId=2
```

小程序示例：

```javascript
const code = 'MATH.G1.ADD.WITHIN_10';
wx.request({
  url: `${baseUrl}/learning/skills/${encodeURIComponent(code)}?childId=${childId}`,
  header: { Authorization: `Bearer ${token}` }
});
```

### 4.2 响应 `data`（`LearningSkillDetailVO`）

| 字段 | 说明 |
|------|------|
| code, name, description, grade | 基本信息 |
| subject | 固定 `math`（P1） |
| mastery | 同地图中单条 skill 结构 |
| prerequisites | 前置 SKILL 简要列表 `{ code, name, status }` |
| nextSkills | 后续 SKILL（本知识点为前置） |
| misconceptions | 易错点 `{ code, name, description }`，可能为空数组 |
| parentTip | 给家长的一句话建议，可直接展示 |

详情页底部 CTA 建议：

- 「让孩子问一题」→ 文案说明：对设备说「进入作业辅导」
- 若影子任务列表中存在 `source=learning` 且 `skillCode` 相同 → 按钮「查看回炉练习」

---

## 5. 辅导记录（沿用已有接口）

### 5.1 列表

```
GET /xiaozhi/parent-api/learning/sessions?childId=2&weekStart=2026-07-28&page=1&pageSize=20
```

列表项字段见洞察文档；展示 `startedAt`、`observationLevel`（strong/medium/weak 映射中文：拍题辅导 / 口头问答 / 陪伴记录）。

### 5.2 详情

```
GET /xiaozhi/parent-api/learning/sessions/{sessionId}
```

`skillCodes[]` 可渲染为 tag，点击跳转 `skill-detail`。

---

## 6. 关联接口速查

| 能力 | 方法 | 路径 |
|------|------|------|
| Tab 首页 | GET | `/learning/overview` |
| 掌握地图 | GET | `/learning/mastery-map` |
| 模块学习顺序 | GET | `/learning/mastery-map/module-path` |
| 知识点详情 | GET | `/learning/skills/{code}` |
| 周报 only | GET | `/learning/weekly-digest` |
| 辅导列表 | GET | `/learning/sessions` |
| 辅导详情 | GET | `/learning/sessions/{sessionId}` |
| 进行中影子任务 | GET | `/shadow-mission/list?childId=&status=active` |
| 更新年级 | PUT | `/device/child` body 含 `currentGrade` |
| 读档案 | GET | `/device/child?deviceId=` |

---

## 7. 空态与错误

| 场景 | 处理 |
|------|------|
| `overview.weeklyDigest.sessionCount === 0` | 用 headline/suggestion，引导「进入作业辅导」 |
| `strong + medium === 0` 但有 session | 不强调薄弱榜；tooltip 说明缺问答/拍题 |
| `topWeakSkills` 空 | 「暂无特别薄弱项」 |
| `graphReady === false` | 数学地图入口置灰 |
| `gradeConfigured === false` | 黄条 + 仍可用默认 grade=1 看地图 |
| mastery-map 非 math subject | 后端 `msg`：学科未开放，仅展示占位卡片 |
| skills 404 | 图谱无该 code 或已下线 |

**观测强度（勿误解）**

- `weak` session **不等于** 孩子学得差
- 掌握度 **不等于** 考试成绩

---

## 8. 模块 moduleKey 对照（数学）

由知识点 `code` 第三段解析，常见：

| moduleKey | moduleLabel |
|-----------|-------------|
| NUM | 数的认识 |
| ADD | 加法 |
| SUB | 减法 |
| MUL | 乘法 |
| DIV | 除法 |
| WORD | 应用题 |
| FRA | 分数 |
| GEO | 图形与几何 |
| MEA | 测量 |
| TIME | 时间 |
| DATA | 数据 |
| OTHER | 其它 |

---

## 9. 推荐开发顺序

1. Tab 壳 + `overview` 首页（摘要、薄弱 Top5、sessions 入口）
2. `mastery-map` + 模块列表 + skill 列表
3. `skills/{code}` 详情
4. `module-path` 学习顺序（可选）
5. 与影子任务、档案年级联动

---

## 10. 后端部署说明（给联调同学）

- 需已执行 Liquibase `202608011500.sql` 并 **publish 数学图谱**
- 新接口在 `LearningParentController`：`/mastery-map`、`/skills/{code}`、`/mastery-map/module-path`
- 部署 **manager-api** 后小程序即可联调；无需改 xiaozhi-server

---

## 11. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-08-02 | 初版：掌握地图 + 知识点详情 + 模块路径 + Tab 信息架构 |
