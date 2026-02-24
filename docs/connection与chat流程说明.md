# connection.py 与 chat 流程说明

本文档梳理 xiaozhi-server 连接处理与对话的代码逻辑，重点说明 **connection.py** 的消息路由和 **chat** 方法。

---

## 一、连接与消息入口

### 1.1 连接建立（handle_connection）

- WebSocket 建连后进入 `ConnectionHandler.handle_connection(ws)`。
- 从 `ws.request.headers` 取 **device-id**、**client-id** 等，保存到 `conn`。
- 生成 `session_id`，创建 **超时检查任务** `_check_timeout()`。
- **不阻塞主循环**：启动 `_background_initialize()` 异步任务做：
  - `_initialize_private_config_async()`：按 device-id 拉取智控台私有配置（若 `read_config_from_api`），设置 `need_bind` / `bind_code`，合并到 `self.config`；
  - 在线程池中执行 `_initialize_components()`：TTS/ASR/VAD、声纹、记忆、意图、上报线程等。
- 主循环：`async for message in self.websocket` → 每条消息调用 **`_route_message(message)`**。

### 1.2 消息路由（_route_message）

```
message 进入
    │
    ├─ 若 bind 状态未就绪（bind_completed_event 未 set）
    │      → 等待最多 1 秒，超时则 _discard_message_with_bind_prompt() 并 return
    │
    ├─ 若 need_bind == True
    │      → _discard_message_with_bind_prompt()（播报绑定码等）并 return
    │
    └─ 正常处理
           ├─ isinstance(message, str)
           │      → handleTextMessage(self, message)
           │
           └─ isinstance(message, bytes)
                  → 若来自 MQTT 且带 16 字节头：_process_mqtt_audio_message(message)
                  → 否则：self.asr_audio_queue.put(message)   // 供 ASR 线程消费
```

- **文本**：走 `handleTextMessage` → `TextMessageProcessor.process_message` → 按 JSON 里 `type` 查注册表，执行对应 Handler 的 `handle(conn, msg_json)`。
- **音频**：只做绑定/来源判断，然后把二进制压入 **asr_audio_queue**；实际 VAD/ASR 在 **ASR 的消费线程** 里触发。

---

## 二、用户输入如何到达 chat

### 2.1 语音路径（音频 → chat）

1. **ASR 消费线程**（如 `asr_text_priority_thread`）：  
   `conn.asr_audio_queue.get()` → `handleAudioMessage(conn, message)`（通过 `run_coroutine_threadsafe` 丢到事件循环）。

2. **handleAudioMessage**（receiveAudioHandle.py）：  
   - VAD 判断当前帧是否有人声；  
   - 若有人声且非 manual 模式且设备正在播放 → 可触发打断（handleAbortMessage）；  
   - 调用 **`conn.asr.receive_audio(conn, audio, have_voice)`**。

3. **ASR.receive_audio**（如 base.py）：  
   - 把音频 append 到 `conn.asr_audio`；  
   - 在**非流式**下，当 `client_voice_stop` 为 True（客户端发 listen state=stop）或内部判定一句话结束，会调用 **handle_voice_stop(conn, asr_audio_task)**。

4. **handle_voice_stop**：  
   - 解码/拼接 PCM，可选并发生纹识别；  
   - 调用 ASR 得到文本，与声纹结果拼成 `enhanced_text`（可能为 `{"speaker":"xxx","content":"..."}` 的 JSON 字符串）；  
   - **`await startToChat(conn, enhanced_text)`**；  
   - 可选上报 ASR 结果。

5. **startToChat**（receiveAudioHandle.py）：  
   - 解析 JSON，得到 `actual_text`、`current_speaker`；  
   - 若 `need_bind` 则只做绑定提示并 return；  
   - 若超每日字数限制则提示并 return；  
   - **意图处理**：`handle_user_intent(conn, actual_text)`（退出命令、唤醒词、意图 LLM 等）；若已处理则 return；  
   - `send_stt_message(conn, actual_text)` 把识别结果发给前端；  
   - **`conn.executor.submit(conn.chat, actual_text)`** → 在线程池中执行 **chat(actual_text)**。

### 2.2 文本路径（JSON 消息 → chat）

