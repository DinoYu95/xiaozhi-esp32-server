# 多角色与智伴 Agent 适配设计草案（讨论用）

本文档在「家长端 + 设备主孩子 + 声纹」已落地的前提下，围绕以下问题做方案设计，便于一起讨论定稿：

1. **声纹识别结果（主孩子 / 非主孩子 / 家长 / 未知）及 introduction 如何传给 zhiban-agent，并支持不同口吻（含年龄等）**
2. **主孩子在说、机器尚未响应时，其他声音（非主孩子/环境）是否打断、如何结合语境**
3. **不同人的 memory 隔离（主孩子多维度 + 个人全局，非主孩子策略）**
4. **按「听到的角色」选用不同 skill（不同 agent），与现有代码如何结合**
5. **除人声以外的环境信息如何参与对话**

---

## 一、现状简要

### 1.1 数据流（当前）

- **声纹**：ASR 结束并发声纹识别 → 得到 `speaker_id` → `voiceprint_provider.speaker_map[speaker_id]` 得到 `name`、`description`（即 introduce）→ 下游收到 `enhanced_text = {"speaker": name, "content": text}` 或 FunASR 的 dict 带 `speaker`。
- **conn**：`conn.current_speaker` 存的是**名字**（sourceName）；对话里 user 消息的 content 可能是整段 JSON 或纯文本。
- **记忆**：`memory.init_memory(role_id=self.device_id)`，全连接共用一个 role_id。
- **zhiban-agent**：只传 `text`、`session_id`、`user_id`（当前未传）；无 speaker_type、无 introduction、无年龄、无环境信息。

### 1.2 私有配置里已有

- **voiceprint.speakers**：`id,sourceName,introduce`（主孩子与后台声纹都有 introduce）。
- 设备有**主孩子**概念（device_child 表，一设备一主孩）；声纹列表 = 本设备主孩子声纹 + 后台配置的声纹。

### 1.3 缺失能力（待设计）

- zhiban-agent 不知道「当前是谁在说话、介绍是什么、大概年龄」→ 无法按人调口吻。
- 多人/环境声同时存在时的**打断策略**未定义。
- 记忆未按人隔离，且未区分「主孩子多维度记忆 vs 其他人」。
- 未按「当前说话人」选择不同 agent/skill。
- 环境信息（噪音、场景等）未参与对话。

---

## 二、传给 zhiban-agent 的「说话人上下文」

### 2.1 目标

- 让 zhiban-agent 知道：**当前这句话是谁说的**、**该人的简介（introduction）**、**建议口吻（含年龄）**，以便用不同语气/内容回复。

### 2.2 建议：扩展请求体，增加 `speaker_context`

在现有 `text`、`session_id`、`user_id` 基础上，增加可选字段（xiaozhi-server → zhiban-agent），并预留 `skill_id`、`environment_context`：

```json
{
  "text": "用户说的话（纯文本）",
  "session_id": "...",
  "user_id": "device_id 或 device_id_childId",
  "skill_id": "skill_children_chat",
  "speaker_context": {
    "speaker_id": "声纹ID",
    "speaker_name": "小明",
    "speaker_type": "owner_child | parent | other_child | other_adult | unknown",
    "introduction": "家长填的 introduce，如：5岁，喜欢恐龙，怕打雷",
    "estimated_age": "5-6",
    "is_owner_child": true
  }
}
```

- **speaker_type**：  
  - `owner_child`：本设备主孩子；  
  - `parent`：家长（若声纹库里有家长并识别到）；  
  - `other_child`：声纹库中其他孩子；  
  - `other_adult`：声纹库中其他成人；  
  - `unknown`：未识别或低于阈值。
- **introduction**：来自私有配置 voiceprint.speakers 里该 speaker 的 introduce 字段，原样或截断后传。
- **estimated_age**：  
- **estimated_age**（已定）：主孩子用 device_child 的 birthday 算出年龄；非主孩子/未知从 introduce 抽取或传 `"unknown"`，zhiban 侧对 unknown 用儿童友好、中性口吻。  
- **is_owner_child**：便于 zhiban 快速判断是否「主人」，用于 memory 策略、回复风格。

