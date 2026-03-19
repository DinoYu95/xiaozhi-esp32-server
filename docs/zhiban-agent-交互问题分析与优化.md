# xiaozhi-server → zhiban-agent 交互问题分析与优化

## 一、问题概述

1. **首响慢**：用户说话后，第一句回复等待时间长  
2. **语句奇怪切分**：如「你吃了吗」被拆成「你吃了」+「吗」两段播放，语气词与前半句分离，听感差

---

## 二、根因分析

### 2.1 首响慢

| 环节 | 位置 | 问题 |
|------|------|------|
| HTTP 连接 | `zhiban_agent_client.py` | 每次 `chat()`/`stream()` 都用 `with httpx.Client()` 新建连接，无连接池、无 keep-alive 复用 |
| 记忆查询 | `connection.py` `chat()` | `memory.query_memory(query)` 在 LLM 前**同步阻塞**，记忆慢会拖累首 token |
| zhiban-agent 冷启 | zhiban-agent 侧 | 首次请求时模型加载、LangGraph 初始化等有冷启动 |
| 流程串行 | connection | ASR → intent → memory → LLM → TTS 全串行，每步叠加延迟 |

### 2.2 语句奇怪切分

**根因**：流式 LLM 输出顺序 + TTS 按标点分句策略冲突。

典型情况：
- LLM 流式输出：`"你吃了"` → `"？"` → `"吗"`
- TTS `_get_segment_text()` 在 `punctuations` 中遇到 `？` 就切分
- 当前 buffer 为 `"你吃了？"` 时立即切分，发送给 TTS
- 此时 `"吗"` 尚未到达
- `"吗"` 到达后作为「剩余文本」单独合成
- 结果：TTS 先播「你吃了？」，再播「吗」，听起来被拆开

涉及文件：
- `core/providers/tts/base.py`：`_get_segment_text()` 按 `。！？!?；;\n` 等标点分句
- `core/utils/textUtils.py`：`get_string_no_punctuation_or_emoji()` 处理首尾标点
- `core/connection.py`：LLM 每个 chunk 直接 `put` 到 `tts_text_queue`

---

## 三、优化方案

### 3.1 语句切分修复（已实现）

在 `_get_segment_text()` 中增加「疑问句尾缓冲」逻辑：

- 当分句标点为 `？` 或 `！`，且**紧邻前一字符为** `了`/`呢`/`啊`
- 且**分段长度 < 6 字符**（常见「X了吗」「X呢」「X啊」尚未完整）
- → 暂不切分，返回 `None`，继续积攒后续 chunk（如「吗」）
- 收到 `tts_stop_request`（LAST）或分段已足够长时再切分

效果：避免「你吃了」+「吗」被拆成两段播放。

### 3.2 首响优化（建议）

1. **httpx 连接复用**  
   - `ZhibanAgentClient` 使用单例或共享的 `httpx.Client`  
   - 开启 `limits=httpx.Limits(max_keepalive_connections=4)` 等配置  

2. **记忆查询与 LLM 并行（可选）**  
   - 记忆非必须时，可尝试首 token 优先，记忆结果后续补充（需评估业务影响）  

3. **zhiban-agent 预热**  
   - 部署后或定时对 `/api/chat` 发空/轻量请求，减少冷启动  

4. **流式优先**  
   - 当前已使用 `stream()`，保持流式以降低 TTFT  

---

## 四、修改清单

| 文件 | 修改内容 |
|------|----------|
| `core/providers/tts/base.py` | `_get_segment_text()` 增加疑问句尾缓冲逻辑，避免「你吃了」+「吗」被拆开 |
| `core/zhibanAgent/zhiban_agent_client.py` | httpx 连接复用：`_get_client()` 返回持久化 Client，`limits` 开启 keep-alive |

---

## 五、串行耗时点梳理（可优化或可跳过）

针对「用不着但耗时间」的串行步骤：

| 环节 | 位置 | 耗时原因 | 是否必需（用 ZhibanAgent 时） | 建议 |
|------|------|----------|------------------------------|------|
| **memory.query_memory** | `connection.py:938-941` | Mem0/自建 API 网络查询，short_long 会调 `_long_search`（HTTP） | **通常不必**：zhiban-agent 自有长期记忆（load_memory_node），xiaozhi 记忆多为重复 | 可配置：用 ZhibanAgent 时跳过 xiaozhi memory 查询，或改为可选/并行 |
| **func_handler.get_functions()** | `connection.py:912` | 遍历各执行器（server_mcp、mcp_endpoint、device_iot 等）拉工具列表，首次可能触发 MCP 连接 | **完全不必**：ZhibanAgent 不处理 xiaozhi 的 function call，`response_with_functions` 直接忽略 functions | 用 ZhibanAgent 时可直接不调 `get_functions()`，或快速短路返回空列表 |
| **func_handler._initialize()** | `connection.py:807-808` | MCP 连接、Home Assistant 初始化等，后台执行 | 对纯 ZhibanAgent 对话**通常不必**：工具在 zhiban-agent 内 | 已是异步，不阻塞首响；若确定不用 xiaozhi 工具，可考虑懒加载 |
| **意图识别** | `intentHandler` | 用 ZhibanAgent 时已跳过（`is_zhiban` 直接 return False） | 已跳过 | 无需改 |
| **check_device_output_limit** | `receiveAudioHandle` | 内存字典查询，几乎无耗时 | 必需 | 无需改 |
| **get_current_time_info** | `result_for_context` 等 | 本地 + cnlunar，耗时很小 | 仅 result_for_context 意图时用 | 无需改 |

**已实现**（`connection.py`）：

1. **memory.query_memory 跳过**：`_is_zhiban_llm()` 为 True 时，不调用 `query_memory`，`memory_str` 保持空，省去 Mem0 网络往返。
2. **get_functions 跳过**：`_is_zhiban_llm()` 为 True 时，不调 `get_functions()`，`functions` 保持 `None`，避免 MCP/工具初始化开销。

---

## 六、关键阶段 RT 日志（已实现）

`connection.py` 在 `depth=0` 时打印以下耗时（`[RT]` 前缀，info 级别）：

| 日志 | 含义 |
|------|------|
| `[RT] get_functions 耗时` | 拉取工具列表耗时（ZhibanAgent 时跳过，不打印） |
| `[RT] memory.query_memory 耗时` | 记忆查询耗时（ZhibanAgent 时跳过，不打印） |
| `[RT] chat 入口到 LLM 调用前` | 含 get_functions/memory/上下文准备的总耗时 |
| `[RT] LLM 首 token: Xs \| chat 入口到首包(TTFT): Ys` | LLM 首包耗时 + 用户感知 TTFT |
| `[RT] LLM 流式完成耗时 \| chat 总耗时` | LLM 流式结束耗时 + 整轮 chat 总耗时 |

---

## 七、后续可做

- zhiban-agent 侧：优化流式 token 边界，尽量不在「吗」前单独输出 `？`
- 记忆查询：评估与 LLM 并行或延后加载的可能
- 部署预热：对 zhiban-agent 做轻量心跳，减小冷启动影响