- 客户端发 **JSON 文本**，如 `{"type":"listen","state":"detect","text":"用户说的话"}`。
- `handleTextMessage` → `process_message` → 根据 `type` 找到 Handler（如 **ListenTextMessageHandler**）。
- Listen 消息中 `state == "detect"` 且带 `text` 时：  
  - 唤醒词且未开问候 → 只发 STT + 停止播放；  
  - 唤醒词且开问候 → `startToChat(conn, "嘿，你好呀")`；  
  - 否则 → `startToChat(conn, original_text)`。  
- `startToChat` 内同样会做意图判断，最后 **`conn.executor.submit(conn.chat, actual_text)`**。

**小结**：无论是语音识别结果还是前端直接发的“听写”文本，最终都会在 **线程池** 里调用 **`conn.chat(actual_text)`**；`chat` 本身是同步方法。

---

## 三、chat 方法逻辑（connection.py）

`chat(self, query, depth=0)` 是**单轮对话 + 可选多轮工具调用**的入口，在**非 async** 线程中执行（通过 executor.submit 调用）。

### 3.1 顶层调用时（depth == 0）

- 生成本句 **sentence_id**。
- 将用户内容写入对话：`self.dialogue.put(Message(role="user", content=query))`。
- 向 TTS 队列放入 **FIRST** 动作（表示开始播报）：  
  `TTSMessageDTO(sentence_type=SentenceType.FIRST, content_type=ContentType.ACTION)`。

### 3.2 深度与工具开关

- **MAX_DEPTH = 5**：达到后不再发起新工具调用，改为往 dialogue 里塞一条系统提示，要求 LLM 直接给最终答案。
- **functions**：  
  - 若 `intent_type == "function_call"` 且存在 `func_handler` 且未强制结束 → `functions = self.func_handler.get_functions()`；  
  - 否则 `functions = None`，本轮不做 function call。

### 3.3 查记忆 + 调 LLM

- 若配置了 **memory**：  
  `memory_str = asyncio.run_coroutine_threadsafe(self.memory.query_memory(query), self.loop).result()`。
- 构造发给 LLM 的对话：  
  `self.dialogue.get_llm_dialogue_with_memory(memory_str, self.config.get("voiceprint", {}))`（内含 system + 历史 + 当前轮，以及可选的 speakers_info 等）。
- 根据是否带 tools 调用：
  - 有 **functions**：`self.llm.response_with_functions(session_id, dialogue, functions=functions)`；
  - 否则：`self.llm.response(session_id, dialogue)`。
- 两者都返回**流式迭代器** `llm_responses`（逐块 yield 内容）。

### 3.4 流式消费 LLM 输出

- 遍历 `for response in llm_responses`：
  - **function_call 模式**：`response` 可能是 `(content, tools_call)` 或含 `"content"` 的 dict；  
    - 累积 `content_arguments`，检测 `<tool_call>` 或 `tools_call` 置位 **tool_call_flag**；  
    - 若有 `tools_call` 则 **合并到 tool_calls_list**（支持多工具并行）。
  - **普通文本**：`response` 为字符串，若不是工具调用则：  
    - 追加到 `response_message`；  
    - 向 TTS 队列放入 **MIDDLE + TEXT**：`TTSMessageDTO(content_detail=content)`。
  - **情绪**：首段非空文本时异步调用 `textUtils.get_emotion(self, content)`（仅一次）。
- 若遍历中抛错：放入错误提示的 TTS，若 depth==0 再发 **LAST** 动作并 return。

### 3.5 工具调用（tool_call_flag == True）

- 若流里没有解析出结构化 tool_calls，但 `content_arguments` 里有类似 `<tool_call>...</tool_call>` 的 JSON，则用 `extract_json_from_string` 解析出一个 tool_call 加入 **tool_calls_list**。
- 若此前有**纯文本回复**（`response_message` 非空）：  
  - 先写入 `dialogue`（assistant），并清空 `response_message`。
- 对 **tool_calls_list** 中每一项：  
  - `asyncio.run_coroutine_threadsafe(self.func_handler.handle_llm_function_call(self, tool_call_data), self.loop)`；  
  - 收集所有 future，`future.result()` 得到 `(result, tool_call_data)` 列表。