**落地**：  
- xiaozhi-server：在调用 zhiban 前，根据 `conn.current_speaker`、voiceprint 的 speaker_map、设备主孩子 id，拼出 `speaker_context`；若未识别则 `speaker_type=unknown`，`introduction`/`estimated_age` 可空或默认说明。  
- zhiban-agent：在 system 或首条上下文里注入上述信息，例如：「当前说话人：{speaker_name}，类型：{speaker_type}，简介：{introduction}，建议年龄：{estimated_age}。请根据以上信息调整称呼和口吻。」

### 2.3 年龄与口吻的约定（建议）

- 有 **estimated_age**：按年龄段选口吻（更童趣/更简洁等）。  
- **unknown**：统一约定「默认以对儿童友好、中性口吻回复」，避免对陌生人过于亲昵或过于冷淡。

---

## 三、打断策略（主孩子在说 + 机器未响应 + 其他声音）

### 3.1 场景

- 主孩子正在说一句话，ASR 尚未结束或已结束但 LLM 还没开始/正在生成。
- 此时又检测到**另一段人声**（非主孩子/其他孩子/家长）或**环境声**。

### 3.2 目标

- 不轻易打断「主孩子在说」的轮次；若判断是**同一轮内的插话**或**无关环境声**，可忽略或延后处理。
- 若判断是**明确的新一轮请求**（如家长说「暂停」），再考虑打断。

### 3.3 策略（已定：做打断意图识别）

- **策略 1：按说话人**  
  - 若**当前轮已绑定为主孩子**：  
    - 新声音若是**主孩子**：可视为同一轮继续（ASR 可能合并或续写）。  
    - 新声音若是**非主孩子**：在**机器尚未开始 TTS 播放前**，不直接打断；先走**打断意图识别**（见下），只有识别为打断意图才打断，否则本段新声音进入待处理队列或丢弃。  
  - 若当前轮是**非主孩子/未知**：可按现有逻辑（或稍宽松）允许打断。
- **策略 2：打断意图识别（必做）**  
  - 当「主孩子在说且机器未播」时，若检测到**非主孩子声音**，先对这段新声音做**轻量意图识别**（如「暂停/停止/别说了/换个话题/等一下」等）：  
    - 若为**明确打断意图** → 允许打断当前轮，开启新轮并处理新声音。  
    - 否则 → 不打断，新声音丢弃或仅打日志。  
  - 实现可选：在 xiaozhi-server 用意图模块做关键词/小模型分类，或把「待判定的短文本」交给 zhiban-agent 轻量接口返回 is_interrupt_intent。
- **策略 3：环境声**  
  - 仅 VAD 有人声、声纹为未识别：不触发新轮，仅打日志或写入 environment_context。

**实现要点**（xiaozhi-server）：

- 在 `handle_voice_stop` / 送 `startToChat` 时，给当前轮打标「当前轮说话人」= 主孩子 / 非主孩子 / 未知。
- 在 **VAD 检测到新一段人声** 且当前轮为主孩子、机器未播时：  
  - 先对**新声音**做 ASR（若尚未有结果）→ 得到文本 → **打断意图识别**；  
  - 若为打断意图 → 调用 `handleAbortMessage` 并开启新轮处理新声音；  
  - 否则 → 不打断，新音频丢弃或缓存。  
- 若当前轮主孩子在说且机器**已在播**：按现有 `client_listen_mode`（manual/auto）决定是否打断。

---

## 四、按人隔离的 Memory 与 zhiban 侧策略

### 4.1 目标

- **主孩子**：有「不同 agent 维度的 memory」+「个人全局 memory」；即同一主孩子在不同技能/场景下的记忆可区分，同时有跨场景的长期记忆。
- **非主孩子/未知**：要么不写长期 memory，要么用「访客/临时」维度，与主孩子严格隔离。

### 4.2 role_id / user_id 约定

