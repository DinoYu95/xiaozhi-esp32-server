# Mem0 源码部署 + 通义千问（Qwen）API + 已有 Postgres

在已有 **mem0-postgres** Docker 容器的前提下，自建 Mem0 服务并使用**阿里云通义千问（DashScope）** 的 API Key，实现 LLM 与 Embedding 全部走国内接口。

---

## 一、前提与准备

- 已有一台跑着 **mem0-postgres** 的机器（Postgres + pgvector，端口如 5432）。
- 已有一份 **通义千问（DashScope）API Key**（阿里云百炼控制台申请）。
- 本机可访问该 Postgres（同机用 `localhost`，跨机用实际 IP）。

---

## 二、通义 API 说明

- **Chat 与 Embedding** 均使用兼容 OpenAI 的同一 Base URL：  
  `https://dashscope.aliyuncs.com/compatible-mode/v1`
- **Chat 模型**：如 `qwen-turbo`、`qwen-plus` 等。
- **Embedding 模型**：如 `text-embedding-v3`（默认 1024 维），或 `text-embedding-v4`。
- 同一 API Key 可用于对话与向量化。

---

## 三、一步一步部署 Mem0 服务（源码 + Qwen）

### 1. 克隆 Mem0 仓库

```bash
git clone https://github.com/mem0ai/mem0.git
cd mem0
```

### 2. 修改 Server 配置（使用 Qwen + 可选去掉 Neo4j）

编辑 **`server/main.py`**。

**2.1 在文件顶部环境变量后，增加通义 Base URL 与模型名（或从环境变量读取）：**

```python
# 在 OPENAI_API_KEY 等变量后添加（若用环境变量可省略下一行）
OPENAI_BASE_URL = os.environ.get("OPENAI_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
OPENAI_LLM_MODEL = os.environ.get("OPENAI_LLM_MODEL", "qwen-turbo")
OPENAI_EMBEDDER_MODEL = os.environ.get("OPENAI_EMBEDDER_MODEL", "text-embedding-v3")
```

**2.2 修改 `DEFAULT_CONFIG` 中的 `llm` 和 `embedder`：**

把原来的：

```python
"llm": {"provider": "openai", "config": {"api_key": OPENAI_API_KEY, "temperature": 0.2, "model": "gpt-4.1-nano-2025-04-14"}},
"embedder": {"provider": "openai", "config": {"api_key": OPENAI_API_KEY, "model": "text-embedding-3-small"}},
```

改成（使用通义 base_url + 模型，embedder 建议加维度以匹配 DashScope）：

```python
"llm": {
    "provider": "openai",
    "config": {
        "api_key": OPENAI_API_KEY,
        "openai_base_url": OPENAI_BASE_URL,
        "model": OPENAI_LLM_MODEL,
        "temperature": 0.2,
    },
},
"embedder": {
    "provider": "openai",
    "config": {
        "api_key": OPENAI_API_KEY,
        "openai_base_url": OPENAI_BASE_URL,
        "model": OPENAI_EMBEDDER_MODEL,
        "embedding_dims": 1024,
    },
},
```

**2.3（可选）仅用 pgvector、不用 Neo4j**

若不想起 Neo4j，可去掉 `graph_store`。先尝试从配置中删除整个 `"graph_store"` 块；若 Mem0 报错要求必填，再保留 Neo4j 并单独起一个 Neo4j 容器。

删除示例：

```python
# 删除或注释掉这两行
# "graph_store": {
#     "provider": "neo4j",
#     "config": {"url": NEO4J_URI, "username": NEO4J_USERNAME, "password": NEO4J_PASSWORD},
# },
```

若删除后 `Memory.from_config(DEFAULT_CONFIG)` 报错，则恢复 `graph_store` 并启动 Neo4j（见下文可选步骤）。

### 3. 配置环境变量

在 **`server/`** 目录下创建 **`.env`**（或导出到当前 shell）：

```bash
# 通义千问 API Key（阿里云百炼）
OPENAI_API_KEY=你的DashScope_API_Key

# 可选：若在 main.py 里没写死，可通过环境变量覆盖
# OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
# OPENAI_LLM_MODEL=qwen-turbo
# OPENAI_EMBEDDER_MODEL=text-embedding-v3

# 连接已有的 mem0-postgres（本机则用 localhost 或 127.0.0.1）
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
POSTGRES_DB=postgres
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
POSTGRES_COLLECTION_NAME=memories
```

若 Postgres 跑在另一台机器或 Docker 网内，把 `POSTGRES_HOST` 改为该机 IP 或容器名。

### 4. 安装依赖并启动服务

在 **`mem0`** 项目根目录（或 `server` 目录，视其 requirements 而定）：