- 调用 **`_handle_function_result(tool_results, depth)`** 统一处理工具结果。

### 3.6 _handle_function_result（工具结果 → 回复或继续 chat）

- 对每个 `(result, tool_call_data)`：
  - **Action.RESPONSE / NOTFOUND / ERROR**：  
    - 用 `result.response` 或 `result.result` 作为回复；  
    - `tts_one_sentence` 播报；  
    - `dialogue.put(Message(role="assistant", content=text))`。
  - **Action.REQLLM**：  
    - 收集到 **need_llm_tools**，表示需要把工具结果再交给 LLM 生成自然语言。
- 若有 **need_llm_tools**：  
  - 向 dialogue 追加 **assistant** 消息（带 `tool_calls`）；  
  - 对每个工具结果追加 **tool** 消息（tool_call_id + content）；  
  - **递归调用** `self.chat(None, depth=depth + 1)`，不再传新用户文本，由 LLM 根据 tool 结果继续生成；  
  - 新的一轮里会再次走「查记忆 → LLM 流式 → 文本入 TTS 或再 tool call」。

### 3.7 收尾（无工具或工具已处理完）

- 若 **response_message** 仍有内容：  
  - 拼成 `text_buff`，写入 `dialogue`（assistant），并赋给 `self.tts_MessageText`。
- 若 **depth == 0**：  
  - 向 TTS 队列放入 **LAST** 动作，表示本句播报结束。
- 返回 `True`。

---

## 四、数据流简图

```
[ 客户端 ]
   │ 文本: {"type":"listen","state":"detect","text":"..."}  或  二进制音频
   ▼
[ _route_message ]
   │ 绑定检查 → 文本 → handleTextMessage → Handler.handle
   │           音频 → asr_audio_queue
   ▼
[ ASR 线程 / Listen 等 ]
   │ handleAudioMessage → receive_audio → handle_voice_stop
   │ 或 ListenMessageHandler 直接 startToChat(text)
   ▼
[ startToChat ]
   │ 意图: handle_user_intent（退出/唤醒/意图 LLM）
   │ send_stt_message(conn, actual_text)
   │ executor.submit(conn.chat, actual_text)
   ▼
[ chat(query, depth=0) ]  ← 在线程池中执行
   │ depth==0: 写 user 消息、发 TTS FIRST
   │ 查 memory → get_llm_dialogue_with_memory
   │ llm.response(...) 或 llm.response_with_functions(...)
   │ for response in llm_responses:
   │    文本 → response_message、TTS MIDDLE+TEXT
   │    tools_call → tool_calls_list
   │ 若有 tool_calls_list → handle_llm_function_call → _handle_function_result
   │    REQLLM → 写 tool 消息 → chat(None, depth+1)
   │ 写 assistant 消息，depth==0 发 TTS LAST
   ▼
[ TTS 队列 ] → 合成、发送音频到客户端
```

---

## 五、关键点小结

| 项目 | 说明 |
|------|------|
| **chat 调用方** | 仅两处：`startToChat` 里 `executor.submit(conn.chat, actual_text)`（语音/听写文本）；`_handle_function_result` 里 `self.chat(None, depth=depth + 1)`（工具结果继续让 LLM 说）。 |
| **chat 执行线程** | 同步方法，在线程池（executor）中执行；内部需要跑 async 时用 `run_coroutine_threadsafe(..., self.loop).result()`。 |
| **对话与 TTS** | 用户句写入 `dialogue`；LLM 文本先入 `response_message`，再按句入 TTS 队列（MIDDLE+TEXT），最后发 LAST；工具直接回复也通过 `tts_one_sentence` 入 TTS。 |
| **工具调用** | 流式里解析出 tool_calls → 并行执行 handle_llm_function_call → _handle_function_result 中按 Action 决定直接回复还是再调 chat(depth+1)。 |
| **记忆** | 每轮 chat 开始时 `memory.query_memory(query)`，结果拼进 `get_llm_dialogue_with_memory`；记忆的 role_id 当前为 device_id。 |

---

## 六、用户打断上一句回答（中止当前回复）

当用户在第一问还没说完/播完时又说了第二问，或主动发「打断」指令，服务端会**中止上一句的回复**（停止 LLM 流式消费、清空未播 TTS、通知客户端停播）。流程如下。