- **xiaozhi-server**  
  - `memory.init_memory(role_id=...)`：  
    - 若识别到声纹且能映射到**主孩子**：`role_id = f"{device_id}_{child_id}"`（或 `child_id` 若全局唯一）；  
    - 若识别到**非主孩子**：`role_id = f"{device_id}_guest_{speaker_id}"` 或 `f"{device_id}_other"`（按是否要分人存储决定）；  
    - 未识别：`role_id = device_id`（与现有一致）。  
  - 注意：**同一连接上说话人会变**，所以 memory 的 `role_id` 应在**每轮对话时按当前说话人更新**，而不是连接初始化时写死。即：每轮先 `memory.switch_role(role_id)` 或等价「按当前说话人查 memory」再 `query_memory`。
- **zhiban-agent**  
  - 请求里 `user_id` 与 xiaozhi 的 `role_id` 对齐（同一套约定），memory 按 `user_id` 分区。  
  - 主孩子：`user_id = device_id_childId`，可做「该 user 下多 agent/skill 维度」的 memory（见下节）。  
  - **非主孩子/访客（已定）**：`user_id = device_id_guest_*` 或 `device_id_other`，存储策略为**只写访客短期记忆、不参与长期沉淀与召回**；与主孩子严格隔离。

### 4.3 主孩子「多维度 memory」在 zhiban 侧

- 现有理解：zhiban-agent 内部有 router → 多 agent/skill，每个 skill 可视为一个「子 agent」。
- 主孩子的 memory 可设计为：  
  - **按 skill/agent 维度**：同一 user_id 下，不同 skill_id 有不同的 memory 命名空间或 key（如 `user_id + skill_id`）；  
  - **个人全局**：再有一份「不绑 skill」的全局记忆，用于跨技能的个人信息、偏好。  
- 这样「不同角色用不同 skill」时，主孩子用默认 skill 时有默认 agent 记忆 + 个人全局记忆；若将来扩展「家长 skill」，可再分 agent 维度。

---

## 五、Skill 固定格式与智控台可配置（已定）

### 5.1 目标

- Skill 像 Claude 的 Skill 一样有**固定格式**（统一 schema），便于智控台创建/编辑、zhiban-agent 加载与路由。
- **智控台可配置**：按「说话人类型」或「设备/智能体」配置使用哪个 skill，而不是写死在 zhiban 内。

### 5.2 Skill 固定格式（类 Claude Skill）

每个 Skill 为一个可序列化的配置对象，建议采用**统一 JSON Schema**，智控台与 zhiban-agent 均按此格式读写。

**字段定义：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 技能唯一标识，如 `skill_children_chat`、`skill_parent_control` |
| `name` | string | 是 | 展示名称，如「儿童对话」「家长控制」 |
| `description` | string | 否 | 简短用途说明，用于智控台列表与路由说明 |
| `instructions` | string | 是 | 技能说明/系统级提示（类似 Claude SKILL.md 内容），发给模型作为 system 或强约束 |
| `version` | string | 否 | 版本号，便于灰度与回滚，如 `1.0` |
| `tools` | array | 否 | 该技能可用的工具 id 列表，如 `["play_music", "set_timer"]`；zhiban 按此过滤或加载工具 |
| `metadata` | object | 否 | 扩展字段，如默认参数、开关等 |

**示例：**

```json
{
  "id": "skill_children_chat",
  "name": "儿童对话",
  "description": "面向主孩子的日常对话、故事、问答",
  "instructions": "你是家庭中的儿童陪伴助手。当前对话的是设备主孩子，请用适合儿童的口吻，简短、有趣、安全。避免复杂逻辑与成人话题。",
  "version": "1.0",
  "tools": ["play_music", "story_tell"],
  "metadata": {}
}
```

**存储形态（智控台为唯一数据源）：**

- **智控台/manager-api**：表 `ai_skill` 存 skill 的完整定义（id、name、instructions、tools 等）；支持 CRUD、与智能体关联（见下）。**这里是 skill 文案与配置的唯一数据源**。
- **zhiban-agent**：**不再**从本地 nodes 文件夹读 instructions；应从智控台/manager-api 拉取（请求时调 API 或启动时/定时同步），运行时按选中的 `skill_id` 取 instructions 与 tools 注入 system。原 nodes 下若只有 prompt 文案可删除，若为执行逻辑可保留但 instructions 必须来自智控台。