```bash
cd mem0
pip install -e .
cd server
pip install -r requirements.txt
# 若 server 无单独 requirements，则只在根目录 pip install -e . 即可
uvicorn main:app --host 0.0.0.0 --port 8000
```

如需从项目根目录指定 `main.py`：

```bash
uvicorn server.main:app --host 0.0.0.0 --port 8000
```

服务起来后：

- API 地址：`http://<本机IP>:8000`
- 文档：`http://<本机IP>:8000/docs`

### 5. 验证

- 健康：浏览器打开 `http://localhost:8000/docs` 能打开 OpenAPI 页。
- 写入记忆：

```bash
curl -X POST "http://localhost:8000/memories" \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"我喜欢吃披萨"}],"user_id":"alice"}'
```

- 搜索记忆：

```bash
curl -X POST "http://localhost:8000/search" \
  -H "Content-Type: application/json" \
  -d '{"query":"喜欢吃什么","user_id":"alice"}'
```

能正常返回即说明 Mem0 + Qwen + Postgres 已打通。

---

## 四、可选：同时跑 Neo4j（若未去掉 graph_store）

若保留 `graph_store` 且 Mem0 要求必填，需启动 Neo4j：

```bash
docker run -d --name neo4j \
  -p 7474:7474 -p 7687:7687 \
  -e NEO4J_AUTH=neo4j/mem0graph \
  neo4j:latest
```

本机跑 Mem0 时，`NEO4J_URI=bolt://localhost:7687`，`NEO4J_USERNAME=neo4j`，`NEO4J_PASSWORD=mem0graph`，与 `server/main.py` 中默认一致即可。

---

## 五、小智（xiaozhi）侧配置

自建 Mem0 的 API 路径与官方云平台不同（自建是 `/memories`、`/search`），因此小智需通过 **base_url** 走自建 REST 接口。

在 **config.yaml** 的 Memory 配置中，使用 **mem0ai** 并填写自建地址与可选 api_key：

```yaml
Memory:
  mem0ai:
    type: mem0ai
    # 自建 Mem0 服务地址（必填）
    base_url: http://你的服务器IP:8000
    # 自建服务若未做鉴权可留空或任意非空占位
    api_key: ""
```

若自建 Mem0 日后加了鉴权，再在这里填与服务器约定好的 `api_key` 即可。

同时把 **selected_module.Memory** 选为 **mem0ai**：

```yaml
selected_module:
  Memory: mem0ai
```

保存后重启小智服务，记忆会写入自建 Mem0（即你当前用的 Postgres + Qwen）。

---

## 六、简要检查清单

| 步骤 | 说明 |
|------|------|
| 1 | 克隆 `mem0ai/mem0`，改 `server/main.py` 的 LLM/Embedder 为通义 base_url + 模型 |
| 2 | 配置 `.env`：`OPENAI_API_KEY`、`POSTGRES_*` 指向已有 mem0-postgres |
| 3 | 可选去掉 `graph_store` 或启动 Neo4j |
| 4 | `uvicorn main:app --host 0.0.0.0 --port 8000` 启动 Mem0 |
| 5 | curl 验证 `/memories`、`/search` |
| 6 | 小智 config.yaml 中 Memory 选 mem0ai，填 `base_url: http://IP:8000` |

完成后，Mem0 使用通义千问做对话与向量化，数据存在已有 Postgres 中，小智通过 base_url 使用自建 Mem0。

---

## 七、短期 + 长期记忆（分支 feature/short-long-term-memory）

若希望同时使用**近期对话的结构化摘要（短期）**和 **Mem0 长期记忆**，可使用本仓库分支 `feature/short-long-term-memory` 中的 **short_long_memory** 提供者。

- **短期**：与 mem_local_short 相同，用配置的 LLM 对对话做结构化摘要，保存在本地 `data/.memory_short_long.yaml`。
- **长期**：与 mem0ai 相同，调用已部署的 Mem0（base_url 或 api_key）。

配置示例（Mem0 已部署在阿里云时）：

```yaml
selected_module:
  Memory: short_long_memory

Memory:
  short_long_memory:
    type: short_long_memory
    base_url: http://你的Mem0服务IP:8000
    llm: ChatGLMLLM   # 用于短期摘要的 LLM
```

查询时返回「近期记忆（短期）」与「长期记忆」两部分，写入时同时更新短期摘要与 Mem0。

**若使用智控台配置 agent**：在「模型配置」→「记忆」中选中「长短期记忆」并编辑，**必须填写「Mem0 自建地址」**（如 `http://你的Mem0服务IP:8000`），否则长期记忆不会启用，仅短期记忆生效，自建 Postgres 里不会有数据。启动 xiaozhi-server 时若看到日志「长期记忆未启用: 请在智控台…」即表示未配置 base_url。