### 6.1 何时触发「打断」

- **语音打断**（最常见）：  
  新一帧**音频**进入 → `_route_message(bytes)` → `asr_audio_queue.put` → ASR 线程取到 → **handleAudioMessage(conn, audio)**。  
  在 handleAudioMessage 里：若本帧 **VAD 检测到有人声**，且 **conn.client_is_speaking == True**（设备正在播 TTS），且 **conn.client_listen_mode != "manual"**（非手动模式），则先执行 **await handleAbortMessage(conn)**，再继续把本帧交给 `receive_audio`。
- **显式打断**：  
  客户端发 **JSON 文本** `{"type":"abort", ...}` → handleTextMessage → **AbortTextMessageHandler** → **handleAbortMessage(conn)**。

即：要么「检测到用户新说话且当前在播」触发，要么客户端发 `type=abort` 触发。

### 6.2 handleAbortMessage 做了什么（abortHandle.py）

1. **conn.client_abort = True**  
   全局打断标志，后续 LLM 消费循环和 TTS 线程都会看这个标志。
2. **conn.clear_queues()**  
   清空：`tts_text_queue`、`tts_audio_queue`、`report_queue`；并重置 **audio_rate_controller**（若存在）。  
   效果：还没合成的文本、还没发下去的音频、还没上报的数据都丢掉，不再播、不再上报。
3. **向客户端发停播**  
   `conn.websocket.send({"type": "tts", "state": "stop", "session_id": conn.session_id})`  
   设备/前端收到后应**立即停止播放**当前 TTS。
4. **conn.clearSpeakStatus()**  
   置 **conn.client_is_speaking = False**，表示「设备不再在说话」，后续新语音可以正常走 startToChat。

### 6.3 各处如何响应 client_abort

| 位置 | 行为 |
|------|------|
| **chat() 流式循环**（connection.py） | `for response in llm_responses:` 里每次先判 `if self.client_abort: break`。不再从 LLM 流里取新内容，本轮 chat 很快结束（可能已写入 dialogue 的 assistant 内容会保留，但后续不再追加、不再入 TTS）。 |
| **TTS 文本线程**（tts/base.py 及各流式 TTS 子类） | 每次从 `tts_text_queue.get()` 取到消息后，若 `conn.client_abort` 为 True，则 **continue**，不合成、不往音频队列放；若收到 **FIRST** 会先把 `conn.client_abort = False`（新一句开始时复位标志）。 |
| **TTS 音频线程**（base 等） | 从 `tts_audio_queue.get()` 取到数据后，若 `conn.client_abort` 为 True，则跳过本包（不发送、不上报），**continue**。 |
| **sendAudioHandle（发音频到客户端）** | 发送前若 `conn.client_abort` 则 **raise asyncio.CancelledError** 或直接 **return**，不再发后续包。 |

效果：**LLM 侧**不再消费流式输出；**TTS 侧**不再合成/发送新内容，并清空队列；**客户端**收到 `tts state=stop` 停播。

### 6.4 打断后新一问如何继续

- 触发打断的那段**语音**仍然会走 **receive_audio**（handleAbortMessage 只设标志和清队列，没有清 asr_audio）。后续 VAD 检测到静音或客户端发 listen state=stop 时，会 **handle_voice_stop** → ASR 识别出「第二问」→ **startToChat(conn, new_text)**。
- 在 **startToChat** 里会执行 **conn.client_abort = False**，把打断标志复位，然后正常走意图、**conn.chat(new_text)**，新一问的 LLM 与 TTS 照常进行。
- 若客户端是**发文本**第二问（如 listen state=detect + text），同样会进 startToChat，同样会先把 **client_abort = False** 再 chat。

**小结**：  
「先问一句，没等回答又问一句」时，**新语音（或 type=abort）** 触发 **handleAbortMessage** → 置 **client_abort**、清 TTS/上报队列、通知客户端停播；**chat()** 和 **TTS** 多处检查 **client_abort** 并停止；新一问在 **startToChat** 里复位 **client_abort** 后正常走 **chat**，从而实现「中止上一句回答、开始回答新问题」。

以上即为当前 **connection 与 chat** 的代码流程与逻辑梳理。