### 5.3 智控台可配置：说话人类型 → 多 Skill（按意图选）

- **一个说话人可对应多个 skill**。在智能体维度配置「**说话人类型 → 多个 skill_id**」；每轮对话时由 **zhiban-agent 根据用户意图** 从该说话人允许的 skill 列表中选一个执行。
- **智控台界面**：在智能体编辑页「说话人→技能」区块中，每类说话人**多选**多个技能（如主孩子可选「儿童对话」「讲故事」等）。
- **私有配置下发**：设备拉取 get_agent_models 时，返回中 `skill_mapping` 为 **speaker_type → skill_id 数组**，例如：

```json
"skill_mapping": {
  "owner_child": ["skill_children_chat", "skill_storytelling"],
  "parent": ["skill_parent_control"],
  "other_child": ["skill_guest", "skill_guest_child"],
  "other_adult": ["skill_guest", "skill_guest_adult"],
  "unknown": ["skill_general_chat"]
}
```

- **可选**：支持设备级覆盖，则在设备私有配置里覆盖对应 key 即可。

### 5.4 xiaozhi-server 与 zhiban-agent 的配合

- **xiaozhi-server**：  
  - 从私有配置读取 `skill_mapping`（speaker_type → list&lt;skill_id&gt;）；  
  - 每轮对话根据 `speaker_type` 查得 **`skill_ids`** 列表，在请求 zhiban-agent 时带上 **`skill_ids`**（可选，若未配置则不带）。  
- **zhiban-agent**：  
  - 若请求带 `skill_ids`：根据**用户意图**从列表中选一个 skill，加载其 instructions、tools，作为本轮 system 与工具集；  
  - 若未带：使用内置默认。  
- 这样**一说话人多技能、按意图选 skill**，路由策略由智控台配置允许的 skill 集合，具体走哪个由 zhiban 意图决定。

### 5.5 与现有 xiaozhi 代码的结合

- 调用链：`conn.chat(query)` → `llm.response(session_id, dialogue, **kwargs)`；kwargs 中增加 `user_id`、`speaker_context`、**`skill_ids`**（从私有配置的 skill_mapping 按当前 speaker_type 查得的列表）。
- ZhibanAgent 的 `response` 与 `zhiban_agent_client.chat/stream`：请求 body 增加 `speaker_context`、`skill_ids`（数组）、可选 `environment_context`；`user_id` 由 connection 按当前轮说话人传入。
- xiaozhi 不维护「多个 LLM 实例」，仅一个 ZhibanAgent 客户端；该说话人可用的 skill 列表由智控台 skill_mapping 下发，**具体走哪个 skill 由 zhiban-agent 按意图选择**。

---

## 六、环境信息（非人声）的参与方式

### 6.1 思路与数据流

- **环境信息统一由 xiaozhi-server 带给 zhiban-agent**：zhiban-agent 不主动拉取任何环境数据，只接收请求体里的 `environment_context`。  
- xiaozhi-server 负责：  
  - 用**本机已有数据**（VAD、ASR、声纹结果等）算出部分字段；  
  - 把**设备通过 WebSocket/hello/上报**传上来的字段（若有）一并放进 `environment_context`；  
  - 随每轮 chat 请求一起发给 zhiban-agent。  
- 这样职责清晰：一端汇总、一端只消费。

### 6.2 当前列出的 `environment_context` 包含啥

