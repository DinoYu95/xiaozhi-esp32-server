# 学习知识图谱 CSV

## 文件说明

| 文件 | 说明 |
|------|------|
| **`nodes-g1-g3-math-full.csv`** | **推荐发布用**：小学 1～3 年级数学，约 90+ 节点（58 个 SKILL + 错因/诊断/练法） |
| **`edges-g1-g3-math-full.csv`** | 对应关系：前置、错因、诊断、干预，约 80+ 条 |
| **`nodes-g1-chinese-pilot.csv`** / **`edges-g1-chinese-pilot.csv`** | **语文试点**：一年级约 18 节点（可导入 `subject=chinese`，非 full 课标库） |
| **`nodes-g1-english-pilot.csv`** / **`edges-g1-english-pilot.csv`** | **英语试点**：一年级约 18 节点（`subject=english`） |
| `nodes.csv` / `edges.csv` | 最小样例（8 节点），仅用于理解格式 |

## 覆盖范围（full 版）

- **一年级**：10/20 以内加减、进退位、分解、减法意义、一步应用题、图形与时间等  
- **二年级**：100 以内加减、乘法意义与口诀、表内除法、有余数除法、测量等  
- **三年级**：万以内数、多位数乘除、分数初步、周长面积、两步应用题等  

对齐 **课标粒度（单元级 SKILL）**，非某一家出版社逐课目录；后续可加 `textbook_mapping` 表映射人教/北师课时。

## 编码规则

- 数学：`MATH.G{年级}.{域}.{名称}`  
- 语文试点：`CHN.G1.*`（后续可扩 G2/G3）  
- 英语试点：`ENG.G1.*`  
- 改含义 **新开 code**，勿改旧 code 语义  

## 学科与产品现状

| subject | 仓库 CSV | 导入后 DB | 家长端掌握地图 / 作业打标 |
|---------|----------|-----------|---------------------------|
| `math` | G1～G3 full | ✅ | ✅ |
| `chinese` / `english` | G1 pilot | ✅ 可发布 | ✅ **需发布 + 部署放开 API 后**；作业 SKILL 池仍偏 math |
| `science` | 无 | — | 小程序可展示卡片，后端暂无图谱 |

## 导入步骤

管理端（超管，`Authorization: Bearer <token>`）：

1. `POST .../admin/learning/kg/release?versionLabel=...&subject=math|chinese|english&gradeMin=&gradeMax=`
2. `POST .../release/{id}/import-nodes` multipart 上传 **nodes\*.csv**
3. `POST .../release/{id}/import-edges` multipart 上传 **edges\*.csv**
4. `POST .../release/{id}/validate`
5. `POST .../release/{id}/publish`
6. 校验：`GET .../release/active?subject=math`（或 chinese / english）

详见 `docs/learning-p1-implementation.md`。

### 一键脚本（可选）

```bash
chmod +x docs/learning-kg-sample/import-kg.sh
export ADMIN_TOKEN='你的智控台超管 token'
export BASE_URL='http://127.0.0.1:8002/xiaozhi'   # 生产改成你的域名:端口/xiaozhi

./docs/learning-kg-sample/import-kg.sh math
./docs/learning-kg-sample/import-kg.sh chinese
./docs/learning-kg-sample/import-kg.sh english
```

### curl 手工（数学，与以前一致）

```bash
BASE='http://127.0.0.1:8002/xiaozhi'
TOKEN='...'
REPO='docs/learning-kg-sample'   # 在仓库根目录执行

# 1. 创建 draft，记下 data 里的 releaseId
curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/admin/learning/kg/release?versionLabel=2026.02-math-g1g3-full&subject=math&gradeMin=1&gradeMax=3"

# 2. 导入（把 {id} 换成上一步返回的 id）
curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@${REPO}/nodes-g1-g3-math-full.csv" \
  "$BASE/admin/learning/kg/release/{id}/import-nodes"

curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  -F "file=@${REPO}/edges-g1-g3-math-full.csv" \
  "$BASE/admin/learning/kg/release/{id}/import-edges"

curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/admin/learning/kg/release/{id}/validate"

curl -sS -X POST -H "Authorization: Bearer $TOKEN" \
  "$BASE/admin/learning/kg/release/{id}/publish"

curl -sS -H "Authorization: Bearer $TOKEN" \
  "$BASE/admin/learning/kg/release/active?subject=math"
```

语文 / 英语：同上，改 `subject=chinese|english`、`gradeMin/Max` 与 CSV 路径为 `nodes-g1-*-pilot.csv` / `edges-g1-*-pilot.csv`。

**Token**：智控台 Web 登录超管账号 → 浏览器开发者工具看接口响应里的 `token`，或 `POST /xiaozhi/user/login`（需验证码/SM2，一般用 Web 登录复制 token 最省事）。

**注意**：同一 `subject` 再次 `publish` 会把旧 published 版本 **归档**，不影响已写入的 `learner_skill_state`（按 node code 全局复用）。
