# P1 学习系统 · 部署与验证

## 1. 数据库

启动 manager-api 时 Liquibase 自动执行 `202608011500.sql`。

## 2. 导入并发布图谱（管理端）

需超级管理员 Token（Header：`Authorization: Bearer <token>`），完整前缀 `http://<host>:8002/xiaozhi/admin/learning/kg`。

**CSV 位置**：`docs/learning-kg-sample/`（数学 full、语/英 G1 pilot、最小样例）。**curl 与脚本**：见同目录 `README.md`、`import-kg.sh`。

### 数学（推荐 full 版）

1. `POST /admin/learning/kg/release?versionLabel=2026.01-math-g1g3&subject=math&gradeMin=1&gradeMax=3`
2. `POST /admin/learning/kg/release/{id}/import-nodes` multipart `file=@docs/learning-kg-sample/nodes-g1-g3-math-full.csv`
3. `POST /admin/learning/kg/release/{id}/import-edges` multipart `file=@docs/learning-kg-sample/edges-g1-g3-math-full.csv`
4. `POST /admin/learning/kg/release/{id}/validate`
5. `POST /admin/learning/kg/release/{id}/publish`
6. `GET /admin/learning/kg/release/active?subject=math`

### 语文 / 英语（试点，可选）

1. `subject=chinese` 或 `english`，`gradeMin=1&gradeMax=1`
2. nodes/edges 分别用 `nodes-g1-chinese-pilot.csv`、`edges-g1-chinese-pilot.csv`（或 english 同名）

发布后可查 active；家长端掌握地图与作业 SKILL 池 **当前仍仅 math**。

## 3. 孩子档案

家长小程序保存孩子时设置 `currentGrade`（1–3 推荐）、可选 `subjectsEnabled` 如 `["math"]`。

## 4. xiaozhi-server

`data/.config.yaml` 可选：

```yaml
learning:
  enabled: true
```

作业辅导：进入/退出/超时 → session；每轮发言 → turn；拍照回复后 → photo 诊断。

## 5. 家长周报

`GET /parent-api/learning/weekly-digest?childId=`

## 6. 回炉影子任务

拍照诊断判错且置信度足够 → 自动创建 `source=learning` 的 `parent_shadow_mission`（日常对话也会注入，与现有影子任务相同）。

## 7. 家长端掌握地图 API

小程序「学习 Tab」掌握地图与知识点详情见 **`docs/家长端-学习Tab-小程序开发说明.md`**。