| 字段 | 含义 | 谁填、从哪来 |
|------|------|----------------|
| **noise_level** | 环境噪音大致等级 | **xiaozhi-server**：根据 VAD 能量或 ASR 置信度简单分级为 `low`/`medium`/`high`；暂无则传 `unknown`。 |
| **has_overlapping_voice** | 本段是否有多人/重叠人声 | **xiaozhi-server**：根据本轮声纹/ASR 结果判断（若支持多说话人则填 true/false），否则默认 false 或 unknown。 |
| **scene** | 使用场景标签 | **设备 → xiaozhi**：设备在 hello 或上报里带「户外/车内/夜间」等；xiaozhi 原样放入。若设备未传则 xiaozhi 填 `unknown`。 |
| **environment_description** | 环境解释文案（如设备图像识别结果） | **设备 → xiaozhi**：设备对当前画面做图像识别后生成的一段文字描述（如「孩子在客厅玩积木，家长在旁」）；xiaozhi 原样带给 zhiban，供回复时结合场景。未传则空或不带该字段。 |
| **device_reported_context** | 设备自定义键值 | **设备 → xiaozhi**：设备上报的任意扩展信息（如 `{"moving": true, "light": "dim"}`）；xiaozhi 原样打包带给 zhiban，不解析。 |

- 小结：**noise_level、has_overlapping_voice** 来自 xiaozhi 自己（VAD/ASR/声纹）；**scene、environment_description、device_reported_context** 来自设备上报，由 xiaozhi 转发。zhiban 只读这些字段，不接设备、不拉其他服务。

### 6.3 请求体中的形态

```json
{
  "text": "...",
  "session_id": "...",
  "user_id": "...",
  "speaker_context": { ... },
  "environment_context": {
    "noise_level": "low | medium | high | unknown",
    "has_overlapping_voice": false,
    "scene": "indoor | outdoor | car | night | unknown",
    "environment_description": "设备图像识别后的环境解释文案，如：孩子在客厅玩积木，家长在旁",
    "device_reported_context": {}
  }
}
```

zhiban-agent 在 system 或上下文里注入：「当前环境：{noise_level}，场景：{scene}，是否有重叠人声：{has_overlapping_voice}。若存在 `environment_description`（设备图像识别给出的环境描述），请结合该描述理解当前场景后再回复。回复时可适当考虑环境，避免过于复杂或不合时宜的内容。」若存在 `device_reported_context`，可一并简要注入供模型参考。

### 6.4 实现顺序（已定：均可）

- 第一版可仅预留 `environment_context` 字段（全为 `unknown` 或省略）；  
- 后续有设备场景上报就填 `scene`，VAD/ASR 有分级就填 `noise_level`，两种方式都支持即可。

---

## 七、实施顺序建议（已按讨论结果收紧）

| 阶段 | 内容 |
|------|------|
| 1 | **speaker_context**：xiaozhi 拼 speaker_type、introduction、estimated_age、is_owner_child；zhiban 接收并注入 system/上下文；user_id 按当前说话人传。 |
| 2 | **Memory 按人**：xiaozhi 每轮按当前说话人设 memory role_id（或 switch_role）；zhiban 按 user_id 分区；非主孩子/访客**只写短期、不参与长期召回**。 |
| 3 | **打断策略 + 打断意图**：主孩子在说且机器未播时，非主孩子声音先做**打断意图识别**，仅识别为打断意图才打断；否则不打断。 |
| 4 | **Skill 固定格式 + 智控台可配置**：定义 Skill 统一 schema（id/name/description/instructions/tools 等）；智控台配置「说话人类型 → skill_id」映射并随私有配置下发；xiaozhi 请求 zhiban 时带 skill_id。 |
| 5 | **environment_context**：预留字段；有设备/ASR 能力则填，无则保持 unknown/省略均可。 |

---

## 八、已定结论汇总

1. **estimated_age**：主孩子用 device_child.birthday 算；非主孩子/未知从 introduce 抽或传 unknown，zhiban 对 unknown 用儿童友好口吻。  
2. **打断**：主孩子轮次内不因非主孩子声音直接打断；**必须做打断意图识别**，仅识别为打断意图才打断。  
3. **非主孩子 memory**：**写访客短期**，不参与长期沉淀与召回。  
4. **Skill**：**智控台可配置**；Skill 有**固定格式**（类 Claude Skill：id、name、description、instructions、tools 等）；智控台配置「说话人类型 → skill_id」映射并下发，xiaozhi 传 skill_id 给 zhiban。  
5. **环境信息**：预留与后续接入**都可以**，按实现节奏来即可。
