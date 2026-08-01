# 学习知识图谱 CSV

## 文件说明

| 文件 | 说明 |
|------|------|
| **`nodes-g1-g3-math-full.csv`** | **推荐发布用**：小学 1～3 年级数学，约 90+ 节点（58 个 SKILL + 错因/诊断/练法） |
| **`edges-g1-g3-math-full.csv`** | 对应关系：前置、错因、诊断、干预，约 80+ 条 |
| `nodes.csv` / `edges.csv` | 最小样例（8 节点），仅用于理解格式 |

## 覆盖范围（full 版）

- **一年级**：10/20 以内加减、进退位、分解、减法意义、一步应用题、图形与时间等  
- **二年级**：100 以内加减、乘法意义与口诀、表内除法、有余数除法、测量等  
- **三年级**：万以内数、多位数乘除、分数初步、周长面积、两步应用题等  

对齐 **课标粒度（单元级 SKILL）**，非某一家出版社逐课目录；后续可加 `textbook_mapping` 表映射人教/北师课时。

## 导入步骤

管理端（超管）：

1. `POST /xiaozhi/admin/learning/kg/release?versionLabel=2026.02-math-g1g3-full&gradeMin=1&gradeMax=3`
2. `import-nodes` ← **nodes-g1-g3-math-full.csv**
3. `import-edges` ← **edges-g1-g3-math-full.csv**
4. `validate` → `publish`

详见 `docs/learning-p1-implementation.md`。

## 编码规则

- 货号稳定：`MATH.G{年级}.{域}.{名称}`  
- 改含义 **新开 code**，勿改旧 code 语义  
